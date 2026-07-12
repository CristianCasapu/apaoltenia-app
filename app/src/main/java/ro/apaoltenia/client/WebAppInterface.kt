package ro.apaoltenia.client

import android.webkit.JavascriptInterface

/**
 * Punte JavaScript <-> Kotlin.
 *
 * Cand utilizatorul se autentifica manual prima data, JavaScript-ul injectat
 * citeste valorile din formular in momentul trimiterii si le paseaza aici, ca
 * sa poata fi propuse pentru salvare criptata. Parola NU este scrisa nicaieri
 * in cod si nu paraseste dispozitivul.
 */
class WebAppInterface(
    private val onCredentialsCaptured: (email: String, password: String) -> Unit
) {
    @JavascriptInterface
    fun captureCredentials(email: String, password: String) {
        if (email.isNotBlank() && password.isNotBlank()) {
            onCredentialsCaptured(email, password)
        }
    }
}
