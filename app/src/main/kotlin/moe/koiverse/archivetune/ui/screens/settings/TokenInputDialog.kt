package moe.koiverse.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.UserInfo
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.ui.component.IconButton
import moe.koiverse.archivetune.utils.DiscordAuthManager
import timber.log.Timber

sealed class TokenDialogState {
    object Initial : TokenDialogState()
    object Validating : TokenDialogState()
    data class Success(val userInfo: UserInfo) : TokenDialogState()
    data class Error(val message: String) : TokenDialogState()
}

@Composable
fun TokenInputDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit,
    authManager: DiscordAuthManager
) {
    val scope = rememberCoroutineScope()
    var dialogState by remember { mutableStateOf<TokenDialogState>(TokenDialogState.Initial) }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }

    fun handleValidate() {
        if (token.isBlank()) {
            dialogState = TokenDialogState.Error("Token cannot be empty")
            return
        }

        if (token.length < 50) {
            dialogState = TokenDialogState.Error("Invalid token format (too short)")
            return
        }

        scope.launch {
            dialogState = TokenDialogState.Validating
            try {
                val result = authManager.validateToken(token.trim())
                result.onSuccess { userInfo ->
                    dialogState = TokenDialogState.Success(userInfo)
                }.onFailure { exception ->
                    Timber.tag("TokenInputDialog").e(exception, "Token validation failed")
                    val errorMessage = when {
                        exception.message?.contains("401", ignoreCase = true) == true ||
                        exception.message?.contains("Unauthorized", ignoreCase = true) == true -> {
                            "Token invalid or expired"
                        }
                        exception.message?.contains("timeout", ignoreCase = true) == true ||
                        exception.message?.contains("network", ignoreCase = true) == true -> {
                            "Network error - please check connection"
                        }
                        else -> "Discord API error - please try again"
                    }
                    dialogState = TokenDialogState.Error(errorMessage)
                }
            } catch (e: Exception) {
                Timber.tag("TokenInputDialog").e(e, "Unexpected error during validation")
                dialogState = TokenDialogState.Error("Unknown error - please try again")
            }
        }
    }

    fun handleLogin() {
        when (val state = dialogState) {
            is TokenDialogState.Success -> {
                scope.launch {
                    authManager.saveToken(token.trim(), state.userInfo)
                    onLoginSuccess()
                }
            }
            else -> {
                handleValidate()
            }
        }
    }

    // Auto-validate when token is entered and dialog is in error state
    LaunchedEffect(token) {
        if (dialogState is TokenDialogState.Error && token.isNotBlank()) {
            dialogState = TokenDialogState.Initial
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Login with Discord Token",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            when (dialogState) {
                is TokenDialogState.Initial, is TokenDialogState.Error -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Paste your Discord token to enable Rich Presence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Discord Token") },
                            placeholder = { Text("Paste token here") },
                            singleLine = true,
                            maxLines = 1,
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (tokenVisible) "Hide token" else "Show token"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = dialogState !is TokenDialogState.Error
                        )

                        if (dialogState is TokenDialogState.Error) {
                            Text(
                                text = (dialogState as TokenDialogState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is TokenDialogState.Validating -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Validating token...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is TokenDialogState.Success -> {
                    val userInfo = (dialogState as TokenDialogState.Success).userInfo
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            AsyncImage(
                                model = "https://cdn.discordapp.com/embed/avatars/${userInfo.username.takeLast(1).toIntOrNull() ?: 0}/128.png",
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = userInfo.username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (userInfo.name != userInfo.username) {
                            Text(
                                text = userInfo.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "Token validated successfully!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { handleLogin() },
                enabled = when (dialogState) {
                    is TokenDialogState.Initial -> token.isNotBlank()
                    is TokenDialogState.Success -> true
                    else -> false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (dialogState) {
                        is TokenDialogState.Initial -> "Validate"
                        is TokenDialogState.Success -> "Confirm Login"
                        else -> ""
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = dialogState !is TokenDialogState.Validating
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
