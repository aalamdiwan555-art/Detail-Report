package com.ultra.autodetector.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultra.autodetector.data.Account
import com.ultra.autodetector.data.AccountStatus
import com.ultra.autodetector.data.AppState
import com.ultra.autodetector.data.PermissionState
import com.ultra.autodetector.service.DetectionService
import com.ultra.autodetector.service.FloatingWidgetService
import com.ultra.autodetector.util.TelegramHelper

private val Ink = Color(0xFF08121B)
private val SurfaceBlue = Color(0xFF0F202D)
private val Teal = Color(0xFF56D6C8)
private val Muted = Color(0xFF8EA5B5)
private val Warning = Color(0xFFFFB454)
private val Danger = Color(0xFFFF7171)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var mediaProjectionResultCode: Int = Activity.RESULT_CANCELED
    private var mediaProjectionData: Intent? = null

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                mediaProjectionResultCode = result.resultCode
                mediaProjectionData = result.data
                updatePermissions()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updatePermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltraAutoDetectorTheme { UltraApp(viewModel, this) } }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        viewModel.refresh()
    }

    private fun updatePermissions() {
        val accessibility = isAccessibilityEnabled()
        val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val capture = mediaProjectionData != null
        viewModel.setPermissions(PermissionState(accessibility, overlay, capture))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.isEnabled && Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(packageName) == true
    }

    fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    fun startDetection() {
        val data = mediaProjectionData ?: return requestCapture()
        val intent = Intent(this, DetectionService::class.java).apply {
            action = DetectionService.ACTION_START
            putExtra(DetectionService.EXTRA_RESULT_CODE, mediaProjectionResultCode)
            putExtra(DetectionService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        ContextCompat.startForegroundService(this, Intent(this, FloatingWidgetService::class.java))
        viewModel.setDetector(true)
    }

    fun stopDetection() {
        sendBroadcast(Intent(DetectionService.ACTION_STOP).setPackage(packageName))
        sendBroadcast(Intent(FloatingWidgetService.ACTION_HIDE).setPackage(packageName))
        viewModel.setDetector(false)
    }

    fun pauseDetection(paused: Boolean) {
        sendBroadcast(Intent(DetectionService.ACTION_PAUSE).setPackage(packageName).putExtra("paused", paused))
        viewModel.setDetector(true, paused)
    }

    fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openOverlaySettings() = startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
    )

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun UltraApp(viewModel: MainViewModel, activity: MainActivity) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdmin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        when {
            state.account == null -> AuthScreen(
                onLogin = viewModel::login,
                onRegister = viewModel::register,
                onResetPassword = viewModel::sendPasswordReset,
                message = state.message,
            )
            showAdmin && state.account?.isAdmin == true -> AdminScreen(
                state = state,
                onBack = { showAdmin = false },
                onGrant = viewModel::grantLicense,
                onReject = viewModel::rejectUser,
                onUpload = viewModel::uploadTemplate,
                onDeleteTemplate = viewModel::deleteTemplate,
                onRefresh = viewModel::refreshAdminData,
            )
            showSettings -> SettingsScreen(
                onBack = { showSettings = false },
                onAccessibility = activity::openAccessibilitySettings,
                onOverlay = activity::openOverlaySettings,
                onNotifications = activity::requestNotifications,
            )
            else -> DashboardScreen(
                state = state,
                onAdmin = { showAdmin = true },
                onSettings = { showSettings = true },
                onLogout = viewModel::logout,
                onCapture = activity::requestCapture,
                onStart = activity::startDetection,
                onStop = activity::stopDetection,
                onPause = activity::pauseDetection,
                onTelegram = { state.account?.let { TelegramHelper.openRenewal(activity, it) } },
            )
        }
    }
}

@Composable
private fun AuthScreen(
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    message: String?,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Ink).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
            Text("ULTRA", color = Teal, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text("AutoDetector", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                "A user-controlled screen detection workspace.",
                color = Muted,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (register) onRegister(email, password) else onLogin(email, password) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink),
            ) {
                Text(if (register) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    if (register) "Already have an account? Sign in" else "New here? Create an account",
                    color = Teal,
                )
            }
            TextButton(
                onClick = { onResetPassword(email) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Forgot password?", color = Muted, fontSize = 13.sp)
            }
            message?.let { Text(it, color = Teal, fontSize = 13.sp) }
            Text(
                "Local demo mode is available until Firebase is configured.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    state: AppState,
    onAdmin: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onCapture: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: (Boolean) -> Unit,
    onTelegram: () -> Unit,
) {
    val account = state.account ?: return
    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Control center", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    if (account.isAdmin) IconButton(onClick = onAdmin) {
                        Icon(Icons.Default.AdminPanelSettings, "Admin panel", tint = Teal)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Log out", tint = Muted) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Greeting(account) }
            item { SubscriptionCard(account, onTelegram) }
            item { PermissionCard(state.permissionState, onCapture) }
            item {
                DetectorCard(
                    state = state,
                    onStart = onStart,
                    onStop = onStop,
                    onPause = onPause,
                )
            }
            item { TemplatesCard(state) }
        }
    }
}

@Composable
private fun Greeting(account: Account) {
    Column {
        Text("Good to see you", color = Muted, fontSize = 14.sp)
        Text(
            if (account.isAdmin) "Administrator workspace" else "Detection workspace",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(account.email, color = Teal, fontSize = 13.sp)
    }
}

@Composable
private fun SubscriptionCard(account: Account, onTelegram: () -> Unit) {
    val active = account.hasActiveLicense()
    val tone = if (active) Teal else Warning
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceBlue), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = tone, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("LICENSE STATUS", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
            }
            Text(
                when {
                    account.isAdmin -> "Administrator access"
                    account.status == AccountStatus.PENDING -> "Pending approval"
                    account.status == AccountStatus.REJECTED -> "Access rejected"
                    active -> "Active subscription"
                    else -> "Subscription expired"
                },
                color = tone,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(account.remainingLabel(), color = Color.White, fontSize = 14.sp)
            if (!active && !account.isAdmin) {
                Button(
                    onClick = onTelegram,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink),
                ) { Text("Request renewal") }
            }
        }
    }
}

@Composable
private fun PermissionCard(state: PermissionState, onCapture: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceBlue), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DEVICE ACCESS", color = Color.White, fontWeight = FontWeight.Bold)
            PermissionRow("Accessibility gestures", state.accessibility)
            PermissionRow("Draw over other apps", state.overlay)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Terminal, null, tint = Muted)
                Spacer(Modifier.width(12.dp))
                Text("Screen capture consent", color = Color.White, modifier = Modifier.weight(1f))
                if (state.screenCapture) Icon(Icons.Default.Check, "Granted", tint = Teal)
                else TextButton(onClick = onCapture) { Text("Grant", color = Teal) }
            }
            Text(
                "These permissions are used only after you start detection. You can revoke them in Android settings.",
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(if (granted) Icons.Default.Check else Icons.Default.Lock, null, tint = if (granted) Teal else Warning)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Text(if (granted) "Ready" else "Required", color = if (granted) Teal else Warning, fontSize = 12.sp)
    }
}

@Composable
private fun DetectorCard(
    state: AppState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: (Boolean) -> Unit,
) {
    val allowed = state.account?.hasActiveLicense() == true && state.permissionState.allGranted
    Card(colors = CardDefaults.cardColors(containerColor = if (state.isDetectorRunning) Color(0xFF11352F) else SurfaceBlue), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(if (state.isDetectorRunning) Teal else Muted, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(if (state.isDetectorRunning) if (state.isDetectorPaused) "Detection paused" else "Detection running" else "Detector ready", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                if (!allowed && !state.isDetectorRunning) "Activate your license and grant all device access to begin."
                else "Templates are evaluated locally on the captured frame. Nothing starts until you choose Start.",
                color = Muted,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (!state.isDetectorRunning) {
                    Button(
                        onClick = onStart,
                        enabled = allowed,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (allowed) "Start detection" else "Locked")
                    }
                } else {
                    OutlinedButton(onClick = { onPause(!state.isDetectorPaused) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(if (state.isDetectorPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isDetectorPaused) "Resume" else "Pause")
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Ink),
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatesCard(state: AppState) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceBlue), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ACTIVE TEMPLATES", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.templates.size}", color = Teal, fontWeight = FontWeight.Bold)
            }
            state.templates.take(3).forEach { template ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = Teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, color = Color.White)
                        Text(template.description, color = Muted, fontSize = 12.sp)
                    }
                    Text("%.0f%%".format(template.confidenceThreshold * 100), color = Teal, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
) {
    Scaffold(
        containerColor = Ink,
        topBar = { TopAppBar(title = { Text("Settings", color = Color.White) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Teal) } }) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Permissions", color = Teal, fontWeight = FontWeight.Bold)
            SettingButton("Open accessibility settings", onAccessibility)
            SettingButton("Open overlay settings", onOverlay)
            SettingButton("Allow notifications", onNotifications)
            Divider(color = Color(0x2233FFFFFF))
            Text("Privacy", color = Teal, fontWeight = FontWeight.Bold)
            Text(
                "Ultra AutoDetector captures screen frames only while you explicitly run detection. The accessibility service is used for user-requested gestures. Stop detection at any time from this screen or the floating control.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun SettingButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Icon(Icons.Default.Settings, null)
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowForward, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminScreen(
    state: AppState,
    onBack: () -> Unit,
    onGrant: (Account, Int?) -> Unit,
    onReject: (Account) -> Unit,
    onUpload: (String, String, Uri?) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var templateName by remember { mutableStateOf("") }
    var templateDescription by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onUpload(templateName, templateDescription, uri)
        templateName = ""
        templateDescription = ""
    }
    LaunchedEffect(Unit) { onRefresh() }
    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Admin panel", color = Color.White) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Teal) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("License management", color = Teal, fontWeight = FontWeight.Bold)
                Text("Approve or reject user accounts. In production, these operations are enforced by Firebase rules and trusted authorization.", color = Muted, fontSize = 13.sp)
            }
            item {
                if (state.adminUsers.isEmpty()) {
                    Text("No user accounts are waiting for action.", color = Muted, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.adminUsers.forEach { account ->
                            AdminUserCard(
                                account = account,
                                onGrant = onGrant,
                                onReject = onReject,
                            )
                        }
                    }
                }
            }
            item {
                Text("Template cloud", color = Teal, fontWeight = FontWeight.Bold)
                OutlinedTextField(templateName, { templateName = it }, label = { Text("Template name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(templateDescription, { templateDescription = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { picker.launch("image/*") },
                    enabled = templateName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload image template")
                }
            }
            items(state.templates, key = { it.id }) { template ->
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = SurfaceBlue)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.UploadFile, null, tint = Teal)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(template.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(template.description, color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onDeleteTemplate(template.id) }) { Text("Delete", color = Danger) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUserCard(account: Account, onGrant: (Account, Int?) -> Unit, onReject: (Account) -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = SurfaceBlue)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(account.email, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Current: ${account.status} · ${account.remainingLabel()}", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { days ->
                    OutlinedButton(onClick = { onGrant(account, days) }) { Text("${days}d", color = Teal) }
                }
                OutlinedButton(onClick = { onGrant(account, null) }) { Text("Life", color = Teal) }
            }
            TextButton(onClick = { onReject(account) }) { Text("Reject account", color = Danger) }
        }
    }
}

@Composable
private fun UltraAutoDetectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Teal,
            onPrimary = Ink,
            background = Ink,
            surface = SurfaceBlue,
            onSurface = Color.White,
            outline = Color(0xFF456172),
        ),
        content = content,
    )
}