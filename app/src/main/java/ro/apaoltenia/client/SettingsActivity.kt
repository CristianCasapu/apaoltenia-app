package ro.apaoltenia.client

import android.Manifest
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

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableNotifications()
            } else {
                binding.notifySwitch.isChecked = false
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

        setupTheme()
        setupNotifications()
        setupUpdates()
        setupData()
    }

    // ── Tema ──────────────────────────────────────────────────────────────
    private fun setupTheme() {
        when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.themeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.themeDark.isChecked = true
            else -> binding.themeSystem.isChecked = true
        }
        binding.themeGroup.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.themeMode = mode
            AppPreferences.applyTheme(mode)
        }
    }

    // ── Notificari ─────────────────────────────────────────────────────────
    private fun setupNotifications() {
        binding.notifySwitch.isChecked = prefs.invoiceNotificationsEnabled
        binding.notifySwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                prefs.invoiceNotificationsEnabled = false
                InvoiceCheckScheduler.disable(this)
                return@setOnCheckedChangeListener
            }
            // Verificarea in fundal are sens doar daca exista o sesiune de reluat.
            if (!CredentialStore(this).hasCredentials) {
                binding.notifySwitch.isChecked = false
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

    private fun enableNotifications() {
        prefs.invoiceNotificationsEnabled = true
        binding.notifySwitch.isChecked = true
        InvoiceCheckScheduler.enable(this)
    }

    // ── Actualizari ──────────────────────────────────────────────────────────
    private fun setupUpdates() {
        binding.versionLabel.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        binding.checkUpdatesButton.setOnClickListener {
            binding.checkUpdatesButton.isEnabled = false
            toast(getString(R.string.update_checking))
            lifecycleScope.launch {
                val update = UpdateChecker.check(BuildConfig.VERSION_NAME)
                binding.checkUpdatesButton.isEnabled = true
                if (update == null) {
                    toast(getString(R.string.update_up_to_date))
                } else {
                    promptUpdate(update)
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
                    CredentialStore(this).clear()
                    prefs.invoiceNotificationsEnabled = false
                    InvoiceCheckScheduler.disable(this)
                    binding.notifySwitch.isChecked = false
                    finish()
                }
                .setNegativeButton(R.string.save_no, null)
                .show()
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
