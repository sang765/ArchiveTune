/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.koiverse.archivetune

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import moe.koiverse.archivetune.db.entities.Song
import moe.koiverse.archivetune.ui.component.DefaultDialog
import moe.koiverse.archivetune.ui.menu.AddToPlaylistDialogOnline
import moe.koiverse.archivetune.ui.menu.LoadingScreen
import moe.koiverse.archivetune.ui.theme.ArchiveTuneTheme
import moe.koiverse.archivetune.viewmodels.BackupCategory
import moe.koiverse.archivetune.viewmodels.BackupRestoreViewModel
import kotlin.math.floor

@AndroidEntryPoint
class FileImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        setContent {
            ArchiveTuneTheme {
                FileImportScreen(uri = uri, onFinish = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileImportScreen(
    uri: Uri,
    onFinish: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsState()

    var showRestoreOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showChoosePlaylistDialogOnline by rememberSaveable { mutableStateOf(false) }
    val importedSongs = remember { mutableStateListOf<Song>() }

    var isProgressStarted by rememberSaveable { mutableStateOf(false) }
    var progressPercentage by rememberSaveable { mutableIntStateOf(0) }
    var progressStatus by remember { mutableStateOf("") }

    LaunchedEffect(uri) {
        val fileName = getFileName(context, uri)
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""

        when (extension) {
            "backup" -> {
                showRestoreOptionsDialog = true
            }
            "m3u" -> {
                coroutineScope.launch {
                    val result = viewModel.loadM3UOnline(context, uri)
                    if (result.isNotEmpty()) {
                        importedSongs.clear()
                        importedSongs.addAll(result)
                        showChoosePlaylistDialogOnline = true
                    } else {
                        onFinish()
                    }
                }
            }
            "csv" -> {
                coroutineScope.launch {
                    val result = viewModel.importPlaylistFromCsv(context, uri)
                    if (result.isNotEmpty()) {
                        importedSongs.clear()
                        importedSongs.addAll(result)
                        showChoosePlaylistDialogOnline = true
                    } else {
                        onFinish()
                    }
                }
            }
            else -> {
                Toast.makeText(context, "Unsupported file format", Toast.LENGTH_SHORT).show()
                onFinish()
            }
        }
    }

    if (showRestoreOptionsDialog) {
        BackupOptionsDialog(
            title = stringResource(R.string.restore_options_title),
            confirmLabel = stringResource(R.string.action_restore),
            onConfirm = { categories ->
                showRestoreOptionsDialog = false
                viewModel.restore(context, uri, categories)
            },
            onDismiss = {
                showRestoreOptionsDialog = false
                onFinish()
            },
        )
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        songs = importedSongs,
        onDismiss = {
            showChoosePlaylistDialogOnline = false
            onFinish()
        },
        onProgressStart = { isProgressStarted = it },
        onPercentageChange = { progressPercentage = it },
        onStatusChange = { progressStatus = it }
    )

    LoadingScreen(
        isVisible = backupRestoreProgress != null || isProgressStarted,
        value = backupRestoreProgress?.percent ?: progressPercentage,
        title = backupRestoreProgress?.title,
        stepText = backupRestoreProgress?.step ?: progressStatus,
        indeterminate = backupRestoreProgress?.indeterminate ?: false,
    )
}

private fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    return cursor.getString(index)
                }
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}

@Composable
private fun BackupOptionsDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    val density = LocalDensity.current
    val cbStrokeWidthPx = remember(density) { with(density) { floor(CheckboxDefaults.StrokeWidth.toPx()) } }
    val cbCheckmarkStroke = remember(cbStrokeWidthPx) {
        Stroke(width = cbStrokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val cbOutlineStroke = remember(cbStrokeWidthPx) { Stroke(width = cbStrokeWidthPx) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = { onConfirm(selected) },
                shapes = ButtonDefaults.shapes(),
                enabled = selected.isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        BackupCategory.entries.forEach { category ->
            val isChecked = category in selected
            val labelRes = when (category) {
                BackupCategory.LIBRARY -> R.string.backup_category_library
                BackupCategory.ACCOUNT -> R.string.backup_category_account
                BackupCategory.SETTINGS -> R.string.backup_category_settings
            }
            val descRes = when (category) {
                BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
            }
            val iconRes = when (category) {
                BackupCategory.LIBRARY -> R.drawable.library_music
                BackupCategory.ACCOUNT -> R.drawable.account
                BackupCategory.SETTINGS -> R.drawable.settings
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        selected = if (isChecked) selected - category else selected + category
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        selected = if (checked) selected + category else selected - category
                    },
                    checkmarkStroke = cbCheckmarkStroke,
                    outlineStroke = cbOutlineStroke,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
