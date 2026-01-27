package moe.koiverse.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.my.kizzy.rpc.KizzyRPC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.koiverse.archivetune.LocalPlayerAwareWindowInsets
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.constants.DiscordNameKey
import moe.koiverse.archivetune.constants.DiscordTokenKey
import moe.koiverse.archivetune.constants.DiscordUsernameKey
import moe.koiverse.archivetune.ui.component.IconButton
import moe.koiverse.archivetune.ui.utils.backToMain
import moe.koiverse.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordTokenInputScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val emptyTokenMessage = stringResource(R.string.discord_token_error_empty)
    val invalidTokenMessage = stringResource(R.string.discord_token_error_invalid)
    val tokenSavedMessage = stringResource(R.string.discord_token_saved)
    val validatingMessage = stringResource(R.string.discord_token_validating)

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")

    var tokenInput by rememberSaveable { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun cancel() {
        focusManager.clearFocus()
        keyboardController?.hide()
        navController.navigateUp()
    }

    fun save() {
        val trimmed = tokenInput.trim()
        if (trimmed.isEmpty()) {
            errorMessage = emptyTokenMessage
            scope.launch { snackbarHostState.showSnackbar(emptyTokenMessage) }
            return
        }

        errorMessage = null
        isValidating = true

        scope.launch(Dispatchers.IO) {
            KizzyRPC.getUserInfo(trimmed).onSuccess { userInfo ->
                withContext(Dispatchers.Main) {
                    isValidating = false
                    errorMessage = null
                    discordToken = trimmed
                    discordUsername = userInfo.username
                    discordName = userInfo.name

                    focusManager.clearFocus()
                    keyboardController?.hide()

                    snackbarHostState.showSnackbar(
                        message = tokenSavedMessage,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                    navController.navigateUp()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    isValidating = false
                    errorMessage = invalidTokenMessage
                    snackbarHostState.showSnackbar(invalidTokenMessage)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            Text(
                text = stringResource(R.string.discord_token_login_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Text(
                text = stringResource(R.string.discord_token_login_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            OutlinedTextField(
                value = tokenInput,
                onValueChange = {
                    tokenInput = it
                    errorMessage = null
                },
                label = { Text(stringResource(R.string.discord_token)) },
                placeholder = { Text("mfa.xxxxx... or xxxxx.yyyyy.zzzzz") },
                isError = errorMessage != null,
                enabled = !isValidating,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (tokenVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    val description = if (tokenVisible)
                        stringResource(R.string.hide_token)
                    else stringResource(R.string.show_token)

                    androidx.compose.material3.IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                supportingText = {
                    if (isValidating) {
                        Text(
                            text = validatingMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { save() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Text(
                text = stringResource(R.string.discord_token_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = stringResource(R.string.discord_token_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { cancel() },
                    modifier = Modifier.weight(1f),
                    enabled = !isValidating,
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = { save() },
                    modifier = Modifier.weight(1f),
                    enabled = !isValidating,
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.action_login))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        TopAppBar(
            title = { Text(stringResource(R.string.advanced_login)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
        )
    }
}
