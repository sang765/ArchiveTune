package moe.koiverse.archivetune.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.my.kizzy.rpc.KizzyRPC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.koiverse.archivetune.constants.DiscordAvatarUrlKey
import moe.koiverse.archivetune.constants.DiscordNameKey
import moe.koiverse.archivetune.constants.DiscordTokenKey
import moe.koiverse.archivetune.constants.DiscordUsernameKey
import timber.log.Timber
import java.io.IOException

class DiscordAuthManager(private val context: Context) {
    private val logTag = "DiscordAuthManager"

    /**
     * Validate token using KizzyRPC.getUserInfo()
     * Returns Result<UserInfo> with username and globalName
     */
    suspend fun validateToken(token: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (token.isBlank()) {
                throw IllegalArgumentException("Token is empty")
            }
            val shortToken = try { token.take(8) + "…" } catch (_: Exception) { "(token)" }
            Timber.tag(logTag).d("Validating token=$shortToken")
            val userInfo = KizzyRPC.getUserInfo(token).getOrThrow()
            Timber.tag(logTag).d("Token valid for user=${userInfo.username}")
            userInfo
        }
    }

    /**
     * Save token + user info to preferences
     * This triggers DiscordPresenceManager to detect and init RPC
     */
    suspend fun saveToken(token: String, userInfo: UserInfo) {
        val shortToken = try { token.take(8) + "…" } catch (_: Exception) { "(token)" }
        Timber.tag(logTag).i("Saving token=$shortToken for user=${userInfo.username}")
        context.dataStore.edit { prefs ->
            prefs[DiscordTokenKey] = token
            prefs[DiscordUsernameKey] = userInfo.username
            prefs[DiscordNameKey] = userInfo.name
        }
    }

    /**
     * Clear token and user info (logout)
     */
    suspend fun clearToken() {
        Timber.tag(logTag).i("Clearing token")
        context.dataStore.edit { prefs ->
            prefs[DiscordTokenKey] = ""
            prefs[DiscordUsernameKey] = ""
            prefs[DiscordNameKey] = ""
            prefs[DiscordAvatarUrlKey] = ""
        }
    }

    /**
     * Get current token from preferences
     */
suspend fun getToken(): String? = runCatching {
    context.dataStore.data.first()[DiscordTokenKey]
}.getOrNull()
        context.dataStore.data.first()[DiscordTokenKey]
    }.getOrNull()

    /**
     * Check if token still valid by re-validating with KizzyRPC
     */
    suspend fun isTokenValid(): Boolean = runCatching {
        val token = context.dataStore.data.first()[DiscordTokenKey] ?: return@runCatching false
        if (token.isBlank()) return@runCatching false
        val shortToken = try { token.take(8) + "…" } catch (_: Exception) { "(token)" }
        Timber.tag(logTag).d("Re-validating token=$shortToken")
        val result = KizzyRPC.getUserInfo(token)
        val isValid = result.isSuccess
        Timber.tag(logTag).d("Token re-validation result: $isValid")
        isValid
    }.getOrDefault(false)

    /**
     * Flow of token changes for observation
     */
    fun observeToken(): Flow<String> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Timber.tag(logTag).e(exception, "Error reading preferences")
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[DiscordTokenKey] ?: ""
            }
    }

    /**
     * Get current user info from preferences
     */
    suspend fun getUserInfo(): Pair<String, String>? = runCatching {
        val username = context.dataStore.data.first()[DiscordUsernameKey] ?: return@runCatching null
        val name = context.dataStore.data.first()[DiscordNameKey] ?: return@runCatching null
        username to name
    }.getOrNull()

    /**
     * Check if user is logged in
     */
suspend fun isLoggedIn(): Boolean {
    val token = runCatching {
        context.dataStore.data.first()[DiscordTokenKey]
    }.getOrNull()
    return !token.isNullOrEmpty()
}
        val token = runCatching {
            context.dataStore.data.first()[DiscordTokenKey]
        }.getOrNull()
        return !token.isNullOrEmpty()
    }
}
