package com.thalock.app.security

/**
 * In-memory holder for the unwrapped database passphrase.
 *
 * Populated after a successful unlock (PIN verified or biometric cipher authorized)
 * and cleared on relock. Only an unlocked session may open the Room/SQLCipher DB.
 *
 * Note: the JVM cannot guarantee erasure (heap copies, GC). This holder
 * minimizes the lifetime of plaintext copies — prefer [withPassphrase] over
 * [get], which leaves an untracked copy in the caller's hands.
 */
object SessionKey {
    @Volatile
    private var passphrase: ByteArray? = null

    fun set(key: ByteArray) {
        passphrase?.fill(0)
        passphrase = key.copyOf()
    }

    /**
     * Run [block] with a scratch copy of the passphrase, then zero that copy
     * before returning. The stored passphrase is left intact. Returns `null`
     * when the vault is locked.
     */
    fun <T> withPassphrase(block: (ByteArray) -> T): T? {
        val copy = passphrase?.copyOf() ?: return null
        return try {
            block(copy)
        } finally {
            copy.fill(0)
        }
    }

    /**
     * Returns a fresh copy of the passphrase. Prefer [withPassphrase]; any
     * copy returned here lives in the caller's heap until GC runs.
     */
    fun get(): ByteArray? = passphrase?.copyOf()

    fun isUnlocked(): Boolean = passphrase != null

    fun clear() {
        passphrase?.fill(0)
        passphrase = null
    }
}
