package ro.apaoltenia.client

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ro.apaoltenia.client.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences

    /**
     * True cat timp schimbam programatic switch-ul de notificari, ca
     * listener-ul sa nu se reapeleze singur (setarea isChecked din interiorul
     * lui il declanseaza din nou).
     */
    private var suppressNotifyListener = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableNotifications()
            } else {
                setNotifySwitch(false)
                toast(getString(R.string.notif_permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        prefs = AppPreferences(this)

        setupAutoLogin()
        setupTheme()
        setupNotifications()
        setupUpdates()
        setupData()
    }

    // ── Autentificare automata ───────────────────────────────────────────────
    private fun setupAutoLogin() {
        binding.autoLoginSwitch.isChecked = prefs.autoLoginEnabled
        binding.autoLoginSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.autoLoginEnabled = checked
        }
    }

    // ── Tema ──────────────────────────────────────────────────────────────
    private fun setupTheme() {
        when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.themeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.themeDark.isChecked = true
            else -> binding.themeSystem.isChecked = true
        }
        binding.themeGroup.setOnCheckedChangeListener { _, id ->
            val mode = themeModeFor(id)
            prefs.themeMode = mode
            AppPreferences.applyTheme(mode)
        }
    }

    companion object {
        /** Mapare id buton radio -> mod de tema AppCompat. Extras pentru testare. */
        internal fun themeModeFor(checkedId: Int): Int = when (checkedId) {
            R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
            R.id.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    // ── Notificari ─────────────────────────────────────────────────────────
    private fun setupNotifications() {
        binding.notifySwitch.isChecked = prefs.invoiceNotificationsEnabled
        binding.notifySwitch.setOnCheckedChangeListener { _, checked ->
            if (suppressNotifyListener) return@setOnCheckedChangeListener
            if (!checked) {
                prefs.invoiceNotificationsEnabled = false
                InvoiceCheckScheduler.disable(this)
                return@setOnCheckedChangeListener
            }
            // Verificarea in fundal are sens doar daca exista o sesiune de reluat.
            if (!CredentialStore(this).hasCredentials) {
                setNotifySwitch(false)
                toast(getString(R.string.settings_notify_needs_login))
                return@setOnCheckedChangeListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.hasPermission(this)
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enableNotifications()
            }
        }
    }

    /** Schimba switch-ul fara sa (re)declanseze listener-ul. */
    private fun setNotifySwitch(checked: Boolean) {
        suppressNotifyListener = true
        binding.notifySwitch.isChecked = checked
        suppressNotifyListener = false
    }

    private fun enableNotifications() {
        prefs.invoiceNotificationsEnabled = true
        setNotifySwitch(true)
        InvoiceCheckScheduler.enable(this)
    }

    // ── Actualizari ──────────────────────────────────────────────────────────
    private fun setupUpdates() {
        binding.versionLabel.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        binding.changelogButton.setOnClickListener {
            startActivity(Intent(this, ChangelogActivity::class.java))
        }
        binding.checkUpdatesButton.setOnClickListener {
            binding.checkUpdatesButton.isEnabled = false
            toast(getString(R.string.update_checking))
            lifecycleScope.launch {
                val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
                binding.checkUpdatesButton.isEnabled = true
                when (result) {
                    is UpdateChecker.CheckResult.Available -> promptUpdate(result.update)
                    UpdateChecker.CheckResult.UpToDate ->
                        toast(getString(R.string.update_up_to_date))
                    // Offline / eroare API: nu pretindem ca esti la zi.
                    UpdateChecker.CheckResult.Failed ->
                        toast(getString(R.string.update_check_failed))
                }
            }
        }
    }

    private fun promptUpdate(update: UpdateChecker.Update) {
        UpdateManager.promptAndInstall(this, update)
    }

    // ── Date salvate ─────────────────────────────────────────────────────────
    private fun setupData() {
        binding.forgetButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.forget_title)
                .setMessage(R.string.forget_message)
                .setPositiveButton(R.string.forget_yes) { _, _ ->
                    wipeLocalData(this)
                    setNotifySwitch(false)
                    finish()
                }
                .setNegativeButton(R.string.save_no, null)
                .show()
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
