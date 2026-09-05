package ir.ali0003.downloader.data.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

class VaultManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    companion object {
        private const val PREFS_FILE = "downloader_secure_prefs"
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_SALT = "vault_pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
        private const val KEY_VAULT_SET_UP = "vault_is_set_up"
        private const val KEY_AUTO_LOCK_MINUTES = "vault_auto_lock_minutes"
        private const val KEY_HIDDEN_MAPPINGS = "vault_hidden_mappings"
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Safe fallback if hardware keystore is unavailable in some emulator environments
            context.applicationContext.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE)
        }
    }

    fun isVaultConfigured(): Boolean {
        return prefs.getBoolean(KEY_VAULT_SET_UP, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setupPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        val salt = UUID.randomUUID().toString()
        val hash = hashPin(pin, salt)

        prefs.edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_VAULT_SET_UP, true)
            .apply()

        _isVaultUnlocked.value = true
        _failedAttempts.value = 0
        return true
    }

    fun verifyPin(pin: String): Boolean {
        if (!isVaultConfigured()) return false
        val salt = prefs.getString(KEY_SALT, "") ?: ""
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPin(pin, salt)

        val success = (inputHash == savedHash)
        if (success) {
            _isVaultUnlocked.value = true
            _failedAttempts.value = 0
        } else {
            _failedAttempts.value += 1
        }
        return success
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun unlockWithBiometrics() {
        _isVaultUnlocked.value = true
        _failedAttempts.value = 0
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun resetVault() {
        prefs.edit().clear().apply()
        _isVaultUnlocked.value = false
        _failedAttempts.value = 0
    }

    private fun hashPin(pin: String, salt: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$pin:$salt".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
