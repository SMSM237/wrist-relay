package com.sangmin.wristrelay.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedValue(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val schemaVersion: Int = CURRENT_CRYPTO_SCHEMA_VERSION,
)

class SensitiveDataException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

class CryptoManager(
    private val keyProvider: () -> SecretKey = { loadOrCreateAndroidKey() },
) {
    fun encrypt(entityType: String, rowId: String, plaintext: String): EncryptedValue {
        validateBinding(entityType, rowId)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
            cipher.updateAAD(aad(entityType, rowId, CURRENT_CRYPTO_SCHEMA_VERSION))
            EncryptedValue(
                ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8)),
                iv = cipher.iv.clone(),
            )
        } catch (error: Exception) {
            throw SensitiveDataException("Sensitive data encryption failed", error)
        }
    }

    fun decrypt(entityType: String, rowId: String, value: EncryptedValue): String {
        validateBinding(entityType, rowId)
        require(value.iv.size == GCM_IV_BYTES) { "Invalid encrypted value IV" }
        require(value.schemaVersion == CURRENT_CRYPTO_SCHEMA_VERSION) {
            "Unsupported encrypted value schema"
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider(),
                GCMParameterSpec(GCM_TAG_BITS, value.iv),
            )
            cipher.updateAAD(aad(entityType, rowId, value.schemaVersion))
            cipher.doFinal(value.ciphertext).toString(StandardCharsets.UTF_8)
        } catch (error: Exception) {
            throw SensitiveDataException("Sensitive data authentication failed", error)
        }
    }

    private fun validateBinding(entityType: String, rowId: String) {
        require(entityType.isNotBlank()) { "Entity type is required" }
        require(rowId.isNotBlank()) { "Row ID is required" }
    }

    private fun aad(entityType: String, rowId: String, schemaVersion: Int): ByteArray =
        "$entityType\u0000$rowId\u0000$schemaVersion".toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val KEY_ALIAS = "wrist_relay_sensitive_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private val keyLock = Any()

        private fun loadOrCreateAndroidKey(): SecretKey = synchronized(keyLock) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateAndroidKey()
        }

        private fun generateAndroidKey(): SecretKey {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            val specification = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
            generator.init(specification)
            return generator.generateKey()
        }
    }
}

const val CURRENT_CRYPTO_SCHEMA_VERSION = 1
