package moe.koiverse.archivetune.utils

import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.UserInfo
import timber.log.Timber

/**
 * Utility class for validating Discord tokens
 */
object DiscordTokenValidator {

    /**
     * Validates Discord token format using basic pattern matching
     * 
     * @param token The token string to validate
     * @return true if the token has a valid format, false otherwise
     */
    fun isValidTokenFormat(token: String): Boolean {
        if (token.isBlank()) return false
        
        // Basic Discord token format validation
        // Tokens are typically in the format: [a-zA-Z0-9]{24}\.[a-zA-Z0-9_-]{6}\.[a-zA-Z0-9_-]{27}
        // or similar variations
        val trimmedToken = token.trim()
        
        // Check minimum length and basic structure
        if (trimmedToken.length < 50) return false
        
        // Check for presence of dots (typical token format)
        val dotCount = trimmedToken.count { it == '.' }
        if (dotCount != 2) return false
        
        // Check that token doesn't contain obvious invalid characters
        val validChars = trimmedToken.all { char -> 
            char.isLetterOrDigit() || char == '.' || char == '_' || char == '-'
        }
        
        return validChars
    }

    /**
     * Validates a Discord token by calling the KizzyRPC API
     * 
     * @param token The Discord token to validate
     * @return Result containing UserInfo if successful, or an error message if validation fails
     */
    suspend fun validateToken(token: String): Result<UserInfo> {
        return try {
            // First validate the format
            if (!isValidTokenFormat(token)) {
                Result.failure(Exception("Invalid token format"))
            } else {
                // Call KizzyRPC to verify token validity
                val userInfoResult = KizzyRPC.getUserInfo(token)
                
                userInfoResult.onSuccess { userInfo ->
                    // Token is valid and we have user info
                    Result.success(userInfo)
                }.onFailure { error ->
                    // Handle specific error cases
                    val errorMessage = when (error) {
                        is java.net.SocketException -> "Network error: Unable to connect to Discord"
                        is java.net.UnknownHostException -> "Network error: No internet connection"
                        is java.net.ConnectException -> "Network error: Connection refused"
                        is IllegalArgumentException -> "Invalid token: ${error.message}"
                        else -> "Token validation failed: ${error.message}"
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            // Handle any unexpected exceptions
            Timber.tag("DiscordTokenValidator").w(e, "Token validation failed")
            Result.failure(Exception("Token validation failed: ${e.message}"))
        }
    }

    /**
     * Validates a Discord token and returns a user-friendly result
     * 
     * @param token The Discord token to validate
     * @return Pair containing success status and message (either user info or error)
     */
    suspend fun validateTokenWithMessage(token: String): Pair<Boolean, String> {
        return try {
            if (!isValidTokenFormat(token)) {
                Pair(false, "Invalid token format. Please check your token and try again.")
            } else {
                val result = validateToken(token)
                result.fold(
                    onSuccess = { userInfo ->
                        Pair(true, "Token is valid! Welcome, ${userInfo.name}")
                    },
                    onFailure = { error ->
                        Pair(false, error.message ?: "Token validation failed")
                    }
                )
            }
        } catch (e: Exception) {
            Timber.tag("DiscordTokenValidator").w(e, "Token validation failed")
            Pair(false, "An unexpected error occurred during token validation")
        }
    }
}