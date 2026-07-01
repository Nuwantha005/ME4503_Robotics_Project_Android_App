package com.example.ui.screens

import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionStatus
import com.example.network.ControlScheme
import com.example.network.RobotMode
import com.example.network.Waypoint
import com.example.network.TelemetryMessage
import androidx.compose.ui.geometry.Size
import com.example.ui.theme.*
import com.example.ui.viewmodel.RobotViewModel
import kotlinx.coroutines.launch
import kotlin.math.*

enum class AppTab(val title: String, val icon: ImageVector) {
    CONTROL("Control", Icons.Default.Gamepad),
    METERS("Meters", Icons.Default.Speed),
    MAPPING("Mapping", Icons.Default.Map),
    TELEMETRY("Telemetry", Icons.Default.Analytics),
    CONFIG("Config", Icons.Default.Settings)
}

enum class TestScreen(val title: String, val icon: ImageVector) {
    LED_WIFI("WiFi & LED Test", Icons.Default.Wifi),
    MOTOR_PID("Motor & PID Tuning", Icons.Default.SettingsInputComponent),
    COMM_LOG("Raw Comm Logger", Icons.Default.Terminal)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmrDashboard(viewModel: RobotViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(AppTab.CONTROL) }
    var activeTestScreen by remember { mutableStateOf<TestScreen?>(null) }

    val connection by viewModel.connectionStatus.collectAsState()
    val packetsRate by viewModel.packetsReceivedPerSec.collectAsState()
    val telemetryState by viewModel.telemetry.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                drawerContentColor = OnSurfaceWhite
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "WMR TEST HARNESS",
                    style = MaterialTheme.typography.titleMedium,
                    color = VioletPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = "Hardware Bring-Up Modules",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                HorizontalDivider(color = DarkSurfaceElevated, modifier = Modifier.padding(vertical = 16.dp))

                // Standard Telemetry Dashboard selection
                NavigationDrawerItem(
                    label = { Text("Main Operations Dashboard") },
                    selected = activeTestScreen == null,
                    onClick = {
                        activeTestScreen = null
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = VioletSecondary,
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor = OnSurfaceWhite,
                        unselectedIconColor = OnSurfaceMuted,
                        selectedTextColor = OnSurfaceWhite,
                        unselectedTextColor = OnSurfaceMuted
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Isolated Test Modules",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                TestScreen.values().forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.title) },
                        selected = activeTestScreen == screen,
                        onClick = {
                            activeTestScreen = screen
                            // Manage accelerometer state based on testing
                            viewModel.unregisterAccelerometerListener()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = VioletSecondary,
                            unselectedContainerColor = Color.Transparent,
                            selectedIconColor = OnSurfaceWhite,
                            unselectedIconColor = OnSurfaceMuted,
                            selectedTextColor = OnSurfaceWhite,
                            unselectedTextColor = OnSurfaceMuted
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Persistent Top Bar common to all views
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "WMR CONTROLLER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connection) {
                                                ConnectionStatus.CONNECTED -> AccentGreen
                                                ConnectionStatus.CONNECTING -> AccentOrange
                                                ConnectionStatus.DISCONNECTED -> AccentRed
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (connection) {
                                        ConnectionStatus.CONNECTED -> "CONNECTED • ${packetsRate}Hz"
                                        ConnectionStatus.CONNECTING -> "CONNECTING"
                                        ConnectionStatus.DISCONNECTED -> "DISCONNECTED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (connection) {
                                        ConnectionStatus.CONNECTED -> AccentGreen
                                        ConnectionStatus.CONNECTING -> AccentOrange
                                        ConnectionStatus.DISCONNECTED -> AccentRed
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("hamburger_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = OnSurfaceWhite)
                        }
                    },
                    actions = {
                        // Permanent Power Switch and clickable Mode / Obstacle indicators
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // Mode Toggler
                            Card(
                                onClick = {
                                    viewModel.activeMode = if (viewModel.activeMode == RobotMode.MANUAL) RobotMode.LINE_FOLLOW else RobotMode.MANUAL
                                    viewModel.logToConsole("TopBar: Active mode changed to ${viewModel.activeMode.name}")
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (viewModel.activeMode == RobotMode.MANUAL) DarkSurfaceElevated else VioletSecondary
                                ),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .height(34.dp)
                                    .testTag("mode_toggle_indicator")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp)) {
                                    Text(
                                        text = if (viewModel.activeMode == RobotMode.MANUAL) "MANUAL" else "LINE FOLL",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = OnSurfaceWhite
                                    )
                                }
                            }

                            // Obstacle Toggle
                            Card(
                                onClick = {
                                    viewModel.obstacleEnabled = !viewModel.obstacleEnabled
                                    viewModel.logToConsole("TopBar: Obstacle Avoidance is now ${if (viewModel.obstacleEnabled) "ENABLED" else "DISABLED"}")
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (viewModel.obstacleEnabled) AccentOrange.copy(alpha = 0.25f) else DarkSurfaceElevated
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .height(34.dp)
                                    .testTag("obstacle_toggle_indicator")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (viewModel.obstacleEnabled) AccentOrange else OnSurfaceMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Power Switch
                            IconButton(
                                onClick = {
                                    viewModel.isPowerOn = !viewModel.isPowerOn
                                    if (!viewModel.isPowerOn) {
                                        viewModel.cmdX = 0f
                                        viewModel.cmdY = 0f
                                        viewModel.cmdRot = 0f
                                    }
                                    viewModel.logToConsole("TopBar: Power Switch set to ${if (viewModel.isPowerOn) "ON" else "OFF"}")
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("power_toggle_button")
                                    .background(
                                        if (viewModel.isPowerOn) VioletPrimary.copy(alpha = 0.25f) else AccentRed.copy(alpha = 0.25f),
                                        CircleShape
                                    )
                                    .border(1.dp, if (viewModel.isPowerOn) VioletPrimary else AccentRed, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.PowerSettingsNew,
                                    contentDescription = "Power",
                                    tint = if (viewModel.isPowerOn) AccentGreen else AccentRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DarkSurface,
                        titleContentColor = OnSurfaceWhite
                    )
                )
            },
            bottomBar = {
                if (activeTestScreen == null) {
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        AppTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = {
                                    currentTab = tab
                                    if (tab == AppTab.CONTROL && viewModel.activeScheme == ControlScheme.TILT) {
                                        viewModel.registerAccelerometerListener()
                                    } else {
                                        viewModel.unregisterAccelerometerListener()
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = OnSurfaceWhite,
                                    selectedTextColor = OnSurfaceWhite,
                                    unselectedIconColor = OnSurfaceMuted,
                                    unselectedTextColor = OnSurfaceMuted,
                                    indicatorColor = VioletPrimary
                                ),
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            containerColor = DarkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (activeTestScreen != null) {
                    // Show Bring-up Test Screens
                    when (activeTestScreen) {
                        TestScreen.LED_WIFI -> LedWifiTestView(viewModel) { activeTestScreen = null }
                        TestScreen.MOTOR_PID -> MotorPidTestView(viewModel) { activeTestScreen = null }
                        TestScreen.COMM_LOG -> RawCommLoggerView(viewModel) { activeTestScreen = null }
                        null -> {}
                    }
                } else {
                    // Show standard operational tabs
                    when (currentTab) {
                        AppTab.CONTROL -> ControlTabView(viewModel)
                        AppTab.METERS -> MetersTabView(viewModel, telemetryState)
                        AppTab.MAPPING -> MappingTabView(viewModel, telemetryState)
                        AppTab.TELEMETRY -> TelemetryTabView(viewModel, telemetryState)
                        AppTab.CONFIG -> ConfigTabView(viewModel)
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. CONFIG TAB VIEW
// ==========================================
@Composable
fun ConfigTabView(viewModel: RobotViewModel) {
    var isMicroManaging by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ROBOT CONFIGURATION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VioletPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Micro-Manage", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isMicroManaging,
                        onCheckedChange = { isMicroManaging = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VioletPrimary)
                    )
                }
            }
        }

        // Connection detail card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NETWORK INTERFACE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.robotIp,
                        onValueChange = { viewModel.robotIp = it },
                        label = { Text("Pico W AP IP Address") },
                        leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = VioletPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DarkSurfaceElevated
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.connectToRobot() },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Connect")
                        }
                        Button(
                            onClick = { viewModel.disconnectFromRobot() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Disconnect", color = AccentRed)
                        }
                    }
                }
            }
        }

        if (!isMicroManaging) {
            // SURFACE LEVEL
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("SURFACE PARAMETERS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)

                        // Max Speed
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Max Speed limit", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format("%.2f", viewModel.maxSpeedMps)} m/s", color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.maxSpeedMps,
                                onValueChange = { viewModel.maxSpeedMps = it },
                                valueRange = 0.1f..2.5f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        // Max Accel
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Max Acceleration limit", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format("%.2f", viewModel.maxAccelMps2)} m/s²", color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.maxAccelMps2,
                                onValueChange = { viewModel.maxAccelMps2 = it },
                                valueRange = 0.5f..5.0f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        // Obstacle Stop Distance
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Obstacle Stop Distance", style = MaterialTheme.typography.bodyMedium)
                                Text("${viewModel.obstacleStopDistanceMm.toInt()} mm", color = AccentOrange, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.obstacleStopDistanceMm,
                                onValueChange = { viewModel.obstacleStopDistanceMm = it },
                                valueRange = 50f..500f,
                                colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange)
                            )
                        }
                    }
                }
            }
        } else {
            // MICRO MANAGING
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("WHEEL PID LOOPS (Shared Gains)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)

                        // Kp
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Kp (Proportional)", style = MaterialTheme.typography.bodyMedium)
                                Text(String.format("%.2f", viewModel.pidKp), color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.pidKp,
                                onValueChange = { viewModel.pidKp = it },
                                valueRange = 0.0f..5.0f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        // Ki
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Ki (Integral)", style = MaterialTheme.typography.bodyMedium)
                                Text(String.format("%.3f", viewModel.pidKi), color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.pidKi,
                                onValueChange = { viewModel.pidKi = it },
                                valueRange = 0.0f..0.5f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        // Kd
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Kd (Derivative)", style = MaterialTheme.typography.bodyMedium)
                                Text(String.format("%.3f", viewModel.pidKd), color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.pidKd,
                                onValueChange = { viewModel.pidKd = it },
                                valueRange = 0.0f..0.1f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("ROBOT PHYSICS & KINEMATICS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)

                        // Wheel Radius
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Wheel Radius", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format("%.1f", viewModel.wheelRadiusM * 1000)} mm", color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.wheelRadiusM,
                                onValueChange = { viewModel.wheelRadiusM = it },
                                valueRange = 0.02f..0.10f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        // Wheelbase LX / LY
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Track Width Half (lx)", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format("%.1f", viewModel.wheelbaseLxM * 1000)} mm", color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.wheelbaseLxM,
                                onValueChange = { viewModel.wheelbaseLxM = it },
                                valueRange = 0.03f..0.15f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Wheelbase Length Half (ly)", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format("%.1f", viewModel.wheelbaseLyM * 1000)} mm", color = VioletPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.wheelbaseLyM,
                                onValueChange = { viewModel.wheelbaseLyM = it },
                                valueRange = 0.03f..0.15f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary, activeTrackColor = VioletPrimary)
                            )
                        }

                        // Encoder Counts & Gear Ratio inputs
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = viewModel.encoderCountsPerRev.toString(),
                                onValueChange = { viewModel.encoderCountsPerRev = it.toFloatOrNull() ?: 360f },
                                label = { Text("Encoder Counts") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VioletPrimary)
                            )
                            OutlinedTextField(
                                value = viewModel.motorGearRatio.toString(),
                                onValueChange = { viewModel.motorGearRatio = it.toFloatOrNull() ?: 1.0f },
                                label = { Text("Gear Ratio") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VioletPrimary)
                            )
                        }
                    }
                }
            }
        }

        // Send parameters button
        item {
            Button(
                onClick = { viewModel.sendConfigToRobot() },
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("apply_config_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("APPLY AND SEND PARAMETERS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ==========================================
// 2. CONTROL TAB VIEW
// ==========================================
@Composable
fun ControlTabView(viewModel: RobotViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode scheme selector tabs (Top pills)
        ScrollableTabRow(
            selectedTabIndex = viewModel.activeScheme.ordinal,
            containerColor = Color.Transparent,
            contentColor = VioletPrimary,
            edgePadding = 0.dp,
            indicator = {},
            divider = {}
        ) {
            ControlScheme.values().forEach { scheme ->
                val isSelected = viewModel.activeScheme == scheme
                Card(
                    onClick = {
                        viewModel.activeScheme = scheme
                        if (scheme == ControlScheme.TILT) {
                            viewModel.registerAccelerometerListener()
                        } else {
                            viewModel.unregisterAccelerometerListener()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) VioletPrimary else DarkSurface
                    ),
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .height(38.dp)
                        .testTag("scheme_tab_${scheme.name.lowercase()}")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp)) {
                        Text(
                            text = when (scheme) {
                                ControlScheme.BUTTONS -> "D-PAD"
                                ControlScheme.JOYSTICK -> "JOYSTICK"
                                ControlScheme.TILT -> "TILT DRIVE"
                                ControlScheme.FIELD_CENTRIC -> "FIELD CENTRIC"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!viewModel.isPowerOn) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PowerOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = AccentRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SYSTEM POWER IS SHUT DOWN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tap the power switch in the top bar to enable kinematics loop.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted, textAlign = TextAlign.Center)
                }
            }
        } else if (viewModel.activeMode == RobotMode.LINE_FOLLOW) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(64.dp), tint = VioletPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("LINE FOLLOWING MODE IS ACTIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Autonomous QTR-8RC loop has control. To drive manually, switch back to MANUAL mode.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted, textAlign = TextAlign.Center)
                }
            }
        } else {
            // Main control interactive boards
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (viewModel.activeScheme) {
                    ControlScheme.BUTTONS -> ClassicButtonsDriveView(viewModel)
                    ControlScheme.JOYSTICK -> JoystickDriveView(viewModel)
                    ControlScheme.TILT -> TiltDriveView(viewModel)
                    ControlScheme.FIELD_CENTRIC -> FieldCentricDriveView(viewModel)
                }
            }
        }
    }
}

// 2.1. Classic D-Pad 8-dir view
@Composable
fun ClassicButtonsDriveView(viewModel: RobotViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxHeight()
    ) {
        // Rotate row on top
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            ControlActionButton(Icons.Default.RotateLeft, "ROT_CCW",
                onPress = { viewModel.cmdRot = -0.5f },
                onRelease = { viewModel.cmdRot = 0f }
            )
            ControlActionButton(Icons.Default.RotateRight, "ROT_CW",
                onPress = { viewModel.cmdRot = 0.5f },
                onRelease = { viewModel.cmdRot = 0f }
            )
        }

        // 3x3 Grid of Directions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlActionButton(Icons.Default.NorthWest, "NW",
                    onPress = { viewModel.cmdX = -0.5f; viewModel.cmdY = 0.5f },
                    onRelease = { viewModel.cmdX = 0f; viewModel.cmdY = 0f }
                )
                ControlActionButton(Icons.Default.North, "N",
                    onPress = { viewModel.cmdY = 0.7f },
                    onRelease = { viewModel.cmdY = 0f }
                )
                ControlActionButton(Icons.Default.NorthEast, "NE",
                    onPress = { viewModel.cmdX = 0.5f; viewModel.cmdY = 0.5f },
                    onRelease = { viewModel.cmdX = 0f; viewModel.cmdY = 0f }
                )
            }
            // Row 2
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlActionButton(Icons.Default.West, "W",
                    onPress = { viewModel.cmdX = -0.7f },
                    onRelease = { viewModel.cmdX = 0f }
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(VioletPrimary))
                }
                ControlActionButton(Icons.Default.East, "E",
                    onPress = { viewModel.cmdX = 0.7f },
                    onRelease = { viewModel.cmdX = 0f }
                )
            }
            // Row 3
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlActionButton(Icons.Default.SouthWest, "SW",
                    onPress = { viewModel.cmdX = -0.5f; viewModel.cmdY = -0.5f },
                    onRelease = { viewModel.cmdX = 0f; viewModel.cmdY = 0f }
                )
                ControlActionButton(Icons.Default.South, "S",
                    onPress = { viewModel.cmdY = -0.7f },
                    onRelease = { viewModel.cmdY = 0f }
                )
                ControlActionButton(Icons.Default.SouthEast, "SE",
                    onPress = { viewModel.cmdX = 0.5f; viewModel.cmdY = -0.5f },
                    onRelease = { viewModel.cmdX = 0f; viewModel.cmdY = 0f }
                )
            }
        }
    }
}

@Composable
fun ControlActionButton(
    icon: ImageVector,
    tag: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(64.dp)
            .testTag("btn_drive_$tag")
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPressed) VioletPrimary else DarkSurface)
            .border(1.dp, if (isPressed) VioletTertiary else DarkSurfaceElevated, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    onPress()
                    waitForUpOrCancellation()
                    isPressed = false
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPressed) OnSurfaceWhite else VioletPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}

// 2.2. Interactive Joystick Canvas
@Composable
fun JoystickDriveView(viewModel: RobotViewModel) {
    val maxRadiusPx = 250f
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("VIRTUAL MECANUM JOYSTICK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletTertiary)
        Text("Drag knob to command strafing and forward/backward velocities", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .testTag("joystick_pad_area")
                .background(DarkSurface, CircleShape)
                .border(2.dp, VioletPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Draw crosshairs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawLine(
                    color = VioletPrimary.copy(alpha = 0.15f),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 2f
                )
                drawLine(
                    color = VioletPrimary.copy(alpha = 0.15f),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 2f
                )
                drawCircle(
                    color = VioletPrimary.copy(alpha = 0.05f),
                    radius = maxRadiusPx / 2,
                    center = center
                )
            }

            // Draggable Knob
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt())
                    }
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(VioletTertiary, VioletSecondary)
                        )
                    )
                    .border(2.dp, OnSurfaceWhite, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                dragOffset = Offset.Zero
                                viewModel.cmdX = 0f
                                viewModel.cmdY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val raw = dragOffset + dragAmount
                                val distance = sqrt(raw.x * raw.x + raw.y * raw.y)

                                dragOffset = if (distance <= maxRadiusPx) {
                                    raw
                                } else {
                                    val angle = atan2(raw.y, raw.x)
                                    Offset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
                                }

                                // Map joystick coordinates back to robot velocities:
                                // dragOffset.x mapped to robot cmdX (sideways strafe): -1f..1f
                                // dragOffset.y mapped to negative robot cmdY (forward is negative y in Android screen coords): -1f..1f
                                viewModel.cmdX = (dragOffset.x / maxRadiusPx)
                                viewModel.cmdY = -(dragOffset.y / maxRadiusPx)
                            }
                        )
                    }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("X (STRAFE)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Text(String.format("%.2f", viewModel.cmdX), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Y (FORWARD)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Text(String.format("%.2f", viewModel.cmdY), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletPrimary)
            }
        }
    }
}

// 2.3. Accelerometer Tilt Drive View
@Composable
fun TiltDriveView(viewModel: RobotViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("ACCELEROMETER TILT DRIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletTertiary)
        Text("Tilt phone left/right to strafe and forward/backward to drive", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)

        Spacer(modifier = Modifier.height(32.dp))

        // Simulated Gyro HUD
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(DarkSurface, CircleShape)
                .border(2.dp, VioletPrimary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(color = VioletPrimary.copy(alpha = 0.1f), radius = 80f, center = center)

                // Draw level bubbles
                val bubbleX = center.x + (viewModel.cmdX * 100f)
                val bubbleY = center.y - (viewModel.cmdY * 100f) // inverted y coordinate

                drawCircle(
                    color = AccentGreen,
                    radius = 16f,
                    center = Offset(bubbleX, bubbleY)
                )

                // Target ring
                drawCircle(
                    color = OnSurfaceWhite.copy(alpha = 0.5f),
                    radius = 20f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("REAL-TIME CALIBRATION", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("STRAFE: ${String.format("%.2f", viewModel.cmdX)}", color = OnSurfaceWhite, fontWeight = FontWeight.Bold)
                    Text("DRIVE: ${String.format("%.2f", viewModel.cmdY)}", color = OnSurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 2.4. Field Centric Drive View (IMU Yaw rotated)
@Composable
fun FieldCentricDriveView(viewModel: RobotViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("FIELD CENTRIC STEERING", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletTertiary)
        Text("Firmware uses internal IMU Yaw to translate joystick inputs", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)

        Spacer(modifier = Modifier.height(24.dp))

        val currentTelemetry = viewModel.telemetry.collectAsState().value
        val robotYawRad = currentTelemetry?.odom?.theta ?: 0f
        val robotYawDeg = (robotYawRad * 180f / Math.PI.toFloat())

        // Compass HUD
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(DarkSurface, CircleShape)
                .border(2.dp, VioletPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2 - 20f

                // Cardinal marks
                drawString(this, "N", center.x, center.y - radius + 10f)
                drawString(this, "S", center.x, center.y + radius - 10f)
                drawString(this, "W", center.x - radius + 10f, center.y)
                drawString(this, "E", center.x + radius - 10f, center.y)

                // Draw rotated compass pointer (Robot heading)
                val headingAngle = -robotYawRad - (PI.toFloat() / 2) // aligned with North
                val pointerLength = radius - 30f
                val endX = center.x + cos(headingAngle) * pointerLength
                val endY = center.y + sin(headingAngle) * pointerLength

                drawLine(
                    color = AccentGreen,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 4f
                )

                // Small arrow cap
                drawCircle(color = AccentGreen, radius = 6f, center = Offset(endX, endY))
            }
            Text(
                text = "${robotYawDeg.roundToInt()}°",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AccentGreen
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Joystick nested specifically in Field Centric to send vectors
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(DarkSurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            var joystickOffset by remember { mutableStateOf(Offset.Zero) }
            val fcLimit = 120f

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = VioletPrimary.copy(alpha = 0.1f), radius = fcLimit)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(joystickOffset.x.toInt(), joystickOffset.y.toInt()) }
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(VioletPrimary)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                joystickOffset = Offset.Zero
                                viewModel.cmdX = 0f
                                viewModel.cmdY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val raw = joystickOffset + dragAmount
                                val dist = sqrt(raw.x * raw.x + raw.y * raw.y)
                                joystickOffset = if (dist <= fcLimit) raw else raw * (fcLimit / dist)

                                viewModel.cmdX = (joystickOffset.x / fcLimit)
                                viewModel.cmdY = -(joystickOffset.y / fcLimit)
                            }
                        )
                    }
            )
        }
    }
}

private fun drawString(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, text: String, x: Float, y: Float) {
    // Basic text drawing helper
}


// ==========================================
// 3. METERS TAB VIEW
// ==========================================
@Composable
fun MetersTabView(viewModel: RobotViewModel, telemetry: TelemetryMessage?) {
    val speedHistory by viewModel.robotSpeedHistory.collectAsState()
    val accelHistory by viewModel.robotAccelHistory.collectAsState()
    val irHistory by viewModel.irHistory.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "REAL-TIME METERS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VioletPrimary
            )
        }

        // Real-time numeric cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ROBOT SPEED", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format("%.2f", speedHistory.lastOrNull() ?: 0f)} m/s",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = VioletPrimary
                        )
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BATTERY VOLT", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        val volt = telemetry?.batteryV ?: 0f
                        Text(
                            text = "${String.format("%.2f", volt)} V",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (volt < 6.8f && volt > 0f) AccentRed else AccentGreen
                        )
                    }
                }
            }
        }

        // QTR-8RC IR Bar graph
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("QTR-8RC INFRARED REFLECTANCE ARRAY", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Text("Decay timings in microseconds (3500 is center)", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    Spacer(modifier = Modifier.height(16.dp))

                    val irData = telemetry?.ir ?: listOf(0, 0, 0, 0, 0, 0, 0, 0)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        irData.forEachIndexed { index, value ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                // Dynamic reflective value bar
                                val normValue = (value / 3000f).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DarkSurfaceElevated),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(normValue)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(VioletTertiary, VioletPrimary)
                                                )
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("IR$index", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted, fontSize = 9.sp)
                                Text("$value", style = MaterialTheme.typography.labelSmall, color = OnSurfaceWhite, fontSize = 8.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Estimated Line Position: ${if (telemetry?.linePosition == -999) "NONE" else telemetry?.linePosition.toString()}",
                        color = if (telemetry?.linePosition == -999) AccentRed else AccentGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Live Speed Graph (Canvas draw)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ROBOT SPEED OVER TIME", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Spacer(modifier = Modifier.height(12.dp))
                    TelemetryLineChart(data = speedHistory, label = "m/s", maxVal = 1.5f)
                }
            }
        }

        // Live Acceleration Graph (Canvas draw)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ROBOT ACCELERATION OVER TIME", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Spacer(modifier = Modifier.height(12.dp))
                    TelemetryLineChart(data = accelHistory, label = "m/s²", maxVal = 4.0f)
                }
            }
        }
    }
}

// Sleek Custom Line Chart using Canvas
@Composable
fun TelemetryLineChart(data: List<Float>, label: String, maxVal: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(DarkSurfaceElevated, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        val width = size.width
        val height = size.height

        if (data.size < 2) return@Canvas

        // Draw grid lines
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = height * (i.toFloat() / gridCount)
            drawLine(
                color = OnSurfaceMuted.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        val points = mutableListOf<Offset>()
        val stepX = width / (data.size - 1)

        data.forEachIndexed { index, valF ->
            val normY = (valF / maxVal).coerceIn(0f, 1f)
            val y = height - (normY * height)
            val x = index * stepX
            points.add(Offset(x, y))
        }

        // Draw filled path under the line
        val fillPath = Path().apply {
            moveTo(0f, height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(width, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(VioletPrimary.copy(alpha = 0.3f), Color.Transparent)
            )
        )

        // Draw main line path
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        drawPath(
            path = linePath,
            color = VioletPrimary,
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}


// ==========================================
// 4. MAPPING TAB VIEW
// ==========================================
@Composable
fun MappingTabView(viewModel: RobotViewModel, telemetry: TelemetryMessage?) {
    val pathHistory by viewModel.robotPath.collectAsState()
    val waypoints by viewModel.plannedWaypoints.collectAsState()
    val scope = rememberCoroutineScope()

    val currentOdom = telemetry?.odom ?: com.example.network.Odometry(0f, 0f, 0f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "MAPPING & PATH PLANNING",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VioletPrimary
            )
        }

        // Live Interactive Canvas map
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LIVE NAVIGATION CANVAS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Text("Tap grid to record waypoint markers. Green dot is robot.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Map grid (Scale: 1m = 100px. Center of 300dp box is (0f,0f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .testTag("mapping_canvas")
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    // Translate screen offset to coordinates relative to center (0m,0m)
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val scale = 100f // 100 pixels per meter

                                    val mappedX = (offset.x - centerX) / scale
                                    val mappedY = -(offset.y - centerY) / scale // invert Y for screen space

                                    // Round to 2 decimal places
                                    val finalX = (round(mappedX * 100f) / 100f)
                                    val finalY = (round(mappedY * 100f) / 100f)
                                    viewModel.addWaypoint(finalX, finalY)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val cx = w / 2
                            val cy = h / 2
                            val scale = 100f // pixels per meter

                            // Draw grid lines
                            val gridStep = 50f // 0.5 meters
                            for (x in (cx % gridStep).toInt()..w.toInt() step gridStep.toInt()) {
                                drawLine(OnSurfaceMuted.copy(alpha = 0.1f), Offset(x.toFloat(), 0f), Offset(x.toFloat(), h), 1f)
                            }
                            for (y in (cy % gridStep).toInt()..h.toInt() step gridStep.toInt()) {
                                drawLine(OnSurfaceMuted.copy(alpha = 0.1f), Offset(0f, y.toFloat()), Offset(w, y.toFloat()), 1f)
                            }

                            // Axes
                            drawLine(VioletSecondary.copy(alpha = 0.4f), Offset(cx, 0f), Offset(cx, h), 2f)
                            drawLine(VioletSecondary.copy(alpha = 0.4f), Offset(0f, cy), Offset(w, cy), 2f)

                            // Draw robot path taken
                            if (pathHistory.size >= 2) {
                                val path = Path().apply {
                                    val first = pathHistory.first()
                                    moveTo(cx + first.first * scale, cy - first.second * scale)
                                    for (i in 1 until pathHistory.size) {
                                        val p = pathHistory[i]
                                        lineTo(cx + p.first * scale, cy - p.second * scale)
                                    }
                                }
                                drawPath(path, VioletPrimary, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }

                            // Draw Planned Waypoints
                            waypoints.forEachIndexed { i, wp ->
                                val wx = cx + wp.x * scale
                                val wy = cy - wp.y * scale
                                drawCircle(VioletTertiary, radius = 6f, center = Offset(wx, wy))
                                // Connect waypoint path lines
                                if (i > 0) {
                                    val prev = waypoints[i - 1]
                                    drawLine(
                                        color = VioletTertiary.copy(alpha = 0.5f),
                                        start = Offset(cx + prev.x * scale, cy - prev.y * scale),
                                        end = Offset(wx, wy),
                                        strokeWidth = 2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                }
                            }

                            // Draw current robot position dot & orientation triangle
                            val rx = cx + currentOdom.x * scale
                            val ry = cy - currentOdom.y * scale
                            val angle = -currentOdom.theta

                            drawCircle(AccentGreen, radius = 8f, center = Offset(rx, ry))

                            // Heading line
                            val headingLen = 22f
                            val hx = rx + cos(angle) * headingLen
                            val hy = ry + sin(angle) * headingLen
                            drawLine(AccentGreen, Offset(rx, ry), Offset(hx, hy), 4f)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons below canvas
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.zeroOdometry() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            modifier = Modifier.weight(1f).testTag("zero_odom_button")
                        ) {
                            Text("Zero Odom", color = OnSurfaceWhite)
                        }
                        Button(
                            onClick = { viewModel.clearWaypoints() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                            modifier = Modifier.weight(1f).testTag("clear_waypoints_button")
                        ) {
                            Text("Clear Paths", color = AccentRed)
                        }
                        Button(
                            onClick = { viewModel.uploadWaypointsToRobot() },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            modifier = Modifier.weight(1.2f).testTag("send_waypoints_button")
                        ) {
                            Text("Send Waypoints")
                        }
                    }
                }
            }
        }

        // Live stats Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = DarkSurface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("ODOM_X", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        Text("${String.format("%.3f", currentOdom.x)} m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletPrimary)
                    }
                    Column {
                        Text("ODOM_Y", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        Text("${String.format("%.3f", currentOdom.y)} m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletPrimary)
                    }
                    Column {
                        Text("ODOM_THETA", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        val angleDeg = (currentOdom.theta * 180f / Math.PI.toFloat())
                        Text("${angleDeg.roundToInt()}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VioletPrimary)
                    }
                }
            }
        }

        // Table of waypoints list
        if (waypoints.isNotEmpty()) {
            item {
                Text("WAYPOINT ROUTE TABLE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
            }
            items(waypoints.size) { index ->
                val wp = waypoints[index]
                Card(colors = CardDefaults.cardColors(containerColor = DarkSurface)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(VioletPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceWhite, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Point: (${wp.x}m, ${wp.y}m)", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(
                            onClick = { viewModel.removeWaypoint(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed)
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// 5. TELEMETRY TAB VIEW
// ==========================================
@Composable
fun TelemetryTabView(viewModel: RobotViewModel, telemetry: TelemetryMessage?) {
    val wheelSpeedsHistory by viewModel.wheelSpeedsHistory.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SENSOR TELEMETRY",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VioletPrimary
            )
        }

        // ToF sonar distance Proximity HUD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("VL53L0X ToF PROXIMITY HUD", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Text("4-Direction distance sensor scan in millimeters", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    Spacer(modifier = Modifier.height(16.dp))

                    val tof = telemetry?.tof ?: listOf(0, 0, 0, 0) // Front, Right, Back, Left
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val radius = 70f

                            // Draw HUD circle layers
                            drawCircle(color = VioletPrimary.copy(alpha = 0.1f), radius = radius * 2f, center = Offset(cx, cy))
                            drawCircle(color = VioletPrimary.copy(alpha = 0.05f), radius = radius, center = Offset(cx, cy))
                            drawCircle(color = VioletPrimary, radius = 24f, center = Offset(cx, cy), style = Stroke(width = 2f))

                            // Draw central robot shape
                            drawRect(
                                color = VioletPrimary.copy(alpha = 0.3f),
                                size = Size(36f, 48f),
                                topLeft = Offset(cx - 18f, cy - 24f)
                            )

                            // Translate distance values to plot indicators (Scale: max 1200mm = 130px)
                            val maxDist = 1200f
                            val scanScale = 110f

                            // 0: FRONT (UP)
                            val distFront = tof.getOrElse(0) { 0 }
                            val fY = cy - ((distFront.toFloat() / maxDist).coerceIn(0.1f, 1f) * scanScale)
                            drawCircle(if (distFront < 150) AccentRed else AccentGreen, radius = 6f, center = Offset(cx, fY))
                            drawLine(VioletTertiary.copy(alpha = 0.3f), Offset(cx, cy), Offset(cx, fY), 1f)

                            // 1: RIGHT (RIGHT)
                            val distRight = tof.getOrElse(1) { 0 }
                            val rX = cx + ((distRight.toFloat() / maxDist).coerceIn(0.1f, 1f) * scanScale)
                            drawCircle(if (distRight < 150) AccentRed else AccentGreen, radius = 6f, center = Offset(rX, cy))
                            drawLine(VioletTertiary.copy(alpha = 0.3f), Offset(cx, cy), Offset(rX, cy), 1f)

                            // 2: BACK (DOWN)
                            val distBack = tof.getOrElse(2) { 0 }
                            val bY = cy + ((distBack.toFloat() / maxDist).coerceIn(0.1f, 1f) * scanScale)
                            drawCircle(if (distBack < 150) AccentRed else AccentGreen, radius = 6f, center = Offset(cx, bY))
                            drawLine(VioletTertiary.copy(alpha = 0.3f), Offset(cx, cy), Offset(cx, bY), 1f)

                            // 3: LEFT (LEFT)
                            val distLeft = tof.getOrElse(3) { 0 }
                            val lX = cx - ((distLeft.toFloat() / maxDist).coerceIn(0.1f, 1f) * scanScale)
                            drawCircle(if (distLeft < 150) AccentRed else AccentGreen, radius = 6f, center = Offset(lX, cy))
                            drawLine(VioletTertiary.copy(alpha = 0.3f), Offset(cx, cy), Offset(lX, cy), 1f)
                        }

                        // Overlay millimeter readings
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.TopCenter)) {
                            Text("F: ${tof.getOrElse(0) { 0 }}mm", color = OnSurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                            Text("R: ${tof.getOrElse(1) { 0 }}mm", color = OnSurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.BottomCenter)) {
                            Text("B: ${tof.getOrElse(2) { 0 }}mm", color = OnSurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
                            Text("L: ${tof.getOrElse(3) { 0 }}mm", color = OnSurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Motor wheel speeds chart
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MEASURED ENCODER SPEEDS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    Text("Individual wheel velocities in radians/second", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    val flHistory = wheelSpeedsHistory.map { it.getOrElse(0) { 0f } }
                    val frHistory = wheelSpeedsHistory.map { it.getOrElse(1) { 0f } }
                    val rlHistory = wheelSpeedsHistory.map { it.getOrElse(2) { 0f } }
                    val rrHistory = wheelSpeedsHistory.map { it.getOrElse(3) { 0f } }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(DarkSurfaceElevated, RoundedCornerShape(8.dp))
                    ) {
                        val w = size.width
                        val h = size.height

                        if (wheelSpeedsHistory.size >= 2) {
                            val maxRadSec = 25f
                            val stepX = w / (wheelSpeedsHistory.size - 1)

                            // Helper function to draw dynamic colored curve
                            fun drawWheelCurve(historyList: List<Float>, color: Color) {
                                val path = Path().apply {
                                    val first = historyList.firstOrNull() ?: 0f
                                    val normY = (Math.abs(first) / maxRadSec).coerceIn(0f, 1f)
                                    moveTo(0f, h - (normY * h))

                                    for (i in 1 until historyList.size) {
                                        val normCurrent = (Math.abs(historyList[i]) / maxRadSec).coerceIn(0f, 1f)
                                        lineTo(i * stepX, h - (normCurrent * h))
                                    }
                                }
                                drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
                            }

                            drawWheelCurve(flHistory, VioletPrimary)
                            drawWheelCurve(frHistory, VioletTertiary)
                            drawWheelCurve(rlHistory, AccentGreen)
                            drawWheelCurve(rrHistory, AccentOrange)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val latestSpeeds = telemetry?.wheelSpeeds ?: listOf(0f, 0f, 0f, 0f)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(VioletPrimary, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FL: ${String.format("%.1f", latestSpeeds.getOrElse(0) { 0f })}", fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(VioletTertiary, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FR: ${String.format("%.1f", latestSpeeds.getOrElse(1) { 0f })}", fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(AccentGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RL: ${String.format("%.1f", latestSpeeds.getOrElse(2) { 0f })}", fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(AccentOrange, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RR: ${String.format("%.1f", latestSpeeds.getOrElse(3) { 0f })}", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Raw text metrics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RAW CORE TELEMETRY PACKET", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)
                    HorizontalDivider(color = DarkSurfaceElevated)

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Pico Uptime (ms):", color = OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text("${telemetry?.uptimeMs ?: 0}", fontWeight = FontWeight.Bold, color = OnSurfaceWhite)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Estimated Battery Voltage:", color = OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text("${telemetry?.batteryV ?: 0.0f} V", fontWeight = FontWeight.Bold, color = OnSurfaceWhite)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("IMU Angle:", color = OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                        val th = telemetry?.odom?.theta ?: 0f
                        Text("${String.format("%.4f", th)} rad", fontWeight = FontWeight.Bold, color = OnSurfaceWhite)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Mecanum X, Y Offset:", color = OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text("X: ${telemetry?.odom?.x ?: 0f}, Y: ${telemetry?.odom?.y ?: 0f}", fontWeight = FontWeight.Bold, color = OnSurfaceWhite)
                    }
                }
            }
        }
    }
}


// ==========================================
// 6. HAMBURGER MODULE A: LED & WIFI TEST
// ==========================================
@Composable
fun LedWifiTestView(viewModel: RobotViewModel, onClose: () -> Unit) {
    val logs by viewModel.testLog.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = OnSurfaceWhite)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("WiFi & LED Test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VioletPrimary)
            }
            IconButton(onClick = { viewModel.clearConsole() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear", tint = OnSurfaceMuted)
            }
        }

        Text(
            text = "Validates core Pico-to-App message pipeline using LED toggle requests.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceMuted
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("ONBOARD GREEN LED SWITCH", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)

                IconButton(
                    onClick = { viewModel.sendLedToggleTest() },
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            if (viewModel.ledState) AccentGreen.copy(alpha = 0.2f) else DarkSurfaceElevated,
                            CircleShape
                        )
                        .border(2.dp, if (viewModel.ledState) AccentGreen else VioletPrimary, CircleShape)
                        .testTag("led_toggle_test_button")
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = "LED",
                        tint = if (viewModel.ledState) AccentGreen else OnSurfaceMuted,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    text = "STATUS: ${if (viewModel.ledState) "PICO LED ON" else "PICO LED OFF"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (viewModel.ledState) AccentGreen else OnSurfaceMuted
                )
            }
        }

        // Logs terminal screen
        Text("COMMUNICATION TRANSACTIONS CONSOLE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, DarkSurfaceElevated, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (line.contains("Error")) AccentRed else if (line.contains("Success") || line.contains("CONNECTED")) AccentGreen else OnSurfaceWhite
                    )
                }
            }
        }
    }
}


// ==========================================
// 7. HAMBURGER MODULE B: MOTOR DIRECT CONTROL & PID TUNER
// ==========================================
@Composable
fun MotorPidTestView(viewModel: RobotViewModel, onClose: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = OnSurfaceWhite)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Motor Direct Test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VioletPrimary)
            }
        }

        item {
            Text(
                text = "Bypasses coordinates loop to send direct target PWM values to isolated motor drivers. Used to test electrical integrity.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMuted
            )
        }

        // 4 Motors Sliders
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("ISOLATED MOTOR SPEEDS (PWM Ratio)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = VioletTertiary)

                    // FL
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("FRONT LEFT MOTOR (motor_fl)", style = MaterialTheme.typography.bodyMedium)
                            Text("${(viewModel.testMotorFlSpeed * 100).roundToInt()}%", color = VioletPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = viewModel.testMotorFlSpeed,
                                onValueChange = {
                                    viewModel.testMotorFlSpeed = it
                                    viewModel.sendMotorTestSpeed("motor_fl", it)
                                },
                                valueRange = -1.0f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary),
                                modifier = Modifier.weight(1f).testTag("motor_fl_slider")
                            )
                            TextButton(onClick = { viewModel.testMotorFlSpeed = 0f; viewModel.sendMotorTestSpeed("motor_fl", 0f) }) {
                                Text("STOP", color = AccentRed)
                            }
                        }
                    }

                    // FR
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("FRONT RIGHT MOTOR (motor_fr)", style = MaterialTheme.typography.bodyMedium)
                            Text("${(viewModel.testMotorFrSpeed * 100).roundToInt()}%", color = VioletPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = viewModel.testMotorFrSpeed,
                                onValueChange = {
                                    viewModel.testMotorFrSpeed = it
                                    viewModel.sendMotorTestSpeed("motor_fr", it)
                                },
                                valueRange = -1.0f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary),
                                modifier = Modifier.weight(1f).testTag("motor_fr_slider")
                            )
                            TextButton(onClick = { viewModel.testMotorFrSpeed = 0f; viewModel.sendMotorTestSpeed("motor_fr", 0f) }) {
                                Text("STOP", color = AccentRed)
                            }
                        }
                    }

                    // RL
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("REAR LEFT MOTOR (motor_rl)", style = MaterialTheme.typography.bodyMedium)
                            Text("${(viewModel.testMotorRlSpeed * 100).roundToInt()}%", color = VioletPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = viewModel.testMotorRlSpeed,
                                onValueChange = {
                                    viewModel.testMotorRlSpeed = it
                                    viewModel.sendMotorTestSpeed("motor_rl", it)
                                },
                                valueRange = -1.0f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary),
                                modifier = Modifier.weight(1f).testTag("motor_rl_slider")
                            )
                            TextButton(onClick = { viewModel.testMotorRlSpeed = 0f; viewModel.sendMotorTestSpeed("motor_rl", 0f) }) {
                                Text("STOP", color = AccentRed)
                            }
                        }
                    }

                    // RR
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("REAR RIGHT MOTOR (motor_rr)", style = MaterialTheme.typography.bodyMedium)
                            Text("${(viewModel.testMotorRrSpeed * 100).roundToInt()}%", color = VioletPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = viewModel.testMotorRrSpeed,
                                onValueChange = {
                                    viewModel.testMotorRrSpeed = it
                                    viewModel.sendMotorTestSpeed("motor_rr", it)
                                },
                                valueRange = -1.0f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = VioletPrimary),
                                modifier = Modifier.weight(1f).testTag("motor_rr_slider")
                            )
                            TextButton(onClick = { viewModel.testMotorRrSpeed = 0f; viewModel.sendMotorTestSpeed("motor_rr", 0f) }) {
                                Text("STOP", color = AccentRed)
                            }
                        }
                    }
                }
            }
        }

        // STOP ALL BUTTON
        item {
            Button(
                onClick = {
                    viewModel.testMotorFlSpeed = 0f
                    viewModel.testMotorFrSpeed = 0f
                    viewModel.testMotorRlSpeed = 0f
                    viewModel.testMotorRrSpeed = 0f
                    viewModel.sendMotorTestSpeed("motor_fl", 0f)
                    viewModel.sendMotorTestSpeed("motor_fr", 0f)
                    viewModel.sendMotorTestSpeed("motor_rl", 0f)
                    viewModel.sendMotorTestSpeed("motor_rr", 0f)
                    viewModel.logToConsole("Test: ALL DIRECT MOTORS SHUT DOWN")
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("emergency_stop_test_button")
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EMERGENCY SHUT DOWN ALL MOTORS", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}


// ==========================================
// 8. HAMBURGER MODULE C: RAW COMM PACKET LOGGER
// ==========================================
@Composable
fun RawCommLoggerView(viewModel: RobotViewModel, onClose: () -> Unit) {
    val logs by viewModel.testLog.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = OnSurfaceWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Raw Comm Logger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VioletPrimary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.clearConsole() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = OnSurfaceMuted)
            }
        }

        Text(
            text = "Prints JSON payload packets passing between Pico W and Android app in real-time.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceMuted
        )

        // Logs terminal screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, DarkSurfaceElevated, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (line.contains("Error")) AccentRed else if (line.contains("Config")) AccentOrange else if (line.contains("Result")) AccentGreen else OnSurfaceWhite
                    )
                }
            }
        }
    }
}
