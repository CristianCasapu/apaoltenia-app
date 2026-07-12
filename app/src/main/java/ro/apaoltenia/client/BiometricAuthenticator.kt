package ro.apaoltenia.client

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Deblocare cu metoda dispozitivului: amprenta, recunoastere faciala, model,
 * PIN sau parola. Pe API 30+ (Redmi Note 13 Pro ruleaza Android 13+) putem
 * combina BIOMETRIC_STRONG cu DEVICE_CREDENTIAL, astfel incat sistemul ofera
 * automat toate metodele de deblocare configurate de utilizator.
 */
class BiometricAuthenticator(private val activity: AppCompatActivity) {

    private val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** true daca dispozitivul are cel putin o metoda de deblocare configurata. */
    fun canAuthenticate(): Boolean {
        val status = BiometricManager.from(activity)
            .canAuthenticate(allowedAuthenticators)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        onSuccess: () -> Unit,
        onError: (message: String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_prompt_title))
            .setSubtitle(activity.getString(R.string.unlock_prompt_subtitle))
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
