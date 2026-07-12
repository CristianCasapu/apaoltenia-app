package ro.apaoltenia.client

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import ro.apaoltenia.client.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: CredentialStore
    private lateinit var biometric: BiometricAuthenticator

    /** Setat pe true dupa deblocarea reusita, ca sa injectam auto-login o singura data. */
    private var autoLoginArmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        store = CredentialStore(this)
        biometric = BiometricAuthenticator(this)

        configureWebView()

        binding.swipeRefresh.setOnRefreshListener { binding.webView.reload() }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }

        binding.forgetButton.setOnClickListener { confirmForgetCredentials() }

        if (store.hasCredentials && biometric.canAuthenticate()) {
            showLockedState()
            runBiometric()
        } else {
            openLoginPage()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                binding.webView.reload(); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun runBiometric() {
        biometric.authenticate(
            onSuccess = {
                autoLoginArmed = true
                openLoginPage()
            },
            onError = { message -> showLockedState(message) },
            onFailed = { showLockedState(getString(R.string.unlock_failed)) }
        )
    }

    private fun openLoginPage() {
        showWebView()
        binding.webView.loadUrl(LOGIN_URL)
    }

    private fun showWebView() {
        binding.lockOverlay.visibility = View.GONE
        binding.swipeRefresh.visibility = View.VISIBLE
    }

    private fun showLockedState(status: String = getString(R.string.unlock_hint)) {
        binding.swipeRefresh.visibility = View.GONE
        binding.lockOverlay.visibility = View.VISIBLE
        binding.unlockStatus.text = status
        binding.actionButton.text = getString(R.string.unlock_button)
        binding.actionButton.setOnClickListener { runBiometric() }
        binding.forgetButton.visibility = View.VISIBLE
    }

    private fun showErrorState() {
        binding.swipeRefresh.visibility = View.GONE
        binding.lockOverlay.visibility = View.VISIBLE
        binding.unlockStatus.text = getString(R.string.load_error)
        binding.actionButton.text = getString(R.string.retry_button)
        binding.actionButton.setOnClickListener {
            showWebView()
            binding.webView.reload()
        }
        binding.forgetButton.visibility = View.GONE
    }

    private fun confirmForgetCredentials() {
        AlertDialog.Builder(this)
            .setTitle(R.string.forget_title)
            .setMessage(R.string.forget_message)
            .setPositiveButton(R.string.forget_yes) { _, _ ->
                store.clear()
                autoLoginArmed = false
                openLoginPage()
            }
            .setNegativeButton(R.string.save_no, null)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val web = binding.webView
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // Blindaj: fara acces la fisierele locale sau la continut din alte origini.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            // Fara continut mixt HTTP intr-o pagina HTTPS.
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Google Safe Browsing pentru pagini periculoase (daca dispozitivul il suporta).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(web.settings, true)
        }

        applyWebViewTheme()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Portalul e first-party; nu acceptam cookie-uri third-party.
            setAcceptThirdPartyCookies(web, false)
        }

        web.addJavascriptInterface(
            WebAppInterface { email, password -> onCredentialsCaptured(email, password) },
            "AndroidBridge"
        )

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val host = url.host ?: return false
                if (host == PORTAL_DOMAIN || host.endsWith(".$PORTAL_DOMAIN")) {
                    return false
                }
                // Linkurile externe se deschid in browserul telefonului, dar numai
                // pentru scheme sigure (http/https). Refuzam intent:/file:/javascript:
                // etc. ca sa nu poata fi pornite activitati arbitrare dintr-o pagina.
                val scheme = url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, url).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (!url.contains("login.jsp", ignoreCase = true)) return

                if (autoLoginArmed && store.hasCredentials) {
                    val email = store.email
                    val password = store.password
                    if (email != null && password != null) {
                        injectAutoFill(email, password)
                    }
                    autoLoginArmed = false
                }
                // Ascultam mereu trimiterea formularului: la prima logare
                // propunem salvarea, iar daca parola s-a schimbat intre timp
                // propunem actualizarea datelor salvate.
                injectCaptureHook()
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                if (request.isForMainFrame) showErrorState()
            }
        }
    }

    /** Activeaza randarea intunecata a paginii web cand aplicatia e pe tema dark. */
    private fun applyWebViewTheme() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
        val nightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(binding.webView.settings, isDark)
    }

    /**
     * Injecteaza JS care completeaza formularul de login si il trimite.
     * Selecteaza formularul de autentificare = cel cu EXACT un camp de tip
     * password (inregistrarea are doua, recuperarea are zero).
     */
    private fun injectAutoFill(email: String, password: String) {
        val safeEmail = jsEscape(email)
        val safePassword = jsEscape(password)
        val js = """
            (function() {
              function loginForm() {
                var pwds = Array.prototype.slice.call(
                    document.querySelectorAll('input[type=password]'));
                for (var i = 0; i < pwds.length; i++) {
                  var f = pwds[i].form;
                  if (!f) continue;
                  var inForm = f.querySelectorAll('input[type=password]').length;
                  if (inForm === 1) return f;
                }
                return pwds.length ? pwds[0].form : null;
              }
              var form = loginForm();
              if (!form) return;
              var pwd = form.querySelector('input[type=password]');
              var user = form.querySelector('input[type=email]') ||
                         form.querySelector('input[type=text]') ||
                         form.querySelector('input:not([type=password]):not([type=hidden]):not([type=checkbox])');
              if (user) { user.value = '$safeEmail';
                          user.dispatchEvent(new Event('input',{bubbles:true})); }
              if (pwd)  { pwd.value = '$safePassword';
                          pwd.dispatchEvent(new Event('input',{bubbles:true})); }
            })();
        """.trimIndent()
        // NOTA: nu apasam automat "Login". Portalul cere completarea unui
        // Cloudflare Turnstile (anti-bot) inainte de trimitere, asa ca lasam
        // utilizatorul sa bifeze verificarea si sa apese Login. Campurile sunt
        // deja completate, deci ramane un singur gest.
        binding.webView.evaluateJavascript(js, null)
    }

    /**
     * Injecteaza JS care, la trimiterea formularului de login, citeste valorile
     * introduse si le trimite in Kotlin prin AndroidBridge.
     */
    private fun injectCaptureHook() {
        val js = """
            (function() {
              function loginForm() {
                var pwds = Array.prototype.slice.call(
                    document.querySelectorAll('input[type=password]'));
                for (var i = 0; i < pwds.length; i++) {
                  var f = pwds[i].form;
                  if (!f) continue;
                  if (f.querySelectorAll('input[type=password]').length === 1) return f;
                }
                return null;
              }
              var form = loginForm();
              if (!form || form.__hooked) return;
              form.__hooked = true;
              form.addEventListener('submit', function() {
                var pwd = form.querySelector('input[type=password]');
                var user = form.querySelector('input[type=email]') ||
                           form.querySelector('input[type=text]') ||
                           form.querySelector('input:not([type=password]):not([type=hidden]):not([type=checkbox])');
                if (user && pwd && user.value && pwd.value) {
                  AndroidBridge.captureCredentials(user.value, pwd.value);
                }
              }, true);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    private fun onCredentialsCaptured(email: String, password: String) {
        runOnUiThread {
            if (!biometric.canAuthenticate()) return@runOnUiThread

            val isUpdate = store.hasCredentials
            if (isUpdate && store.email == email && store.password == password) {
                return@runOnUiThread
            }

            AlertDialog.Builder(this)
                .setTitle(if (isUpdate) R.string.update_title else R.string.save_title)
                .setMessage(if (isUpdate) R.string.update_message else R.string.save_message)
                .setPositiveButton(R.string.save_yes) { _, _ ->
                    store.save(email, password)
                }
                .setNegativeButton(R.string.save_no, null)
                .show()
        }
    }

    private fun jsEscape(value: String): String =
        value.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\u2028", "")
            .replace("\u2029", "")

    companion object {
        private const val PORTAL_DOMAIN = "apaoltenia.ro"
        private const val LOGIN_URL =
            "https://clienti.apaoltenia.ro/self_utilities/login.jsp"
    }
}
