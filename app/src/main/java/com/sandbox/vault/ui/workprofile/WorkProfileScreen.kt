package com.sandbox.vault.ui.workprofile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sandbox.vault.core.workprofile.SandboxAppInfo
import com.sandbox.vault.ui.theme.DangerRed
import com.sandbox.vault.ui.theme.SafeGreen

private const val CHROME_PACKAGE = "com.android.chrome"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkProfileScreen(
    viewModel: WorkProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showWipeDialog by remember { mutableStateOf(false) }

    val provisionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.completeInstallFlow() }
    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importPickedApk)
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VirtualAndro", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isProfileOwner) {
                val chrome = state.sandboxApps.firstOrNull { it.packageName == CHROME_PACKAGE }
                val otherApps = state.sandboxApps.filterNot { it.packageName == CHROME_PACKAGE }

                item { ManagedProfileHeader() }
                item {
                    ChromeCard(
                        chrome = chrome,
                        onOpenChrome = {
                            viewModel.launchIntentForApp(CHROME_PACKAGE)?.let(context::startActivity)
                        }
                    )
                }
                item {
                    OutsideApkInstallerCard(
                        state = state,
                        onPickApk = {
                            apkPicker.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onInstall = {
                            viewModel.installIntentForSelectedApk()?.let(installLauncher::launch)
                        },
                        onCancel = { viewModel.clearSelectedApk() }
                    )
                }
                item {
                    SandboxAppsCard(
                        apps = otherApps,
                        onOpenApp = { packageName ->
                            viewModel.launchIntentForApp(packageName)?.let(context::startActivity)
                        }
                    )
                }
                item {
                    DangerZone(onWipeProfile = { showWipeDialog = true })
                }
            } else {
                item {
                    PersonalProfileSetup(
                        isDeviceAdminActive = state.isDeviceAdminActive,
                        onCreateProfile = {
                            provisionLauncher.launch(viewModel.provisioningIntent())
                        }
                    )
                }
            }

            state.error?.let { message ->
                item { ErrorCard(message) }
            }
        }
    }

    if (showWipeDialog && activity != null) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = DangerRed) },
            title = { Text("Delete VirtualAndro sandbox?") },
            text = {
                Text("This deletes the work profile, including all sandbox apps, app data, accounts, and cached files.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        viewModel.wipeManagedProfile(activity)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                ) {
                    Text("Delete sandbox")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PersonalProfileSetup(
    isDeviceAdminActive: Boolean,
    onCreateProfile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusHeader(
            icon = Icons.Outlined.Shield,
            title = "Personal space",
            subtitle = "Create the VirtualAndro work profile first."
        )
        SecurityModelCard()
        Button(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Create VirtualAndro sandbox")
        }
        if (isDeviceAdminActive) {
            Text(
                "Open the work-profile copy after provisioning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ManagedProfileHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusHeader(
            icon = Icons.Outlined.Security,
            title = "VirtualAndro sandbox",
            subtitle = "Apps shown here run inside the Android work profile."
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip("Internet on", Icons.Outlined.Wifi, SafeGreen)
            StatusChip("Play Store off", Icons.Outlined.Storefront, DangerRed)
            StatusChip("Chrome first", Icons.Outlined.Language, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SecurityModelCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Work-profile isolation", fontWeight = FontWeight.Bold)
            Text(
                "Deleting the work profile removes sandbox apps, app data, accounts, and this app's profile-local cache.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ChromeCard(
    chrome: SandboxAppInfo?,
    onOpenChrome: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(Icons.Outlined.Language, MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("Chrome", fontWeight = FontWeight.Bold)
                Text(
                    if (chrome != null) "Ready in the sandbox" else "Chrome is not available as a work-profile system app on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Button(
                onClick = onOpenChrome,
                enabled = chrome != null
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Open")
            }
        }
    }
}

@Composable
private fun OutsideApkInstallerCard(
    state: WorkProfileUiState,
    onPickApk: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text("Outside APK installer", fontWeight = FontWeight.Bold)
            }
            Text(
                "Pick an APK from personal storage. VirtualAndro copies it into work-profile cache and opens the sandbox installer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (state.isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Preparing APK")
                }
            }
            state.scanResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(result.appName, fontWeight = FontWeight.Bold)
                        Text(result.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onPickApk, modifier = Modifier.weight(1f)) {
                    Text("Pick APK")
                }
                if (state.scanResult != null) {
                    Button(onClick = onInstall, modifier = Modifier.weight(1f)) {
                        Text("Install")
                    }
                }
            }
            if (state.scanResult != null) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SandboxAppsCard(
    apps: List<SandboxAppInfo>,
    onOpenApp: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text("Installed sandbox apps", fontWeight = FontWeight.Bold)
            }
            if (apps.isEmpty()) {
                Text(
                    "Apps installed from Chrome inside the work profile will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                apps.forEach { app ->
                    AppRow(app = app, onOpenApp = onOpenApp)
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: SandboxAppInfo,
    onOpenApp: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(Icons.Outlined.Apps, MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(onClick = { onOpenApp(app.packageName) }) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Open")
        }
    }
}

@Composable
private fun AppIcon(icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.size(44.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color)
        }
    }
}

@Composable
private fun DangerZone(onWipeProfile: () -> Unit) {
    OutlinedButton(
        onClick = onWipeProfile,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
    ) {
        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Delete sandbox profile")
    }
}

@Composable
private fun StatusHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.size(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, icon: ImageVector, color: Color) {
    AssistChip(
        onClick = {},
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
        label = { Text(label) }
    )
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = DangerRed,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
