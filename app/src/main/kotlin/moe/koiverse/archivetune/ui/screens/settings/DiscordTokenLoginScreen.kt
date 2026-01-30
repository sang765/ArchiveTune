package moe.koiverse.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.my.kizzy.rpc.UserInfo
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.LocalPlayerAwareWindowInsets
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.constants.DiscordTokenKey
import moe.koiverse.archivetune.constants.DiscordUsernameKey
import moe.koiverse.archivetune.constants.DiscordNameKey
import moe.koiverse.archivetune.ui.component.IconButton
import moe.koiverse.archivetune.ui.component.InfoLabel
import moe.koiverse.archivetune.ui.utils.backToMain
import moe.koiverse.archivetune.utils.DiscordTokenValidator
import moe.koiverse.archivetune.utils.rememberPreference
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordTokenLoginScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    
    var tokenInput by rememberSaveable { mutableStateOf("") }
    var isValidating by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var showTokenVisibility by rememberSaveable { mutableStateOf(false) }
    var validationSuccess by rememberSaveable { mutableStateOf(false) }
    var userInfo by rememberSaveable { mutableStateOf<UserInfo?>(null) }
    
    // Check if user is already logged in
    val isLoggedIn = remember(discordToken) { discordToken.isNotEmpty() }
    
    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            // If already logged in, navigate to token view screen
            navController.navigate("settings/discord/token/view") {
                popUpTo("settings/discord/login") {
                    inclusive = true
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.token_login_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )
            
            Text(
                text = stringResource(R.string.token_login_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            // Security warning
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.token_security_warning),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Info about token usage
            InfoLabel(text = stringResource(R.string.token_info))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Token input field
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { 
                    tokenInput = it
                    validationError = null
                    validationSuccess = false
                },
                label = { Text(stringResource(R.string.enter_discord_token)) },
                placeholder = { Text(stringResource(R.string.token_hint)) },
                visualTransformation = if (showTokenVisibility) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                trailingIcon = {
                    IconButton(onClick = { showTokenVisibility = !showTokenVisibility }) {
                        Icon(
                            imageVector = if (showTokenVisibility) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (showTokenVisibility) {
                                stringResource(R.string.hide_token)
                            } else {
                                stringResource(R.string.show_token)
                            }
                        )
                    }
                },
                isError = validationError != null,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (validationError != null) {
                Text(
                    text = validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (validationSuccess && userInfo != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.token_validation_success),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        userInfo?.let { user ->
                            Text(
                                text = "${stringResource(R.string.welcome_back)}, ${user.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (user.username.isNotEmpty()) {
                                Text(
                                    text = "@${user.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { 
                        if (tokenInput.isBlank()) {
                            validationError = context.getString(R.string.token_required)
                        } else if (!DiscordTokenValidator.isValidTokenFormat(tokenInput)) {
                            validationError = context.getString(R.string.invalid_token_format)
                        } else {
                            // Validate token
                            isValidating = true
                            validationError = null
                            
                            coroutineScope.launch {
                                try {
                                    val result = DiscordTokenValidator.validateToken(tokenInput)
                                    result.fold(
                                        onSuccess = { info ->
                                            userInfo = info
                                            validationSuccess = true
                                            isValidating = false
                                        },
                                        onFailure = { error ->
                                            validationError = error.message ?: context.getString(R.string.token_validation_failed)
                                            isValidating = false
                                        }
                                    )
                                } catch (e: Exception) {
                                    validationError = context.getString(R.string.token_validation_failed)
                                    isValidating = false
                                }
                            }
                        }
                    },
                    enabled = !isValidating && tokenInput.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.validate_token))
                    }
                }
                
                Button(
                    onClick = { 
                        if (validationSuccess) {
                            // Save token and navigate back
                            discordToken = tokenInput
                            discordUsername = userInfo?.username ?: ""
                            discordName = userInfo?.name ?: ""
                            
                            navController.navigateUp()
                        } else {
                            validationError = context.getString(R.string.token_validation_failed)
                        }
                    },
                    enabled = validationSuccess && !isValidating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save_token))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Alternative login option
            TextButton(
                onClick = { 
                    navController.navigate("settings/discord/login") {
                        popUpTo("settings/discord/token/login") {
                            inclusive = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.login_with_browser))
            }
        }
    }
}