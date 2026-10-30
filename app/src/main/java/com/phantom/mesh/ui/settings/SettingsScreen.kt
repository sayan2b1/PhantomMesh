package com.phantom.mesh.ui.settings

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.foundation.layout.PaddingValues
import com.phantom.mesh.BuildConfig
import com.phantom.mesh.update.DownloadState
import com.phantom.mesh.update.UpdateCheckResult
import com.phantom.mesh.ui.update.ChangelogViewerDialog
import com.phantom.mesh.ui.update.UpdateAvailableDialog
import com.phantom.mesh.model.ConversationMode
import com.phantom.mesh.util.HardwareStateUtil
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.phantom.mesh.data.local.backup.BackupFrequency
import com.phantom.mesh.util.SecurityQuestions
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DarkSurface
import com.phantom.mesh.ui.theme.DarkSurfaceVariant
import com.phantom.mesh.ui.theme.DuressCrimson
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.NeonGreen
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import com.phantom.mesh.ui.theme.TransportBluetooth
import com.phantom.mesh.ui.theme.TransportCloud
import com.phantom.mesh.ui.theme.WarningAmber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubPage {
    MAIN,
    BACKUP_RESTORE,
    CHAT_APPEARANCE,
    PRIVACY_SECURITY,
    DISPLAY_PERFORMANCE,
    ACCOUNT_CREDENTIALS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onLogoutOrWiped: () -> Unit
) {
    val username by viewModel.username.collectAsState()
    val isDuress by viewModel.isDuress.collectAsState()
    val isBluetoothOnlyMode by viewModel.isBluetoothOnlyMode.collectAsState()
    val conversationMode by viewModel.conversationMode.collectAsState()
    val chatWallpaperPath by viewModel.chatWallpaperPath.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isChangingUsername by viewModel.isChangingUsername.collectAsState()
    val context = LocalContext.current

    // Chat appearance & personalizations
    val senderBubbleColor by viewModel.senderBubbleColor.collectAsState()
    val receiverBubbleColor by viewModel.receiverBubbleColor.collectAsState()
    val chatFontStyle by viewModel.chatFontStyle.collectAsState()
    val chatFontSizeScale by viewModel.chatFontSizeScale.collectAsState()
    val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsState()
    val preferredRefreshRate by viewModel.preferredRefreshRate.collectAsState()

    // Backup states
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsState()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsState()
    val lastBackupSize by viewModel.lastBackupSize.collectAsState()
    val lastBackupMessageCount by viewModel.lastBackupMessageCount.collectAsState()
    val isBackupRunning by viewModel.isBackupRunning.collectAsState()
    val isRestoreRunning by viewModel.isRestoreRunning.collectAsState()
    val availableBackups by viewModel.availableBackups.collectAsState()
    val securityQuestion by viewModel.securityQuestion.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val isDndEnabled by viewModel.isDndEnabled.collectAsState()

    var currentSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }

    // Intercept system back button to go back to Settings main menu first
    BackHandler(enabled = currentSubPage != SettingsSubPage.MAIN) {
        currentSubPage = SettingsSubPage.MAIN
    }

    var showNukeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showSecurityQuestionDialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    // Over-the-air update states
    val downloadState by viewModel.downloadState.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    var showChangelogDialog by remember { mutableStateOf(false) }
    var upToDateDialogVersion by remember { mutableStateOf<String?>(null) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(updateCheckResult) {
        when (val res = updateCheckResult) {
            is UpdateCheckResult.UpToDate -> {
                upToDateDialogVersion = res.currentVersion
            }
            is UpdateCheckResult.Error -> {
                updateErrorMessage = res.message
            }
            is UpdateCheckResult.NoInternet -> {
                updateErrorMessage = "No internet connection. Please verify your network connection and try again."
            }
            else -> {}
        }
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.setWallpaperFromUri(context, it) }
    }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.restoreFromUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentSubPage) {
                            SettingsSubPage.MAIN -> "SETTINGS"
                            SettingsSubPage.BACKUP_RESTORE -> "CHAT BACKUP & RESTORE"
                            SettingsSubPage.CHAT_APPEARANCE -> "CHAT THEME & APPEARANCE"
                            SettingsSubPage.PRIVACY_SECURITY -> "PRIVACY & SECURITY"
                            SettingsSubPage.DISPLAY_PERFORMANCE -> "DISPLAY & REFRESH RATE"
                            SettingsSubPage.ACCOUNT_CREDENTIALS -> "CHANGE PASSWORD & USERNAME"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubPage != SettingsSubPage.MAIN) {
                            currentSubPage = SettingsSubPage.MAIN
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status Feedback Banner (if any message set)
            if (!statusMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan, RoundedCornerShape(8.dp))
                        .clickable { viewModel.clearStatusMessage() }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = currentSubPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "SettingsSubPageTransition"
            ) { targetPage ->
                when (targetPage) {
                    SettingsSubPage.MAIN -> {
                        SettingsMainContent(
                            username = username,
                            isDuress = isDuress,
                            isBluetoothOnlyMode = isBluetoothOnlyMode,
                            conversationMode = conversationMode,
                            chatWallpaperPath = chatWallpaperPath,
                            lastBackupTimestamp = lastBackupTimestamp,
                            lastBackupMessageCount = lastBackupMessageCount,
                            readReceiptsEnabled = readReceiptsEnabled,
                            preferredRefreshRate = preferredRefreshRate,
                            isCheckingUpdate = isCheckingUpdate,
                            onCheckForUpdates = { viewModel.checkForUpdatesManual() },
                            onViewChangelogs = { showChangelogDialog = true },
                            onToggleReadReceipts = { viewModel.setReadReceiptsEnabled(it) },
                            onToggleBluetoothOnly = { viewModel.setBluetoothOnlyMode(it) },
                            onSelectConversationMode = { viewModel.setConversationMode(it) },
                            onNavigate = { currentSubPage = it },
                            onLogoutClick = { showLogoutDialog = true },
                            onForgotPasswordClick = { showForgotPasswordDialog = true }
                        )
                    }
                    SettingsSubPage.BACKUP_RESTORE -> {
                        SettingsBackupRestoreContent(
                            autoBackupFrequency = autoBackupFrequency,
                            lastBackupTimestamp = lastBackupTimestamp,
                            lastBackupSize = lastBackupSize,
                            lastBackupMessageCount = lastBackupMessageCount,
                            isBackupRunning = isBackupRunning,
                            isRestoreRunning = isRestoreRunning,
                            onBackupNow = { viewModel.performBackupNow() },
                            onOpenRestore = {
                                viewModel.refreshAvailableBackups()
                                showRestoreDialog = true
                            },
                            onSetFrequency = { viewModel.setAutoBackupFrequency(it) }
                        )
                    }
                    SettingsSubPage.CHAT_APPEARANCE -> {
                        SettingsChatAppearanceContent(
                            chatWallpaperPath = chatWallpaperPath,
                            senderBubbleColor = senderBubbleColor,
                            receiverBubbleColor = receiverBubbleColor,
                            chatFontStyle = chatFontStyle,
                            chatFontSizeScale = chatFontSizeScale,
                            onChoosePhoto = { wallpaperPickerLauncher.launch("image/*") },
                            onResetWallpaper = { viewModel.removeWallpaper(context) },
                            onSelectSenderColor = { viewModel.setSenderBubbleColor(it) },
                            onSelectReceiverColor = { viewModel.setReceiverBubbleColor(it) },
                            onSelectFontStyle = { viewModel.setChatFontStyle(it) },
                            onSelectFontSizeScale = { viewModel.setChatFontSizeScale(it) }
                        )
                    }
                    SettingsSubPage.PRIVACY_SECURITY -> {
                        SettingsPrivacySecurityContent(
                            readReceiptsEnabled = readReceiptsEnabled,
                            isDndEnabled = isDndEnabled,
                            blockedUsers = blockedUsers,
                            currentSecurityQuestion = securityQuestion,
                            onToggleReadReceipts = { viewModel.setReadReceiptsEnabled(it) },
                            onToggleDnd = { viewModel.setDndEnabled(it) },
                            onUnblockUser = { viewModel.unblockUser(it) },
                            getDisplayName = { viewModel.getFormattedDisplayName(it) },
                            onOpenSecurityQuestionDialog = { showSecurityQuestionDialog = true },
                            onSaveDecoy = { viewModel.updateDecoyPassword(it) },
                            onTriggerEmergencyWipe = { showNukeDialog = true }
                        )
                    }
                    SettingsSubPage.DISPLAY_PERFORMANCE -> {
                        SettingsDisplayPerformanceContent(
                            context = context,
                            preferredRefreshRate = preferredRefreshRate,
                            onSelectRefreshRate = { viewModel.setPreferredRefreshRate(it) }
                        )
                    }
                    SettingsSubPage.ACCOUNT_CREDENTIALS -> {
                        SettingsAccountCredentialsContent(
                            currentUsername = username ?: "",
                            isChangingUsername = isChangingUsername,
                            onChangeUsername = { viewModel.changeUsername(it) },
                            onChangePassword = { old, new, confirm ->
                                viewModel.changePassword(old, new, confirm)
                            },
                            onForgotPasswordClick = { showForgotPasswordDialog = true }
                        )
                    }
                }
            }
        }
    }

    // ── LOGOUT CONFIRMATION DIALOG ──────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to log out? Your local encryption keys and active session will be signed out.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLoggedOut = onLogoutOrWiped)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("LOG OUT", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = DarkCard
        )
    }

    // ── EMERGENCY WIPE (NUKE) CONFIRMATION DIALOG ───────────────────────────
    if (showNukeDialog) {
        AlertDialog(
            onDismissRequest = { showNukeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DuressCrimson,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("DESTROY ALL DATA?", color = DuressCrimson, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This action is permanent and IRREVERSIBLE. It will instantly wipe all encryption keys, conversations, contact nicknames, and media from this device.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = DuressCrimson),
                    onClick = {
                        showNukeDialog = false
                        viewModel.triggerEmergencyWipe(onWiped = onLogoutOrWiped)
                    }
                ) {
                    Text("DESTROY ALL DATA", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNukeDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // ── RESTORE CHAT BACKUP DIALOG ──────────────────────────────────────────
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Chat Backup", color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Restoring imports all saved chats, messages, and contact nicknames into your local database without losing any new messages.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (availableBackups.isNotEmpty()) {
                        Text(
                            text = "DISCOVERED BACKUPS (${availableBackups.size}):",
                            fontSize = 10.sp,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableBackups.forEach { backup ->
                                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(backup.timestamp))
                                val sizeStr = when {
                                    backup.sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", backup.sizeBytes.toFloat() / (1024 * 1024))
                                    backup.sizeBytes >= 1024 -> String.format(Locale.US, "%d kB", backup.sizeBytes / 1024)
                                    else -> "${backup.sizeBytes} B"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = backup.fileName,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$dateStr • ${backup.messageCount} msgs • $sizeStr",
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.restoreFromFile(File(backup.filePath))
                                            showRestoreDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("RESTORE", fontSize = 10.sp, color = DarkBackground, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Browse custom backup file
                    Button(
                        onClick = {
                            showRestoreDialog = false
                            backupPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse Files / Storage...", color = NeonCyan, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = DarkCard
        )
    }

    // ── SECURITY QUESTION CONFIGURATION DIALOG ──────────────────────────────
    if (showSecurityQuestionDialog) {
        SecurityQuestionSettingsDialog(
            currentQuestion = securityQuestion,
            onDismiss = { showSecurityQuestionDialog = false },
            onSave = { question, answer ->
                viewModel.updateSecurityQuestion(question, answer)
            }
        )
    }

    // ── FORGOT PASSWORD RECOVERY DIALOG ─────────────────────────────────────
    if (showForgotPasswordDialog) {
        ForgotPasswordSettingsDialog(
            securityQuestion = securityQuestion,
            onDismiss = { showForgotPasswordDialog = false },
            onOpenSetupQuestion = {
                showSecurityQuestionDialog = true
            },
            onResetPassword = { answer, newPass, confirmPass, onDone ->
                viewModel.resetPasswordWithSecurityAnswer(answer, newPass, confirmPass, onDone)
            }
        )
    }

    // ── IN-APP OVER-THE-AIR (OTA) UPDATE DIALOGS ────────────────────────────
    (updateCheckResult as? UpdateCheckResult.UpdateAvailable)?.let { updateAvailable ->
        UpdateAvailableDialog(
            release = updateAvailable.release,
            downloadState = downloadState,
            onUpdateClick = { viewModel.downloadAndInstall(updateAvailable.release) },
            onRemindLaterClick = { viewModel.remindUpdateLater() },
            onIgnoreClick = { viewModel.ignoreUpdate(updateAvailable.release.tagName) },
            onDismissRequest = { viewModel.dismissUpdateDialog() }
        )
    }

    upToDateDialogVersion?.let { ver ->
        AlertDialog(
            onDismissRequest = {
                upToDateDialogVersion = null
                viewModel.dismissUpdateDialog()
            },
            containerColor = DarkSurface,
            titleContentColor = NeonCyan,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Up to Date", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "PhantomMesh v$ver is the latest release. No new updates are available at this time.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        upToDateDialogVersion = null
                        viewModel.dismissUpdateDialog()
                    }
                ) {
                    Text("OK", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    updateErrorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = {
                updateErrorMessage = null
                viewModel.dismissUpdateDialog()
            },
            containerColor = DarkSurface,
            titleContentColor = DuressCrimson,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DuressCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update Check", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = err,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateErrorMessage = null
                        viewModel.dismissUpdateDialog()
                    }
                ) {
                    Text("DISMISS", color = NeonCyan)
                }
            }
        )
    }

    if (showChangelogDialog) {
        ChangelogViewerDialog(
            versionName = "v${BuildConfig.VERSION_NAME}",
            changelogText = viewModel.getCurrentSavedReleaseNotes(),
            onDismiss = { showChangelogDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. MAIN SETTINGS MENU LIST
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsMainContent(
    username: String?,
    isDuress: Boolean,
    isBluetoothOnlyMode: Boolean,
    conversationMode: ConversationMode,
    chatWallpaperPath: String?,
    lastBackupTimestamp: Long,
    lastBackupMessageCount: Int,
    readReceiptsEnabled: Boolean,
    preferredRefreshRate: Int,
    isCheckingUpdate: Boolean,
    onCheckForUpdates: () -> Unit,
    onViewChangelogs: () -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleBluetoothOnly: (Boolean) -> Unit,
    onSelectConversationMode: (ConversationMode) -> Unit,
    onNavigate: (SettingsSubPage) -> Unit,
    onLogoutClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // User Profile Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.5.dp, NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (username?.take(1) ?: "U").uppercase(),
                        color = NeonCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = username?.let { "@$it" } ?: "Encrypted User",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isDuress) "Duress Session Active" else "Mesh & Cloud Connected",
                        fontSize = 12.sp,
                        color = if (isDuress) DuressCrimson else NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── CHATS & DATA ────────────────────────────────────────────────────
        Text(
            text = "CHATS & DATA",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        val backupSubtitle = if (lastBackupTimestamp > 0) {
            val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastBackupTimestamp))
            "Last backup: $date ($lastBackupMessageCount msgs)"
        } else {
            "Single-file backup • Never backed up"
        }

        SettingsNavigationTile(
            title = "Chat Backup and Restore",
            subtitle = backupSubtitle,
            icon = Icons.Default.Backup,
            iconTint = NeonCyan,
            onClick = { onNavigate(SettingsSubPage.BACKUP_RESTORE) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsNavigationTile(
            title = "Chat Theme & Appearance",
            subtitle = "Wallpaper • Bubble colors • Adaptive font • Text size",
            icon = Icons.Default.Palette,
            iconTint = NeonCyan,
            onClick = { onNavigate(SettingsSubPage.CHAT_APPEARANCE) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── PRIVACY & SECURITY ──────────────────────────────────────────────
        Text(
            text = "PRIVACY & SECURITY",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Direct Quick Toggle: Read Receipts On/Off
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (readReceiptsEnabled) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (readReceiptsEnabled) NeonCyan else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Read Receipts",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (readReceiptsEnabled) "ON (Double blue checks)" else "OFF (Checks stay neutral gray)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (readReceiptsEnabled) NeonCyan else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = readReceiptsEnabled,
                    onCheckedChange = onToggleReadReceipts,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsNavigationTile(
            title = "Privacy and Security",
            subtitle = "Read receipts (${if (readReceiptsEnabled) "ON" else "OFF"}) • Security question • Decoy • Wipe",
            icon = Icons.Default.Security,
            iconTint = DuressCrimson,
            onClick = { onNavigate(SettingsSubPage.PRIVACY_SECURITY) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsNavigationTile(
            title = "Change Password and username",
            subtitle = "Username: @${username ?: "..."} • Security credentials",
            icon = Icons.Default.Lock,
            iconTint = NeonCyan,
            onClick = { onNavigate(SettingsSubPage.ACCOUNT_CREDENTIALS) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsNavigationTile(
            title = "Forgot Password?",
            subtitle = "Reset password using secret recovery question",
            icon = Icons.Default.Lock,
            iconTint = WarningAmber,
            onClick = onForgotPasswordClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── DISPLAY & HARDWARE ──────────────────────────────────────────────
        Text(
            text = "DISPLAY & HARDWARE",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        SettingsNavigationTile(
            title = "Display & Refresh Rate",
            subtitle = "Refresh rate: ${if (preferredRefreshRate > 0) "$preferredRefreshRate Hz" else "Adaptive / Max"}",
            icon = Icons.Default.Speed,
            iconTint = NeonCyan,
            onClick = { onNavigate(SettingsSubPage.DISPLAY_PERFORMANCE) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        var showConversationModeMenu by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .clickable { showConversationModeMenu = true }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (conversationMode) {
                                    ConversationMode.HYBRID -> NeonCyan.copy(alpha = 0.15f)
                                    ConversationMode.ONLINE -> TransportCloud.copy(alpha = 0.15f)
                                    ConversationMode.OFFLINE -> TransportBluetooth.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (conversationMode) {
                                ConversationMode.HYBRID -> Icons.Default.SwapCalls
                                ConversationMode.ONLINE -> Icons.Default.Cloud
                                ConversationMode.OFFLINE -> Icons.Default.Bluetooth
                            },
                            contentDescription = null,
                            tint = when (conversationMode) {
                                ConversationMode.HYBRID -> NeonCyan
                                ConversationMode.ONLINE -> TransportCloud
                                ConversationMode.OFFLINE -> TransportBluetooth
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Select Conversation Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = conversationMode.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (conversationMode) {
                                ConversationMode.HYBRID -> NeonCyan
                                ConversationMode.ONLINE -> TransportCloud
                                ConversationMode.OFFLINE -> WarningAmber
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Dropdown indicator button
                IconButton(onClick = { showConversationModeMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select conversation mode dropdown",
                        tint = NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                DropdownMenu(
                    expanded = showConversationModeMenu,
                    onDismissRequest = { showConversationModeMenu = false },
                    modifier = Modifier
                        .background(DarkCard)
                        .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                ) {
                    ConversationMode.values().forEach { mode ->
                        val isSelected = (mode == conversationMode)
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = mode.title,
                                            color = if (isSelected) NeonCyan else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode.description,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (mode) {
                                        ConversationMode.HYBRID -> Icons.Default.SwapCalls
                                        ConversationMode.ONLINE -> Icons.Default.Cloud
                                        ConversationMode.OFFLINE -> Icons.Default.Bluetooth
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showConversationModeMenu = false
                                onSelectConversationMode(mode)
                                if (mode == ConversationMode.OFFLINE) {
                                    val bt = HardwareStateUtil.isBluetoothEnabled(context)
                                    val wifi = HardwareStateUtil.isWifiEnabled(context)
                                    val loc = HardwareStateUtil.isLocationEnabled(context)
                                    if (!bt || !wifi || !loc) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Offline Mode active: Bluetooth, Wi-Fi, and Location services are required.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Updates & Changelogs Section
        Text(
            text = "APP UPDATES & SYSTEM",
            color = NeonCyan,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Check for Updates",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Check GitHub for newer releases & update directly",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = NeonCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = onCheckForUpdates,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CHECK",
                                color = DarkBackground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkCard)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewChangelogs() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Release Notes & Changelogs",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Log out button
        Button(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            border = BorderStroke(1.dp, DuressCrimson.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = DuressCrimson,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LOG OUT OF PHANTOM MESH",
                color = DuressCrimson,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Version Display
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PhantomMesh v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                color = NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Decentralized P2P Mesh & Cloud Communicator",
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "End-to-End Encrypted • Zero Tracking",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsNavigationTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. SUB-PAGE: CHAT BACKUP AND RESTORE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsBackupRestoreContent(
    autoBackupFrequency: String,
    lastBackupTimestamp: Long,
    lastBackupSize: Long,
    lastBackupMessageCount: Int,
    isBackupRunning: Boolean,
    isRestoreRunning: Boolean,
    onBackupNow: () -> Unit,
    onOpenRestore: () -> Unit,
    onSetFrequency: (BackupFrequency) -> Unit
) {
    var showFrequencyMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Signal-style local device backup. Backups are stored in Download/PhantomMesh_Backups/phantom_chat_backup.json so your history survives app uninstalls and Firebase's 15-day server purge.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Master Backup File Status Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRIMARY BACKUP FILE",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (lastBackupTimestamp > 0) "SURVIVES UNINSTALL" else "NOT CONFIGURED",
                        fontSize = 9.sp,
                        color = if (lastBackupTimestamp > 0) NeonGreen else WarningAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "phantom_chat_backup.json",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (lastBackupTimestamp > 0) {
                    val dateFormatted = remember(lastBackupTimestamp) {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(lastBackupTimestamp))
                    }
                    val sizeStr = remember(lastBackupSize) {
                        when {
                            lastBackupSize >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", lastBackupSize.toFloat() / (1024 * 1024))
                            lastBackupSize >= 1024 -> String.format(Locale.US, "%d kB", lastBackupSize / 1024)
                            else -> "$lastBackupSize B"
                        }
                    }

                    Text(
                        text = "Last updated: $dateFormatted",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "$lastBackupMessageCount total messages saved • $sizeStr",
                        fontSize = 12.sp,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Directory: Download/PhantomMesh_Backups/",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                } else {
                    Text(
                        text = "Never backed up yet. Tap 'BACK UP NOW' below to create your first backup.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Backup Frequency Selector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .clickable { showFrequencyMenu = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Backup Frequency",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = BackupFrequency.fromKey(autoBackupFrequency).title,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Change frequency",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )

                    DropdownMenu(
                        expanded = showFrequencyMenu,
                        onDismissRequest = { showFrequencyMenu = false },
                        modifier = Modifier.background(DarkCard)
                    ) {
                        BackupFrequency.entries.forEach { freq ->
                            val isSelected = freq.key.equals(autoBackupFrequency, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = freq.title,
                                            color = if (isSelected) NeonCyan else TextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSetFrequency(freq)
                                    showFrequencyMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: BACK UP NOW & RESTORE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBackupNow,
                enabled = !isBackupRunning,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isBackupRunning) {
                    CircularProgressIndicator(
                        color = DarkBackground,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BACK UP NOW",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Button(
                onClick = onOpenRestore,
                enabled = !isRestoreRunning,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isRestoreRunning) {
                    CircularProgressIndicator(
                        color = NeonCyan,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RESTORE",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Note: Each backup appends new messages and rewrites the single primary backup file without cluttering your storage.",
            fontSize = 11.sp,
            color = TextMuted,
            fontStyle = FontStyle.Italic
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. SUB-PAGE: CHAT THEME & APPEARANCE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsChatAppearanceContent(
    chatWallpaperPath: String?,
    senderBubbleColor: String,
    receiverBubbleColor: String,
    chatFontStyle: String,
    chatFontSizeScale: String,
    onChoosePhoto: () -> Unit,
    onResetWallpaper: () -> Unit,
    onSelectSenderColor: (String) -> Unit,
    onSelectReceiverColor: (String) -> Unit,
    onSelectFontStyle: (String) -> Unit,
    onSelectFontSizeScale: (String) -> Unit
) {
    val senderPalette = listOf(
        Pair("#0D3B3F", "Cyber Cyan"),
        Pair("#1E293B", "Navy Slate"),
        Pair("#0A3828", "Emerald"),
        Pair("#2A183D", "Midnight Purple"),
        Pair("#3B1422", "Sunset Crimson"),
        Pair("#1F2430", "Stealth Charcoal")
    )

    val receiverPalette = listOf(
        Pair("#161B22", "Dark Obsidian"),
        Pair("#21262D", "Dark Surface"),
        Pair("#12241C", "Deep Forest"),
        Pair("#1D1528", "Dark Violet"),
        Pair("#1A202C", "Slate Blue"),
        Pair("#121212", "Pitch Dark")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── 1. CHAT WALLPAPER ───────────────────────────────────────────────
        Text(
            text = "CHAT WALLPAPER",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!chatWallpaperPath.isNullOrBlank()) {
                val file = remember(chatWallpaperPath) { File(chatWallpaperPath) }
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Theme Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = NeonCyan.copy(alpha = 0.4f),
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Default Dark Theme Active",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBackground.copy(alpha = 0.75f))
                    .border(1.dp, if (!chatWallpaperPath.isNullOrBlank()) NeonGreen else NeonCyan, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (!chatWallpaperPath.isNullOrBlank()) "CUSTOM ACTIVE" else "DEFAULT",
                    fontSize = 9.sp,
                    color = if (!chatWallpaperPath.isNullOrBlank()) NeonGreen else NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onChoosePhoto,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, NeonCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CHOOSE PHOTO", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            if (!chatWallpaperPath.isNullOrBlank()) {
                Button(
                    onClick = onResetWallpaper,
                    colors = ButtonDefaults.buttonColors(containerColor = DuressCrimson.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, DuressCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("RESET", color = DuressCrimson, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 2. SENDER BUBBLE COLOR ──────────────────────────────────────────
        Text(
            text = "SENDER MESSAGE BUBBLE COLOR",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            senderPalette.forEach { (hex, name) ->
                val isSelected = senderBubbleColor.equals(hex, ignoreCase = true)
                val color = remember(hex) {
                    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { DarkCard }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .clickable { onSelectSenderColor(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = name,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 3. RECEIVER BUBBLE COLOR ────────────────────────────────────────
        Text(
            text = "RECEIVER MESSAGE BUBBLE COLOR",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            receiverPalette.forEach { (hex, name) ->
                val isSelected = receiverBubbleColor.equals(hex, ignoreCase = true)
                val color = remember(hex) {
                    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { DarkSurfaceVariant }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .clickable { onSelectReceiverColor(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = name,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. ADAPTIVE FONT STYLE ──────────────────────────────────────────
        Text(
            text = "ADAPTIVE FONT STYLE",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        val fontOptions = listOf(
            Pair("DEVICE", "Device System Font (Adaptive)"),
            Pair("MONOSPACE", "Cyber Monospace"),
            Pair("SANS_SERIF", "Modern Sans-Serif"),
            Pair("SERIF", "Classic Serif")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fontOptions.forEach { (key, label) ->
                val isSelected = chatFontStyle.equals(key, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.12f) else DarkCard)
                        .border(1.dp, if (isSelected) NeonCyan else Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable { onSelectFontStyle(key) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) NeonCyan else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = when (key) {
                                "MONOSPACE" -> FontFamily.Monospace
                                "SANS_SERIF" -> FontFamily.SansSerif
                                "SERIF" -> FontFamily.Serif
                                else -> FontFamily.Default
                            }
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 5. CHAT TEXT SIZE ADJUSTMENT ────────────────────────────────────
        Text(
            text = "CHAT TEXT SIZE",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        val sizeOptions = listOf(
            Pair("SMALL", "Small (13sp)"),
            Pair("MEDIUM", "Medium (15sp)"),
            Pair("LARGE", "Large (17sp)"),
            Pair("EXTRA_LARGE", "Extra Large (19sp)")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sizeOptions.forEach { (key, label) ->
                val isSelected = chatFontSizeScale.equals(key, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan else DarkSurfaceVariant)
                        .border(1.dp, if (isSelected) NeonCyan else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { onSelectFontSizeScale(key) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key.take(1) + key.drop(1).lowercase().take(4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) DarkBackground else TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live preview of sample message bubble
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            Column {
                Text("LIVE CHAT BUBBLE PREVIEW", fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(8.dp))
                val sampleSenderBg = remember(senderBubbleColor) {
                    try { Color(android.graphics.Color.parseColor(senderBubbleColor)) } catch (_: Exception) { DarkCard }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(sampleSenderBg)
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Encrypted message preview with chosen font & size.",
                        color = TextPrimary,
                        fontSize = when (chatFontSizeScale.uppercase()) {
                            "SMALL" -> 13.sp
                            "LARGE" -> 17.sp
                            "EXTRA_LARGE" -> 19.sp
                            else -> 15.sp
                        },
                        fontFamily = when (chatFontStyle.uppercase()) {
                            "MONOSPACE" -> FontFamily.Monospace
                            "SANS_SERIF" -> FontFamily.SansSerif
                            "SERIF" -> FontFamily.Serif
                            else -> FontFamily.Default
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. SUB-PAGE: PRIVACY & SECURITY
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsPrivacySecurityContent(
    readReceiptsEnabled: Boolean,
    isDndEnabled: Boolean,
    blockedUsers: Set<String>,
    currentSecurityQuestion: String?,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onUnblockUser: (String) -> Unit,
    getDisplayName: (String) -> String,
    onOpenSecurityQuestionDialog: () -> Unit,
    onSaveDecoy: (String) -> Unit,
    onTriggerEmergencyWipe: () -> Unit
) {
    var decoyPassphrase by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── 1. READ RECEIPTS TOGGLE ─────────────────────────────────────────
        Text(
            text = "READ RECEIPTS",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = if (readReceiptsEnabled) NeonCyan else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Read Receipts (Blue Checks)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (readReceiptsEnabled) "Enabled: double blue checks sent & received" else "Disabled: private mode (checks stay neutral gray)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = readReceiptsEnabled,
                    onCheckedChange = onToggleReadReceipts,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 2. DO NOT DISTURB (DND) MODE ─────────────────────────────
        Text(
            text = "DO NOT DISTURB (DND)",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDndEnabled) DuressCrimson.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (isDndEnabled) DuressCrimson else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Do Not Disturb (Silence All)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isDndEnabled) "Active: all message & call alerts silenced" else "Off: regular alerts and ringtones enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isDndEnabled,
                    onCheckedChange = onToggleDnd,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DuressCrimson,
                        checkedTrackColor = DuressCrimson.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. BLOCKED CONTACTS (COUNT + UNBLOCK) ──────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "BLOCKED CONTACTS (${blockedUsers.size})",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                fontFamily = FontFamily.Monospace
            )
            if (blockedUsers.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DuressCrimson.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${blockedUsers.size} BLOCKED",
                        color = DuressCrimson,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            if (blockedUsers.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "No blocked contacts. Users you block in conversations will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    blockedUsers.forEach { rawUser ->
                        val clean = rawUser.removePrefix("Peer ").removePrefix("Peer-").removePrefix("PEER: ").trim()
                        val formattedName = getDisplayName(clean)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(DarkCard)
                                        .border(1.dp, DuressCrimson.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = DuressCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = formattedName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@$clean",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Button(
                                onClick = { onUnblockUser(clean) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "UNBLOCK",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. ACCOUNT RECOVERY (SECURITY QUESTION) ─────────────────────────
        Text(
            text = "ACCOUNT RECOVERY QUESTION",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Security Recovery Question",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!currentSecurityQuestion.isNullOrBlank()) {
                    Text(
                        text = "CURRENT RECOVERY QUESTION:",
                        fontSize = 10.sp,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$currentSecurityQuestion\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This introspective question is used to authenticate password resets if you forget your master password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "No security question configured yet. If you forget your password, you will not be able to recover your account without setting a security question.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenSecurityQuestionDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (currentSecurityQuestion.isNullOrBlank()) "ADD SECURITY QUESTION" else "CHANGE SECURITY QUESTION",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. DECOY DURESS PASSPHRASE ──────────────────────────────────────
        Text(
            text = "DURESS STEALTH PASSPHRASE",
            style = MaterialTheme.typography.labelSmall,
            color = DuressCrimson,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, DuressCrimson.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = DuressCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Decoy Duress Trigger",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "If forced to unlock Phantom Mesh under duress, entering this decoy passphrase at login will instantly and stealthily obliterate all real chats, contacts, and keys, and log into an empty dummy state without warning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = decoyPassphrase,
                    onValueChange = { decoyPassphrase = it },
                    label = { Text("Set Decoy Passphrase (min 8 chars)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DuressCrimson,
                        unfocusedBorderColor = DuressCrimson.copy(alpha = 0.4f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = DuressCrimson,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onSaveDecoy(decoyPassphrase)
                        decoyPassphrase = ""
                    },
                    enabled = decoyPassphrase.length >= 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DuressCrimson.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, DuressCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "SAVE DECOY PASSPHRASE",
                        color = DuressCrimson,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. PANIC TRIGGER BOX ────────────────────────────────────────────
        Text(
            text = "PANIC TRIGGER (MANUAL WIPE)",
            style = MaterialTheme.typography.labelSmall,
            color = DuressCrimson,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DuressCrimson.copy(alpha = 0.12f))
                .border(1.5.dp, DuressCrimson, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = DuressCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency Nuclear Wipe",
                        style = MaterialTheme.typography.titleMedium,
                        color = DuressCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Immediately destroys all local encryption keys, conversations, and contact nicknames stored on this device. Use this in critical emergencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onTriggerEmergencyWipe,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DuressCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DESTROY ALL LOCAL DATA NOW",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. SUB-PAGE: DISPLAY & REFRESH RATE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsDisplayPerformanceContent(
    context: Context,
    preferredRefreshRate: Int,
    onSelectRefreshRate: (Int) -> Unit
) {
    val supportedRates = remember(context) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display ?: windowManager?.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            }
            val rates = display?.supportedModes?.map { it.refreshRate.toInt() }?.distinct()?.sorted() ?: emptyList()
            rates
        } catch (_: Exception) {
            emptyList()
        }
    }

    val options = listOf(
        Triple(0, "Adaptive / Device Max (Recommended)", "Dynamically uses your phone's highest refresh rate (up to 120Hz/144Hz) for maximum fluid scrolling."),
        Triple(120, "Ultra Smooth 120Hz", "Forces 120 FPS high refresh rate for buttery-smooth animations and chat scrolling."),
        Triple(90, "Smooth 90Hz", "Balanced high refresh rate providing fluid motion while saving battery."),
        Triple(60, "Standard 60Hz", "Standard refresh rate to maximize battery endurance.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Configure high refresh rate display modes (60Hz, 90Hz, 120Hz) for ultra-smooth scrolling across conversations and messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (supportedRates.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "HARDWARE DETECTED MODES: ${supportedRates.joinToString("Hz, ")}Hz",
                    fontSize = 10.sp,
                    color = NeonGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { (rate, title, description) ->
                val isSelected = preferredRefreshRate == rate
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.12f) else DarkCard)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectRefreshRate(rate) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) NeonCyan else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. SUB-PAGE: CHANGE PASSWORD AND USERNAME
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsAccountCredentialsContent(
    currentUsername: String,
    isChangingUsername: Boolean,
    onChangeUsername: (String) -> Unit,
    onChangePassword: (old: String, new: String, confirm: String) -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    var newUsernameInput by remember { mutableStateOf("") }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── SECTION 1: CHANGE USERNAME ──────────────────────────────────────
        Text(
            text = "CHANGE USERNAME",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Handle",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Current username: @$currentUsername",
                    color = NeonGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newUsernameInput,
                    onValueChange = { newUsernameInput = it },
                    label = { Text("New Username (min 3 chars)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onChangeUsername(newUsernameInput)
                        newUsernameInput = ""
                    },
                    enabled = !isChangingUsername && newUsernameInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isChangingUsername) {
                        CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "UPDATE USERNAME",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── SECTION 2: CHANGE PASSWORD ──────────────────────────────────────
        Text(
            text = "CHANGE PASSWORD",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Login Password",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Update your credentials. Your new password will be hashed and updated both locally and on your cloud profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password (min 8 chars)") },
                    singleLine = true,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onChangePassword(currentPassword, newPassword, confirmPassword)
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    },
                    enabled = currentPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "UPDATE PASSWORD",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onForgotPasswordClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Forgot current password? Reset via Security Question",
                        color = WarningAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOG: ADD / CHANGE SECURITY QUESTION
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SecurityQuestionSettingsDialog(
    currentQuestion: String?,
    onDismiss: () -> Unit,
    onSave: (question: String, answer: String) -> Unit
) {
    var selectedQuestion by remember {
        mutableStateOf(currentQuestion ?: SecurityQuestions.QUESTIONS.first())
    }
    var showPickerList by remember { mutableStateOf(false) }
    var secretAnswer by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentQuestion.isNullOrBlank()) "ADD QUESTION" else "CHANGE QUESTION",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select a question only you know the answer to, then provide your secret answer:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selected Question Box (Tap to toggle list)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground)
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { showPickerList = !showPickerList }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "SELECTED QUESTION (TAP TO CHANGE):",
                            fontSize = 9.sp,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedQuestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }

                if (showPickerList) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        LazyColumn {
                            items(SecurityQuestions.QUESTIONS) { q ->
                                val isSelected = q == selectedQuestion
                                Text(
                                    text = q,
                                    fontSize = 12.sp,
                                    color = if (isSelected) NeonCyan else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable {
                                            selectedQuestion = q
                                            showPickerList = false
                                        }
                                        .padding(8.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = secretAnswer,
                    onValueChange = { secretAnswer = it },
                    label = { Text("Your Secret Answer") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        onSave(selectedQuestion, secretAnswer.trim())
                        onDismiss()
                    },
                    enabled = secretAnswer.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "SAVE SECURITY QUESTION",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOG: FORGOT PASSWORD RECOVERY
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ForgotPasswordSettingsDialog(
    securityQuestion: String?,
    onDismiss: () -> Unit,
    onOpenSetupQuestion: () -> Unit,
    onResetPassword: (answer: String, newPass: String, confirmPass: String, onDone: (Boolean) -> Unit) -> Unit
) {
    var answer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET PASSWORD",
                            style = MaterialTheme.typography.titleMedium,
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (securityQuestion.isNullOrBlank()) {
                    Text(
                        text = "No security recovery question is set up on your account. To use the recovery feature, please configure a security question in Privacy & Security first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenSetupQuestion()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "SET UP SECURITY QUESTION",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground)
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "SECURITY QUESTION:",
                                fontSize = 9.sp,
                                color = NeonCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = securityQuestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Your Secret Answer") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (min 8 chars)") },
                        singleLine = true,
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = NeonCyan.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            isSubmitting = true
                            onResetPassword(answer.trim(), newPassword, confirmPassword) { success ->
                                isSubmitting = false
                                if (success) onDismiss()
                            }
                        },
                        enabled = !isSubmitting && answer.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "RESET PASSWORD NOW",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
