package com.example.ui.viewmodel

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RobotViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val wsManager = RobotWebSocketManager(application)

    // Moshi JSON Adapters
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val cmdAdapter = moshi.adapter(CmdMessage::class.java)
    private val configAdapter = moshi.adapter(ConfigMessage::class.java)
    private val waypointsAdapter = moshi.adapter(WaypointsMessage::class.java)
    private val testAdapter = moshi.adapter(TestMessage::class.java)

    // WebSocket Flows
    val connectionStatus = wsManager.connectionStatus
    val packetsReceivedPerSec = wsManager.packetsReceivedPerSec

    private val _telemetry = MutableStateFlow<TelemetryMessage?>(null)
    val telemetry = _telemetry.asStateFlow()

    private val _testLog = MutableStateFlow<List<String>>(emptyList())
    val testLog = _testLog.asStateFlow()

    // Global Robot Top Bar States
    var isPowerOn by mutableStateOf(true)
    var activeMode by mutableStateOf(RobotMode.MANUAL)
    var activeScheme by mutableStateOf(ControlScheme.JOYSTICK)
    var obstacleEnabled by mutableStateOf(true)

    // Connection Details
    var robotIp by mutableStateOf("192.168.4.1")

    // Surface Level Config Variables
    var maxSpeedMps by mutableStateOf(0.25f)
    var maxAccelMps2 by mutableStateOf(1.5f)
    var obstacleStopDistanceMm by mutableStateOf(150f)

    // Micro Managing PID & Kinematics Config Variables
    var pidEnabled by mutableStateOf(false)
    var pidKp by mutableStateOf(1.2f)
    var pidKi by mutableStateOf(0.05f)
    var pidKd by mutableStateOf(0.01f)
    var wheelRadiusM by mutableStateOf(0.04f)
    var wheelbaseLxM by mutableStateOf(0.06f)
    var wheelbaseLyM by mutableStateOf(0.05f)
    var encoderCountsPerRev by mutableStateOf(360f)
    var motorGearRatio by mutableStateOf(1.0f)

    // Manual Drive Command Values
    var cmdX by mutableStateOf(0f)
    var cmdY by mutableStateOf(0f)
    var cmdRot by mutableStateOf(0f)

    // Telemetry Histories (for charts)
    private val maxHistorySize = 50
    private val _wheelSpeedsHistory = MutableStateFlow<List<List<Float>>>(emptyList())
    val wheelSpeedsHistory = _wheelSpeedsHistory.asStateFlow()

    private val _robotSpeedHistory = MutableStateFlow<List<Float>>(emptyList())
    val robotSpeedHistory = _robotSpeedHistory.asStateFlow()

    private val _robotAccelHistory = MutableStateFlow<List<Float>>(emptyList())
    val robotAccelHistory = _robotAccelHistory.asStateFlow()

    private val _irHistory = MutableStateFlow<List<List<Int>>>(emptyList())
    val irHistory = _irHistory.asStateFlow()

    private val _lineHistory = MutableStateFlow<List<Int>>(emptyList())
    val lineHistory = _lineHistory.asStateFlow()

    // Mapping & Path Planning States
    private val _robotPath = MutableStateFlow<List<Pair<Float, Float>>>(listOf(Pair(0f, 0f)))
    val robotPath = _robotPath.asStateFlow()

    private val _plannedWaypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val plannedWaypoints = _plannedWaypoints.asStateFlow()

    // Test Harness State
    var ledState by mutableStateOf(false)
    var testMotorFlSpeed by mutableStateOf(0f)
    var testMotorFrSpeed by mutableStateOf(0f)
    var testMotorRlSpeed by mutableStateOf(0f)
    var testMotorRrSpeed by mutableStateOf(0f)

    // Accelerometer Sensor for Tilt Control
    private val sensorManager = application.getSystemService(Application.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isSensorRegistered = false

    // Periodic Background Jobs
    private var cmdLoopJob: Job? = null
    private var telemetryCollectionJob: Job? = null
    private var testResultCollectionJob: Job? = null

    init {
        // Automatically start the websocket connection
        connectToRobot()

        // Start command sending loop (10 Hz)
        startCommandLoop()

        // Gather telemetry streams
        startTelemetryCollection()

        // Gather test results
        startTestResultCollection()
    }

    fun connectToRobot() {
        viewModelScope.launch {
            logToConsole("System: Connecting to $robotIp...")
            wsManager.connect(robotIp)
        }
    }

    fun disconnectFromRobot() {
        viewModelScope.launch {
            logToConsole("System: Disconnecting from robot...")
            wsManager.disconnect()
        }
    }

    // Starts broadcasting cmd packets at 10 Hz
    private fun startCommandLoop() {
        cmdLoopJob?.cancel()
        cmdLoopJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(100) // 100 ms (10 Hz)
                if (connectionStatus.value == ConnectionStatus.CONNECTED && isPowerOn) {
                    try {
                        val message = CmdMessage(
                            mode = activeMode.value,
                            scheme = activeScheme.value,
                            x = cmdX,
                            y = cmdY,
                            rot = cmdRot,
                            obstacleEnabled = obstacleEnabled
                        )
                        val json = cmdAdapter.toJson(message)
                        wsManager.send(json)
                    } catch (e: Exception) {
                        Log.e("RobotVM", "Command send fail", e)
                    }
                }
            }
        }
    }

    private fun startTelemetryCollection() {
        telemetryCollectionJob?.cancel()
        telemetryCollectionJob = viewModelScope.launch(Dispatchers.IO) {
            var lastSpeed = 0f
            var lastTime = SystemClock.elapsedRealtime()

            wsManager.telemetryFlow.collect { message ->
                _telemetry.value = message

                // Path Mapping (Append odom coordinates)
                val currentOdom = message.odom
                val currentPathList = _robotPath.value
                val lastPoint = currentPathList.lastOrNull()
                if (lastPoint == null || Math.hypot((currentOdom.x - lastPoint.first).toDouble(), (currentOdom.y - lastPoint.second).toDouble()) > 0.02) {
                    _robotPath.value = (currentPathList + Pair(currentOdom.x, currentOdom.y)).takeLast(500)
                }

                // Speed and acceleration calculation for graphs
                // Speed = average measured wheel speeds * radius
                val avgWheelSpeed = message.wheelSpeeds.map { Math.abs(it) }.average().toFloat()
                val currentSpeed = avgWheelSpeed * wheelRadiusM

                val now = SystemClock.elapsedRealtime()
                val dT = (now - lastTime) / 1000f
                val currentAccel = if (dT > 0) (currentSpeed - lastSpeed) / dT else 0f

                lastSpeed = currentSpeed
                lastTime = now

                // Append Histories
                _wheelSpeedsHistory.value = (_wheelSpeedsHistory.value + listOf(message.wheelSpeeds)).takeLast(maxHistorySize)
                _robotSpeedHistory.value = (_robotSpeedHistory.value + currentSpeed).takeLast(maxHistorySize)
                _robotAccelHistory.value = (_robotAccelHistory.value + currentAccel).takeLast(maxHistorySize)
                _irHistory.value = (_irHistory.value + listOf(message.ir)).takeLast(maxHistorySize)
                _lineHistory.value = (_lineHistory.value + message.linePosition).takeLast(maxHistorySize)
            }
        }
    }

    private fun startTestResultCollection() {
        testResultCollectionJob?.cancel()
        testResultCollectionJob = viewModelScope.launch(Dispatchers.IO) {
            wsManager.testResultFlow.collect { testResult ->
                logToConsole("Test Result: target=${testResult.target}, state=${testResult.state}, value=${testResult.value}")
                if (testResult.target == "led") {
                    ledState = testResult.state == "on"
                }
            }
        }
    }

    // Sends the configuration package
    fun sendConfigToRobot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configMsg = ConfigMessage(
                    maxSpeedMps = maxSpeedMps,
                    maxAccelMps2 = maxAccelMps2,
                    obstacleStopDistanceMm = obstacleStopDistanceMm,
                    pidEnabled = pidEnabled,
                    pidKp = pidKp,
                    pidKi = pidKi,
                    pidKd = pidKd,
                    wheelRadiusM = wheelRadiusM,
                    wheelbaseLxM = wheelbaseLxM,
                    wheelbaseLyM = wheelbaseLyM,
                    encoderCountsPerRev = encoderCountsPerRev,
                    motorGearRatio = motorGearRatio
                )
                val json = configAdapter.toJson(configMsg)
                val ok = wsManager.send(json)
                if (ok) {
                    logToConsole("Config: Successfully sent parameters to robot")
                } else {
                    logToConsole("Config Error: Failed to send, check connection status")
                }
            } catch (e: Exception) {
                logToConsole("Config Error: ${e.message}")
            }
        }
    }

    // Adds a waypoint on Mapping screen
    fun addWaypoint(x: Float, y: Float) {
        val current = _plannedWaypoints.value
        _plannedWaypoints.value = current + Waypoint(x, y)
        logToConsole("Map: Added waypoint at ($x, $y)")
    }

    fun removeWaypoint(index: Int) {
        val current = _plannedWaypoints.value.toMutableList()
        if (index in current.indices) {
            val removed = current.removeAt(index)
            _plannedWaypoints.value = current
            logToConsole("Map: Removed waypoint at (${removed.x}, ${removed.y})")
        }
    }

    fun clearWaypoints() {
        _plannedWaypoints.value = emptyList()
        logToConsole("Map: Cleared all planned waypoints")
    }

    fun uploadWaypointsToRobot() {
        viewModelScope.launch(Dispatchers.IO) {
            val points = _plannedWaypoints.value
            if (points.isEmpty()) {
                logToConsole("Map Error: No waypoints planned to send")
                return@launch
            }
            try {
                val message = WaypointsMessage(points = points)
                val json = waypointsAdapter.toJson(message)
                val ok = wsManager.send(json)
                if (ok) {
                    logToConsole("Map: Sent ${points.size} waypoints to robot path follow state")
                } else {
                    logToConsole("Map Error: Failed to send waypoints over WebSocket")
                }
            } catch (e: Exception) {
                logToConsole("Map Error: Failed to serialize waypoints: ${e.message}")
            }
        }
    }

    // Zeroes current odometry starting point
    fun zeroOdometry() {
        viewModelScope.launch(Dispatchers.IO) {
            _robotPath.value = listOf(Pair(0f, 0f))
            // Informing robot to reset internally is typically done with a command or test msg
            val testMsg = TestMessage(target = "odometry", action = "zero")
            val json = testAdapter.toJson(testMsg)
            wsManager.send(json)
            logToConsole("Map: Odometry zeroed internally and path cleared")
        }
    }

    // Helper to print transactions to the scrolling test console
    fun logToConsole(line: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedLine = "[$timestamp] $line"
        _testLog.value = (_testLog.value + formattedLine).takeLast(100)
    }

    fun clearConsole() {
        _testLog.value = emptyList()
    }

    // Send Hamburger Menu Test Commands
    fun sendLedToggleTest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msg = TestMessage(target = "led", action = "toggle")
                val json = testAdapter.toJson(msg)
                val ok = wsManager.send(json)
                logToConsole("Test: Sent LED toggle command (Success=$ok)")
            } catch (e: Exception) {
                logToConsole("Test Error: LED toggle serialization failed")
            }
        }
    }

    fun sendMotorTestSpeed(motorTarget: String, speed: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msg = TestMessage(target = motorTarget, action = "set_pwm", value = speed)
                val json = testAdapter.toJson(msg)
                wsManager.send(json)
                logToConsole("Test: Direct motor speed command target=$motorTarget value=$speed")
            } catch (e: Exception) {
                logToConsole("Test Error: Motor speed send failed")
            }
        }
    }

    // Accelerometer Tilt Logic
    fun registerAccelerometerListener() {
        if (!isSensorRegistered && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            isSensorRegistered = true
            logToConsole("Tilt: Device accelerometer listener engaged")
        }
    }

    fun unregisterAccelerometerListener() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
            logToConsole("Tilt: Device accelerometer listener disengaged")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || activeScheme != ControlScheme.TILT || activeMode != RobotMode.MANUAL || !isPowerOn) {
            return
        }

        // Standard phone tilt mapping
        // Acceleration values on X and Y axes
        val ax = event.values[0] // left-right tilt. ax > 0 tilt left, ax < 0 tilt right
        val ay = event.values[1] // forward-backward tilt. ay > 0 tilt down, ay < 0 tilt up

        // Map to -1f..1f range with deadzone
        val rawX = -ax / 9.81f  // tilt left -> go left, tilt right -> go right
        val rawY = -ay / 9.81f  // tilt forward -> go forward, tilt back -> go back

        val deadZone = 0.08f
        cmdX = if (Math.abs(rawX) > deadZone) rawX.coerceIn(-1f, 1f) else 0f
        cmdY = if (Math.abs(rawY) > deadZone) rawY.coerceIn(-1f, 1f) else 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Unneeded
    }

    override fun onCleared() {
        super.onCleared()
        unregisterAccelerometerListener()
        wsManager.disconnect()
        cmdLoopJob?.cancel()
        telemetryCollectionJob?.cancel()
        testResultCollectionJob?.cancel()
    }
}
