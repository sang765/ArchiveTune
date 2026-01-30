package moe.koiverse.archivetune.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Utility class for managing encrypted storage of sensitive data like Discord tokens.
 * Falls back to regular SharedPreferences if encryption fails.
 */
object EncryptedPreferenceManager {
    private const val ENCRYPTED_PREFS_NAME = "discord_secure_prefs"
    private const val FALLBACK_PREFS_NAME = "discord_secure_prefs_fallback"
    private const val TAG = "EncryptedPreferenceManager"
    
    @Volatile
    private var encryptedPrefs: SharedPreferences? = null
    
    /**
     * Get the encrypted SharedPreferences instance, creating it if necessary.
     * Falls back to regular SharedPreferences if encryption setup fails.
     */
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    ENCRYPTED_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { encryptedPrefs = it }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create encrypted preferences, falling back to regular SharedPreferences", e)
                context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
                    .also { encryptedPrefs = it }
            }
        }
    }
    
    /**
     * Store a string value securely
     */
    fun putString(context: Context, key: String, value: String) {
        getEncryptedPrefs(context).edit().putString(key, value).apply()
    }
    
    /**
     * Retrieve a string value securely
     */
    fun getString(context: Context, key: String, defaultValue: String = ""): String {
        return getEncryptedPrefs(context).getString(key, defaultValue) ?: defaultValue
    }
    
    /**
     * Remove a key from secure storage
     */
    fun remove(context: Context, key: String) {
        getEncryptedPrefs(context).edit().remove(key).apply()
    }
    
    /**
     * Check if a key exists in secure storage
     */
    fun contains(context: Context, key: String): Boolean {
        return getEncryptedPrefs(context).contains(key)
    }
    
    /**
     * Clear all secure storage
     */
    fun clear(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
    }
    
    // Constants for Discord-related keys
    object Keys {
        const val DISCORD_TOKEN = "discord_token"
        const val DISCORD_USERNAME = "discord_username"
        const val DISCORD_NAME = "discord_name"
    }
    
    /**
     * Helper function to get Discord token for use in Discord RPC
     * Returns null if no token is stored
     */
    fun getDiscordToken(context: Context): String? {
        val token = getString(context, Keys.DISCORD_TOKEN)
        return if (token.isBlank()) null else token
    }
}
