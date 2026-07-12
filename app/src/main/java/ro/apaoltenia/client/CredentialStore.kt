package ro.apaoltenia.client

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stocheaza email + parola criptate cu AES-256-GCM, cu cheia generata si
 * pastrata exclusiv in Android Keystore (hardware-backed). Cheia nu poate fi
 * extrasa din dispozitiv, iar datele criptate sunt inutile fara ea.
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    val hasCredentials: Boolean
        get() = prefs.contains(KEY_EMAIL) && prefs.contains(KEY_PASSWORD)

    val email: String?
        get() = decrypt(prefs.getString(KEY_EMAIL, null))

    val password: String?
        get() = decrypt(prefs.getString(KEY_PASSWORD, null))

    fun save(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, encrypt(email))
            .putString(KEY_PASSWORD, encrypt(password))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String? {
        if (encoded == null) return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_BITS, payload, 0, IV_SIZE)
            )
            String(cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE), Charsets.UTF_8)
        } catch (_: Exception) {
            // Cheia din Keystore a fost invalidata (ex. resetarea metodelor de
            // deblocare) — datele nu mai pot fi recuperate, cerem re-logare.
            clear()
            null
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "apaoltenia_credentials_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val PREF_FILE = "apaoltenia_credentials"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
    }
}
