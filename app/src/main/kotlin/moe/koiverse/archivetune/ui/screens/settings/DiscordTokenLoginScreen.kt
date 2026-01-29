package moe.koiverse.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.koiverse.archivetune.LocalPlayerAwareWindowInsets
import moe.koiverse.archivetune.R
import moe.koiverse.archivetune.constants.DiscordTokenKey
import moe.koiverse.archivetune.constants.DiscordUsernameKey
import moe.koiverse.archivetune.constants.DiscordNameKey
import moe.koiverse.archivetune.ui.component.IconButton
import moe.koiverse.archivetune.ui.utils.backToMain
import moe.koiverse.archivetune.utils.rememberPreference
import com.my.kizzy.rpc.KizzyRPC
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordTokenLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    
    var tokenInput by rememberSaveable { mutableStateOf(discordToken) }
    var isTokenVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    
    val isLoggedIn = remember(discordToken) { discordToken.isNotEmpty() }
    
    // Initialize token input with current token when screen loads
    LaunchedEffect(discordToken) {
        if (tokenInput.isEmpty() && discordToken.isNotEmpty()) {
            tokenInput = discordToken
        }
    }
    
    fun validateAndSaveToken() {
        val trimmedToken = tokenInput.trim()
        
        // Basic token validation
        if (trimmedToken.isEmpty()) {
            tokenError = context.getString(R.string.discord_token_invalid)
            return
        }
        
        // Discord tokens are typically 59+ characters and contain dots
        if (trimmedToken.length < 50 || !trimmedToken.contains(".")) {
            tokenError = context.getString(R.string.discord_token_invalid)
            return
        }
        
        isLoading = true
        tokenError = null
        
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Validate token by getting user info
                    KizzyRPC.getUserInfo(trimmedToken)
                }.onSuccess { userInfo ->
                    // Token is valid, save it
                    discordToken = trimmedToken
                    discordUsername = userInfo.username
                    discordName = userInfo.name
                    
                    // Show success message and navigate back
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Successfully logged in!")
                        navController.navigateUp()
                    }
                }.onFailure { error ->
                    tokenError = context.getString(R.string.discord_token_invalid)
                    Timber.tag("DiscordTokenLogin").w(error, "Token validation failed")
                }
            } catch (e: Exception) {
                tokenError = context.getString(R.string.discord_token_invalid)
                Timber.tag("DiscordTokenLogin").w(e, "Token validation failed")
            } finally {
                isLoading = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isLoggedIn) stringResource(R.string.discord_view_edit_token) 
                        else stringResource(R.string.discord_token_login)
                    ) 
                },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.security),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.discord_token_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Token input field
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { 
                    tokenInput = it
                    tokenError = null
                },
                label = { Text(stringResource(R.string.discord_enter_token)) },
                placeholder = { Text(stringResource(R.string.discord_token_hint)) },
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            painterResource(if (isTokenVisible) R.drawable.hide_image else R.drawable.token),
                            contentDescription = if (isTokenVisible) "Hide token" else "Show token"
                        )
                    }
                },
                isError = tokenError != null,
                supportingText = tokenError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )
            
            // Current token display (if logged in)
            if (isLoggedIn && discordToken.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current Token",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        SelectionContainer {
                            Text(
                                text = if (isTokenVisible) discordToken else "•".repeat(discordToken.length.coerceAtMost(50)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (discordName.isNotEmpty()) {
                            Text(
                                text = "Logged in as: $discordName${if (discordUsername.isNotEmpty()) " (@$discordUsername)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                
                Button(
                    onClick = ::validateAndSaveToken,
                    enabled = !isLoading && tokenInput.trim().isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.action_login))
                    }
                }
            }
        }
    }
}
