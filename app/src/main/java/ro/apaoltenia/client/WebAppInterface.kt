package ro.apaoltenia.client

import android.webkit.JavascriptInterface

/**
 * Punte JavaScript <-> Kotlin.
 *
 * Cand utilizatorul se autentifica manual prima data, JavaScript-ul injectat
 * citeste valorile din formular in momentul trimiterii si le paseaza aici, ca
 * sa poata fi propuse pentru salvare criptata. Parola NU este scrisa nicaieri
 * in cod si nu paraseste dispozitivul.
 *
 * Tot pe aici JavaScript-ul raporteaza si pozitia de scroll a paginii, pentru
 * ca pull-to-refresh sa se activeze doar cand continutul e derulat sus de tot
 * (aplicatia OpenUI5 a portalului deruleaza intr-un container intern, invizibil
 * pentru scroll-ul nativ al WebView-ului).
 */
class WebAppInterface(
    private val onCredentialsCaptured: (email: String, password: String) -> Unit,
    private val onScrollStatusChanged: (atTop: Boolean) -> Unit,
    private val onDeleteAccountRequested: () -> Unit
) {
    @JavascriptInterface
    fun captureCredentials(email: String, password: String) {
        if (email.isNotBlank() && password.isNotBlank()) {
            onCredentialsCaptured(email, password)
        }
    }

    @JavascriptInterface
    fun onScrollStatus(atTop: Boolean) {
        onScrollStatusChanged(atTop)
    }

    /**
     * JS-ul a interceptat confirmarea de stergere a contului si cere aplicatiei
     * verificarea suplimentara prin parola inainte de a permite stergerea.
     */
    @JavascriptInterface
    fun requestDeleteAccount() {
        onDeleteAccountRequested()
    }
}
