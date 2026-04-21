package com.thalock.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps uploaded file contents with AES-256-GCM using a Keystore-backed key.
 * File layout on disk:  [1-byte IV length][IV bytes][GCM ciphertext || 16-byte tag]
 *
 * The key lives in AndroidKeyStore — it cannot be exported, so even a rooted
 * attacker with raw access to `filesDir/uploads/` cannot decrypt the blobs
 * without also being able to call into the Keystore as this app.
 */
object FileCryptoManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "thalock_file_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    /**
     * Reads plaintext from [source], writes the AES-GCM encrypted form to [target].
     * Caller owns both streams and is responsible for closing [source]; [target] is
     * created (and replaced if it already exists) here.
     */
    fun encryptTo(source: InputStream, target: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        check(iv.size == IV_SIZE) { "Unexpected IV size: ${iv.size}" }

        FileOutputStream(target).use { out ->
            out.write(iv.size)
            out.write(iv)
            CipherOutputStream(out, cipher).use { cos ->
                source.copyTo(cos)
            }
        }
    }

    /**
     * Returns a decrypting [InputStream] over [source]. The caller must close it.
     */
    fun decryptInputStream(source: File): InputStream {
        val fin = FileInputStream(source)
        val ivLen = fin.read()
        require(ivLen == IV_SIZE) { "Corrupt file header" }
        val iv = ByteArray(IV_SIZE)
        var read = 0
        while (read < IV_SIZE) {
            val n = fin.read(iv, read, IV_SIZE - read)
            if (n < 0) error("Unexpected EOF reading IV")
            read += n
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherInputStream(fin, cipher)
    }

    /**
     * Convenience: decrypt the entire file into memory. Fine for small files
     * (a few MB); use [decryptInputStream] for larger ones.
     */
    fun decryptToBytes(source: File): ByteArray =
        decryptInputStream(source).use { it.readBytes() }
}
