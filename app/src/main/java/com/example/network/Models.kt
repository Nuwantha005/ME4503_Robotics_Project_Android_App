package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Connection States
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

// Drive mode selected on the top bar
enum class RobotMode(val value: String) {
    MANUAL("manual"),
    LINE_FOLLOW("line_follow")
}

// Control Scheme for MANUAL drive mode
enum class ControlScheme(val value: String) {
    BUTTONS("buttons"),
    JOYSTICK("joystick"),
    TILT("tilt"),
    FIELD_CENTRIC("field_centric")
}

// Outbound CMD Payload
@JsonClass(generateAdapter = true)
data class CmdMessage(
    val type: String = "cmd",
    val mode: String,
    val scheme: String,
    val x: Float,
    val y: Float,
    val rot: Float,
    @Json(name = "obstacle_enabled") val obstacleEnabled: Boolean
)

// Outbound CONFIG Payload
@JsonClass(generateAdapter = true)
data class ConfigMessage(
    val type: String = "config",
    @Json(name = "max_speed_mps") val maxSpeedMps: Float,
    @Json(name = "max_accel_mps2") val maxAccelMps2: Float,
    @Json(name = "obstacle_stop_distance_mm") val obstacleStopDistanceMm: Float,
    @Json(name = "pid_enabled") val pidEnabled: Boolean,
    @Json(name = "pid_kp") val pidKp: Float,
    @Json(name = "pid_ki") val pidKi: Float,
    @Json(name = "pid_kd") val pidKd: Float,
    @Json(name = "wheel_radius_m") val wheelRadiusM: Float,
    @Json(name = "wheelbase_lx_m") val wheelbaseLxM: Float,
    @Json(name = "wheelbase_ly_m") val wheelbaseLyM: Float,
    @Json(name = "encoder_counts_per_rev") val encoderCountsPerRev: Float,
    @Json(name = "motor_gear_ratio") val motorGearRatio: Float
)

// Waypoint representation for path planning
@JsonClass(generateAdapter = true)
data class Waypoint(
    val x: Float,
    val y: Float
)

// Outbound WAYPOINTS Payload
@JsonClass(generateAdapter = true)
data class WaypointsMessage(
    val type: String = "waypoints",
    val points: List<Waypoint>
)

// Outbound TEST Payload
@JsonClass(generateAdapter = true)
data class TestMessage(
    val type: String = "test",
    val target: String,
    val action: String,
    val value: Float? = null
)

// Inbound ODOMETRY representation
@JsonClass(generateAdapter = true)
data class Odometry(
    val x: Float,
    val y: Float,
    val theta: Float
)

// Inbound TELEMETRY Payload
@JsonClass(generateAdapter = true)
data class TelemetryMessage(
    val type: String = "telemetry",
    @Json(name = "wheel_speeds") val wheelSpeeds: List<Float> = listOf(0f, 0f, 0f, 0f),
    val odom: Odometry = Odometry(0f, 0f, 0f),
    val ir: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0, 0),
    @Json(name = "line_position") val linePosition: Int = -999,
    val tof: List<Int> = listOf(0, 0, 0, 0),
    @Json(name = "battery_v") val batteryV: Float = 0f,
    @Json(name = "uptime_ms") val uptimeMs: Long = 0L
)

// Inbound TEST_RESULT Payload
@JsonClass(generateAdapter = true)
data class TestResultMessage(
    val type: String = "test_result",
    val target: String,
    val state: String? = null,
    val value: Float? = null
)
