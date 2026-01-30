package moe.koiverse.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
fun DiscordTokenViewScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    
    var showToken by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var editedToken by rememberSaveable { mutableStateOf("") }
    var editTokenVisibility by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var isValidating by rememberSaveable { mutableStateOf(false) }
    var validationSuccess by rememberSaveable { mutableStateOf(false) }
    var userInfo by rememberSaveable { mutableStateOf<UserInfo?>(null) }
    
    // Check if user is logged in
    val isLoggedIn = remember(discordToken) { discordToken.isNotEmpty() }
    
    LaunchedEffect(Unit) {
        if (!isLoggedIn) {
            // If not logged in, navigate back to login
            navController.navigateUp()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.token_view_title)) },
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
                text = stringResource(R.string.token_management),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            // Security warning
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical  = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.token_security_warning),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Current token display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.current_token),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Token display with toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showToken && discordToken.isNotEmpty()) {
                                discordToken
                            } else {
                                stringResource(R.string.token_masked)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
                                                },
                                contentDescription = if (showToken) {
                                                    stringResource(R.string.hide_token)
                                                } else {
                                                    stringResource(R.string.show_token)
                                                }
                            )
                        }
                        
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(discordToken))
                            // Show snackbar or toast
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_token_to_clipboard)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Token actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.edit_token))
                        }
                        
                        Button(
                            onClick = { 
                                // Copy token to clipboard
                                clipboardManager.setText(AnnotatedString(discordToken))
                                // Show success message
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.copy_token_to_clipboard))
                        }
                    }
                }
            }
            
            // Account info
            if (discordName.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.account),
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = discordName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        if (discordUsername.isNotEmpty()) {
                            Text(
                                text = "@$discordUsername",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Info about token usage
            InfoLabel(text = stringResource(R.string.token_info))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Additional actions
            Text(
                text = stringResource(R.string.token_actions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // Revalidate token button
            OutlinedButton(
                onClick = { 
                    if (discordToken.isNotBlank()) {
                        isValidating = true
                        validationError = null
                        
                        coroutineScope.launch {
                            try {
                                val result = DiscordTokenValidator.validateToken(discordToken)
                                result.fold(
                                    onSuccess = { info ->
                                        userInfo = info
                                        validationSuccess = true
                                        isValidating = false
                                        // Update user info
                                        discordUsername = info.username
                                        discordName = info.name
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
                enabled = !isValidating && discordToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
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
                        .padding(vertical = 8.dp)
                ) {
                    userInfo?.let { user ->
                        Text(
                            text = stringResource(R.string.token_validation_success) + " for @${user.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Edit token dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_token)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedToken,
                        onValueChange = { editedToken = it },
                        label = { Text(stringResource(R.string.enter_discord_token)) },
                        placeholder = { Text(stringResource(R.string.token_hint)) },
                        visualTransformation = if (editTokenVisibility) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        trailingIcon = {
                            IconButton(onClick = { editTokenVisibility = !editTokenVisibility }) {
                                Icon(
                                    imageVector = if (editTokenVisibility) {
                                                        Icons.Default.VisibilityOff
                                                    } else {
                                                        Icons.Default.Visibility
                                                    },
                                    contentDescription = if (editTokenVisibility) {
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
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showEditDialog = false }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    
                    Button(
                        onClick = { 
                            if (editedToken.isBlank()) {
                                validationError = context.getString(R.string.token_required)
                            } else if (!DiscordTokenValidator.isValidTokenFormat(editedToken)) {
                                validationError = context.getString(R.string.invalid_token_format)
                            } else {
                                // Validate and save token
                                isValidating = true
                                validationError = null
                                
                                coroutineScope.launch {
                                    try {
                                        val result = DiscordTokenValidator.validateToken(editedToken)
                                        result.fold(
                                            onSuccess = { info ->
                                                // Save new token
                                                discordToken = editedToken
                                                discordUsername = info.username
                                                discordName = info.name
                                                
                                                validationSuccess = true
                                                isValidating = false
                                                showEditDialog = false
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
                        enabled = !isValidating && editedToken.isNotBlank()
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.save_token))
                        }
                    }
                }
            }
        )
    }
}