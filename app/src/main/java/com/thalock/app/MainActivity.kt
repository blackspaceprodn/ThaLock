package com.thalock.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.security.AppLockManager
import com.thalock.app.security.BiometricHelper
import com.thalock.app.security.LockMethod
import com.thalock.app.security.SessionKey
import com.thalock.app.util.DocumentFileGenerator
import androidx.compose.foundation.isSystemInDarkTheme
import com.thalock.app.ui.ThaLockNavGraph
import com.thalock.app.ui.theme.ThaLockTheme
import com.thalock.app.ui.theme.ThemeMode
import com.thalock.app.ui.theme.ThemePreference

class MainActivity : FragmentActivity() {

    private lateinit var biometricHelper: BiometricHelper
    private lateinit var lockManager: AppLockManager

    /** Timestamp when the app was last paused (went to background). */
    private var pausedAtMillis: Long = 0L

    /** Whether the vault is currently unlocked. Managed from Compose via callback. */
    private var relockCallback: (() -> Unit)? = null

    companion object {
        private const val RELOCK_TIMEOUT_MS = 60_000L // 1 minute
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and Recent Apps thumbnail
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        biometricHelper = BiometricHelper(this)
        lockManager = AppLockManager(this)
        ThemePreference.init(this)

        setContent {
            val themeMode by ThemePreference.mode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            ThaLockTheme(darkTheme = darkTheme) {
                var onboardingDone by remember { mutableStateOf(lockManager.isOnboardingComplete) }
                var introDone by remember { mutableStateOf(lockManager.isOnboardingComplete) }
                var isUnlocked by remember { mutableStateOf(false) }
                var authError by remember { mutableStateOf<String?>(null) }

                // Register re-lock callback so lifecycle methods can trigger a full
                // relock: drop the in-memory passphrase, close the DB handle, flip
                // the UI state. Without clearing SessionKey + DB the SAF provider
                // would keep serving documents while the lock screen is up.
                DisposableEffect(Unit) {
                    relockCallback = {
                        SessionKey.clear()
                        AppDatabase.close()
                        isUnlocked = false
                    }
                    onDispose { relockCallback = null }
                }

                Crossfade(
                    targetState = when {
                        !introDone -> "intro"
                        !onboardingDone -> "onboarding"
                        !isUnlocked -> "locked"
                        else -> "unlocked"
                    },
                    label = "screen_transition",
                    animationSpec = tween(400)
                ) { state ->
                    when (state) {
                        "intro" -> {
                            IntroSlides(onDone = { introDone = true })
                        }
                        "onboarding" -> {
                            OnboardingScreen(
                                canUseBiometric = biometricHelper.canAuthenticate(),
                                onBiometricChosen = {
                                    authenticateAndWrapForBiometric(
                                        onReady = {
                                            lockManager.lockMethod = LockMethod.BIOMETRIC
                                            lockManager.isOnboardingComplete = true
                                            onboardingDone = true
                                            isUnlocked = true
                                            authError = null
                                        },
                                        onFailed = { authError = it }
                                    )
                                },
                                onPinSet = { pin ->
                                    lockManager.lockMethod = LockMethod.PIN
                                    lockManager.setPin(pin)
                                    lockManager.isOnboardingComplete = true
                                    onboardingDone = true
                                    isUnlocked = true
                                }
                            )
                        }
                        "locked" -> {
                            LockScreen(
                                lockMethod = lockManager.lockMethod,
                                lockManager = lockManager,
                                error = authError,
                                onBiometricAuth = {
                                    unlockWithBiometric(
                                        onUnlocked = { isUnlocked = true; authError = null },
                                        onFailed = { authError = it }
                                    )
                                },
                                onPinSubmit = { pin ->
                                    if (lockManager.isLockedOut) {
                                        authError = "Too many attempts. Please wait."
                                    } else if (lockManager.verifyPin(pin)) {
                                        isUnlocked = true
                                        authError = null
                                    } else {
                                        authError = if (lockManager.isLockedOut) {
                                            "Too many attempts. Locked for 1 minute."
                                        } else {
                                            "Incorrect PIN (${AppLockManager.MAX_ATTEMPTS - lockManager.failedAttempts} tries left)"
                                        }
                                    }
                                }
                            )

                            LaunchedEffect(Unit) {
                                if (lockManager.lockMethod == LockMethod.BIOMETRIC) {
                                    unlockWithBiometric(
                                        onUnlocked = { isUnlocked = true; authError = null },
                                        onFailed = { authError = it }
                                    )
                                }
                            }
                        }
                        else -> {
                            val navController = rememberNavController()
                            ThaLockNavGraph(navController = navController)
                        }
                    }
                }
            }
        }
    }

    /**
     * Biometric onboarding / switch-to-biometric path. Builds an ENCRYPT cipher
     * against the auth-required Keystore key, prompts for biometric, then wraps
     * the current (or a freshly generated) DB passphrase.
     */
    private fun authenticateAndWrapForBiometric(
        onReady: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val cipher = try {
            lockManager.buildBioEncryptCipher()
        } catch (t: Throwable) {
            if (lockManager.isBioKeyInvalidated(t)) {
                lockManager.removeBioWrap()
                // Retry once with a fresh key.
                runCatching { lockManager.buildBioEncryptCipher() }.getOrElse {
                    onFailed("Biometric unavailable: ${it.localizedMessage ?: "unknown error"}")
                    return
                }
            } else {
                onFailed("Biometric unavailable: ${t.localizedMessage ?: "unknown error"}")
                return
            }
        }
        biometricHelper.authenticate(
            activity = this,
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { authorized ->
                try {
                    lockManager.wrapWithBioCipher(authorized)
                    onReady()
                } catch (t: Throwable) {
                    onFailed("Failed to enroll biometric: ${t.localizedMessage ?: "unknown error"}")
                }
            },
            onError = { onFailed(it) }
        )
    }

    /**
     * Biometric unlock path. If a bio-wrap exists, builds a DECRYPT cipher and
     * unwraps [SessionKey]. If no wrap exists yet (first biometric login after
     * onboarding on a device that somehow lost the wrap), falls back to the
     * encrypt-and-wrap flow. Permanently-invalidated keys drop their wrap and
     * force the user through onboarding again.
     */
    private fun unlockWithBiometric(
        onUnlocked: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (!lockManager.hasBioWrap) {
            authenticateAndWrapForBiometric(onReady = onUnlocked, onFailed = onFailed)
            return
        }
        val cipher = try {
            lockManager.buildBioDecryptCipher()
        } catch (t: Throwable) {
            if (lockManager.isBioKeyInvalidated(t)) {
                lockManager.removeBioWrap()
                SessionKey.clear()
                onFailed("Biometric changed. Please re-enroll.")
            } else {
                onFailed("Biometric unavailable: ${t.localizedMessage ?: "unknown error"}")
            }
            return
        }
        if (cipher == null) {
            authenticateAndWrapForBiometric(onReady = onUnlocked, onFailed = onFailed)
            return
        }
        biometricHelper.authenticate(
            activity = this,
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { authorized ->
                val ok = try {
                    lockManager.unwrapWithBioCipher(authorized)
                } catch (t: Throwable) {
                    if (lockManager.isBioKeyInvalidated(t)) {
                        lockManager.removeBioWrap()
                        SessionKey.clear()
                        onFailed("Biometric changed. Please re-enroll.")
                        return@authenticate
                    }
                    false
                }
                if (ok) onUnlocked() else onFailed("Could not unlock vault")
            },
            onError = { onFailed(it) }
        )
    }

    override fun onPause() {
        super.onPause()
        pausedAtMillis = System.currentTimeMillis()
        // Clean up any exported document cache
        DocumentFileGenerator.cleanCache(this)
    }

    override fun onResume() {
        super.onResume()
        if (pausedAtMillis > 0) {
            val elapsed = System.currentTimeMillis() - pausedAtMillis
            if (elapsed >= RELOCK_TIMEOUT_MS) {
                relockCallback?.invoke()
            }
            pausedAtMillis = 0L
        }
    }
}
