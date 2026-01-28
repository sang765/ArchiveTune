package moe.koiverse.archivetune.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.constants.DiscordNameKey
import moe.koiverse.archivetune.constants.DiscordTokenKey
import moe.koiverse.archivetune.constants.DiscordUsernameKey
import moe.koiverse.archivetune.ui.component.DefaultDialog
import moe.koiverse.archivetune.ui.component.TextFieldDialog
import moe.koiverse.archivetune.utils.PreferenceStore.launchEdit

@Composable
fun DiscordTokenLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (UserInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var token by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun isValidTokenFormat(token: String): Boolean {
        // Discord tokens are typically base64-like strings
        // They should be at least 50 characters and contain only valid characters
        return token.length >= 50 && token.matches(Regex("^[A-Za-z0-9+/=_\\-.]+$"))
    }

    fun loginWithToken() {
        errorMessage = null

        if (!isValidTokenFormat(token)) {
            errorMessage = context.getString(R.string.discord_token_invalid_format)
            return
        }

        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = KizzyRPC.getUserInfo(token)
                result.onSuccess { userInfo ->
                    // Store token in preferences
                    launchEdit(context.dataStore) {
                        this[DiscordTokenKey] = token
                        this[DiscordUsernameKey] = userInfo.username
                        this[DiscordNameKey] = userInfo.name
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.discord_token_login_success, "${userInfo.username}#${userInfo.name}"),
                            Toast.LENGTH_SHORT
                        ).show()
                        onLoginSuccess(userInfo)
                        onDismiss()
                    }
                }.onFailure { exception ->
                    errorMessage = context.getString(R.string.discord_token_login_failed)
                }
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.discord_token_network_error)
            } finally {
                isLoading = false
            }
        }
    }

    fun pasteFromClipboard() {
        val clipboardText = clipboardManager.getText()?.text
        if (!clipboardText.isNullOrBlank()) {
            token = clipboardText.toString()
        }
    }

    TextFieldDialog(
        icon = {
            Icon(
                painter = painterResource(R.drawable.discord),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(R.string.discord_token_login_title))
        },
        initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(token),
        onTextFieldsChange = { _, newToken ->
            token = newToken.text
            errorMessage = null
        },
        onDoneMultiple = { tokenList ->
            loginWithToken()
        },
        onDismiss = onDismiss,
        textFields = listOf(
            "Token" to androidx.compose.ui.text.input.TextFieldValue(token)
        ),
        isInputValid = { it.second.text.length >= 50 },
        autoFocus = true,
        extraContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.discord_token_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = stringResource(R.string.discord_token_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
