package com.thalock.app.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class LockMethod {
    BIOMETRIC,
    PIN
}

/**
 * Auth + DB-key custody.
 *
 * **PIN path**   — the DB passphrase is wrapped with AES-GCM using a key derived
 *   from the user's PIN via PBKDF2 (120k iters, SHA-256). The plaintext passphrase
 *   is never stored.
 *
 * **Biometric path** — the DB passphrase is wrapped with a Keystore-backed
 *   AES-256 key that is gated on [BiometricPrompt] via a [CryptoObject].
 *   Unwrap requires an authorized [Cipher], so a rooted attacker still needs
 *   a fresh biometric/device-credential authentication to use the key.
 *
 * In both cases the unwrapped passphrase is kept only in [SessionKey] and is
 * cleared on relock.
 */
class AppLockManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "thalock_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply() }

    var lockMethod: LockMethod
        get() {
            val stored = prefs.getString(KEY_LOCK_METHOD, null)
            return if (stored == LockMethod.PIN.name) LockMethod.PIN else LockMethod.BIOMETRIC
        }
        set(value) { prefs.edit().putString(KEY_LOCK_METHOD, value.name).apply() }

    val isPinSet: Boolean
        get() = prefs.contains(KEY_PIN_HASH)

    val hasPinWrap: Boolean
        get() = prefs.contains(KEY_DB_PIN_WRAP_CT)

    val hasBioWrap: Boolean
        get() = prefs.contains(KEY_DB_BIO_WRAP_CT)

    // --- PIN auth (PBKDF2) --------------------------------------------------

    /**
     * Hash and store the PIN. If a passphrase is already in [SessionKey] (e.g. during
     * a PIN change while the vault is unlocked), rewrap it with the new PIN. If none
     * is present (fresh onboarding), generate one and seed [SessionKey].
     */
    fun setPin(pin: String) {
        require(pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }) {
            "PIN must be $MIN_PIN_LENGTH-$MAX_PIN_LENGTH digits"
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt, PBKDF2_ITERATIONS)
        prefs.edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()

        val passphrase = SessionKey.get() ?: run {
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            SessionKey.set(fresh)
            fresh
        }
        try {
            wrapWithPin(pin, passphrase)
        } finally {
            passphrase.fill(0)
        }
        // Scrub any legacy plaintext-in-prefs copy once the wrap is in place.
        prefs.edit().remove(KEY_LEGACY_DB_PASSPHRASE).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedIters = prefs.getInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS_LEGACY)

        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val hash = hashPin(pin, salt, storedIters)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        val matches = constantTimeEquals(hashB64, storedHash)

        if (matches) {
            resetFailedAttempts()
            val passphrase = unwrapWithPin(pin) ?: migrateLegacyPassphraseIntoPinWrap(pin)
            if (passphrase != null) {
                SessionKey.set(passphrase)
                runCatching { passphrase.fill(0) }
            }
            // Transparent upgrade: re-hash + re-wrap with the current iteration count
            // so subsequent verifies use the stronger parameters. No-op if already current.
            if (storedIters < PBKDF2_ITERATIONS) {
                rehashAndRewrapWithCurrentIterations(pin)
            }
        } else {
            recordFailedAttempt()
        }
        return matches
    }

    private fun rehashAndRewrapWithCurrentIterations(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt, PBKDF2_ITERATIONS)
        prefs.edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
        SessionKey.withPassphrase { passphrase ->
            wrapWithPin(pin, passphrase)
        }
    }

    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_ITERATIONS)
            .remove(KEY_DB_PIN_WRAP_SALT)
            .remove(KEY_DB_PIN_WRAP_IV)
            .remove(KEY_DB_PIN_WRAP_CT)
            .remove(KEY_DB_PIN_WRAP_ITERS)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }

    // --- Brute-force lockout ------------------------------------------------

    val failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    val isLockedOut: Boolean
        get() {
            val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
            if (until == 0L) return false
            if (System.currentTimeMillis() >= until) {
                // Clear the timer but keep the escalation tier so repeat offenders
                // hit a longer wait on the next round of failures.
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, 0)
                    .apply()
                return false
            }
            return true
        }

    val lockoutRemainingMs: Long
        get() {
            val remaining = prefs.getLong(KEY_LOCKOUT_UNTIL, 0) - System.currentTimeMillis()
            return if (remaining > 0) remaining else 0
        }

    private fun recordFailedAttempt() {
        val attempts = failedAttempts + 1
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
        if (attempts >= MAX_ATTEMPTS) {
            val tier = prefs.getInt(KEY_LOCKOUT_TIER, 0)
            val duration = LOCKOUT_LADDER_MS[tier.coerceAtMost(LOCKOUT_LADDER_MS.lastIndex)]
            val until = System.currentTimeMillis() + duration
            prefs.edit()
                .putLong(KEY_LOCKOUT_UNTIL, until)
                .putInt(KEY_LOCKOUT_TIER, tier + 1)
                .apply()
        }
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .putInt(KEY_LOCKOUT_TIER, 0)
            .apply()
    }

    // --- DB passphrase: PIN-wrap (AES-GCM over PBKDF2-derived key) ----------

    private fun deriveWrapKey(pin: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun wrapWithPin(pin: String, passphrase: ByteArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveWrapKey(pin, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(passphrase)
        prefs.edit()
            .putString(KEY_DB_PIN_WRAP_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_DB_PIN_WRAP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(KEY_DB_PIN_WRAP_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putInt(KEY_DB_PIN_WRAP_ITERS, PBKDF2_ITERATIONS)
            .apply()
    }

    private fun unwrapWithPin(pin: String): ByteArray? {
        val saltB64 = prefs.getString(KEY_DB_PIN_WRAP_SALT, null) ?: return null
        val ivB64 = prefs.getString(KEY_DB_PIN_WRAP_IV, null) ?: return null
        val ctB64 = prefs.getString(KEY_DB_PIN_WRAP_CT, null) ?: return null
        val iters = prefs.getInt(KEY_DB_PIN_WRAP_ITERS, PBKDF2_ITERATIONS_LEGACY)
        return try {
            val key = deriveWrapKey(pin, Base64.decode(saltB64, Base64.NO_WRAP), iters)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * One-shot upgrade: previous releases stored the DB passphrase as a plain Base64
     * value under [KEY_LEGACY_DB_PASSPHRASE] (only encrypted by Keystore). If the
     * user has entered a correct PIN and no PIN-wrap exists yet, wrap the legacy
     * value now and delete the plaintext copy.
     */
    private fun migrateLegacyPassphraseIntoPinWrap(pin: String): ByteArray? {
        val legacy = prefs.getString(KEY_LEGACY_DB_PASSPHRASE, null) ?: return null
        val passphrase = Base64.decode(legacy, Base64.NO_WRAP)
        wrapWithPin(pin, passphrase)
        prefs.edit().remove(KEY_LEGACY_DB_PASSPHRASE).apply()
        return passphrase
    }

    // --- DB passphrase: biometric-wrap (Keystore, auth-required) ------------

    /**
     * Builds a [Cipher] in [Cipher.ENCRYPT_MODE] using the auth-required Keystore
     * key. The cipher must be authorized via `BiometricPrompt.authenticate(
     * CryptoObject(cipher))` before `wrapWithBioCipher` will succeed.
     */
    fun buildBioEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBioKey())
        return cipher
    }

    /**
     * Builds a [Cipher] in [Cipher.DECRYPT_MODE] using the auth-required Keystore
     * key and the stored IV. Returns `null` if no bio-wrap exists. Must be
     * authorized via [BiometricPrompt] before `unwrapWithBioCipher` will succeed.
     */
    fun buildBioDecryptCipher(): Cipher? {
        val ivB64 = prefs.getString(KEY_DB_BIO_WRAP_IV, null) ?: return null
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateBioKey(), GCMParameterSpec(128, iv))
        return cipher
    }

    /**
     * Wrap the current [SessionKey] passphrase (or a freshly generated one if none
     * is cached yet) using the supplied authorized [cipher]. Called on the
     * biometric onboarding path after the user has confirmed identity.
     */
    fun wrapWithBioCipher(cipher: Cipher) {
        val passphrase = SessionKey.get() ?: run {
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            SessionKey.set(fresh)
            fresh
        }
        try {
            val ct = cipher.doFinal(passphrase)
            prefs.edit()
                .putString(KEY_DB_BIO_WRAP_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_DB_BIO_WRAP_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
                .remove(KEY_LEGACY_DB_PASSPHRASE)
                .apply()
        } finally {
            passphrase.fill(0)
        }
    }

    /**
     * Unwrap with an authorized decrypt cipher and seed [SessionKey]. Returns true
     * on success. If [KEY_LEGACY_DB_PASSPHRASE] exists and there is no bio-wrap
     * yet, this will also perform a one-shot migration by wrapping the legacy
     * passphrase with the cipher.
     */
    fun unwrapWithBioCipher(cipher: Cipher): Boolean {
        val ctB64 = prefs.getString(KEY_DB_BIO_WRAP_CT, null)
        if (ctB64 != null) {
            return try {
                val plain = cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP))
                try {
                    SessionKey.set(plain)
                } finally {
                    plain.fill(0)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
        // No bio-wrap yet. If we have a legacy passphrase OR no passphrase at all,
        // treat this as the wrap-creation step: generate/retrieve the passphrase,
        // encrypt with the ENCRYPT-mode cipher. (Caller should have passed an
        // ENCRYPT cipher when no wrap exists yet.)
        val legacy = prefs.getString(KEY_LEGACY_DB_PASSPHRASE, null)
        val passphrase = if (legacy != null) {
            Base64.decode(legacy, Base64.NO_WRAP)
        } else {
            ByteArray(32).also { SecureRandom().nextBytes(it) }
        }
        SessionKey.set(passphrase)
        return try {
            val ct = cipher.doFinal(passphrase)
            prefs.edit()
                .putString(KEY_DB_BIO_WRAP_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_DB_BIO_WRAP_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
                .remove(KEY_LEGACY_DB_PASSPHRASE)
                .apply()
            true
        } catch (_: Exception) {
            false
        } finally {
            passphrase.fill(0)
        }
    }

    private fun getOrCreateBioKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_BIO_ALIAS, null) as? SecretKey)?.let { return it }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_BIO_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    /**
     * Delete the biometric-wrap state (key + stored CT/IV). Call when the Keystore key
     * has been invalidated (fingerprint re-enrolled) so the next onboarding path can
     * regenerate it cleanly. Callers should also drop [SessionKey] if it was loaded
     * from a now-unreachable wrap.
     */
    fun removeBioWrap() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_BIO_ALIAS)) keyStore.deleteEntry(KEY_BIO_ALIAS)
        } catch (_: Exception) { /* Keystore may be momentarily unavailable; ignore. */ }
        prefs.edit()
            .remove(KEY_DB_BIO_WRAP_IV)
            .remove(KEY_DB_BIO_WRAP_CT)
            .apply()
    }

    /**
     * True if the caller's last attempt to use the biometric key failed because the
     * user re-enrolled a biometric (invalidating the Keystore key). UI can use this
     * to drop the stored wrap and restart the biometric onboarding flow.
     */
    fun isBioKeyInvalidated(t: Throwable): Boolean =
        t is KeyPermanentlyInvalidatedException ||
            (t.cause as? Throwable)?.let { it is KeyPermanentlyInvalidatedException } == true

    // --- Legacy ----------------------------------------------------------

    /**
     * Deprecated: the pre-wrap path where the 32-byte passphrase lived directly
     * in [prefs]. Retained only so existing installs can migrate on first unlock.
     */
    @Deprecated("Use SessionKey after unlock — this exposes the raw passphrase.")
    fun getLegacyDatabasePassphrase(): ByteArray? {
        val existing = prefs.getString(KEY_LEGACY_DB_PASSPHRASE, null) ?: return null
        return Base64.decode(existing, Base64.NO_WRAP)
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_BIO_ALIAS = "thalock_bio_wrap_key"

        private const val KEY_PIN_HASH = "app_pin_hash"
        private const val KEY_PIN_SALT = "app_pin_salt"
        private const val KEY_PIN_ITERATIONS = "app_pin_iterations"
        private const val KEY_LOCK_METHOD = "lock_method"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_LOCKOUT_TIER = "lockout_tier"

        private const val KEY_DB_PIN_WRAP_SALT = "db_pin_wrap_salt"
        private const val KEY_DB_PIN_WRAP_IV = "db_pin_wrap_iv"
        private const val KEY_DB_PIN_WRAP_CT = "db_pin_wrap_ct"
        private const val KEY_DB_PIN_WRAP_ITERS = "db_pin_wrap_iters"

        private const val KEY_DB_BIO_WRAP_IV = "db_bio_wrap_iv"
        private const val KEY_DB_BIO_WRAP_CT = "db_bio_wrap_ct"

        // Pre-wrap pref key. Do NOT write to this anymore; only read for migration.
        private const val KEY_LEGACY_DB_PASSPHRASE = "db_passphrase"

        // Current cost parameter. OWASP 2023+ recommends ≥600k for PBKDF2-SHA256.
        private const val PBKDF2_ITERATIONS = 600_000

        // Legacy cost. Older installs' hashes/wraps were produced with this count;
        // we keep reading it so existing users can still unlock, and transparently
        // re-hash + re-wrap on next successful verify.
        private const val PBKDF2_ITERATIONS_LEGACY = 120_000

        private const val PBKDF2_KEY_LENGTH = 256

        const val MAX_ATTEMPTS = 6
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 10

        // Escalating lockout ladder: after each MAX_ATTEMPTS failures, the user
        // is locked out for progressively longer windows — 1m, 5m, 15m, 1h,
        // then 1h cap for repeated offenses. Makes offline brute-force on a
        // stolen device dramatically slower without permanently bricking the
        // vault for a forgetful user.
        private val LOCKOUT_LADDER_MS = longArrayOf(
            60_000L,        // 1 min
            5 * 60_000L,    // 5 min
            15 * 60_000L,   // 15 min
            60 * 60_000L    // 1 hr (and beyond)
        )
    }
}
