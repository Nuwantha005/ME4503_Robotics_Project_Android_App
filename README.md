# WMR Controller

Android controller and test harness for the Autonomous Wheeled Mobile Robot (WMR) project. The app connects to the robot over Wi-Fi, sends drive and configuration commands over WebSocket, and displays live telemetry, mapping, and bring-up screens for individual hardware modules.

## What The App Does

The app is organized around two use cases:

1. Day-to-day robot operation with manual drive, mode switching, telemetry, and waypoint planning.
2. Isolated hardware bring-up screens for LED/Wi-Fi checks, motor and PID tuning, and raw communication logging.

The robot-side communication contract is shared with the firmware project. The message schema in this app matches the WebSocket payloads expected by the robot.

## Key Features

- Manual mecanum drive with four control schemes: D-pad buttons, virtual joystick, phone tilt, and field-centric tilt.
- Mode switching between `MANUAL` and `LINE_FOLLOW`.
- Obstacle avoidance toggle with configurable stop distance.
- Live telemetry views for wheel speeds, odometry, battery, IR array, ToF sensors, and line position.
- Mapping screen with odometry trail visualization and waypoint planning.
- Configuration screen for surface-level limits and low-level kinematics/PID tuning.
- Isolated test screens for Wi-Fi and LED checks, motor/PID testing, and raw WebSocket logging.
- Automatic WebSocket reconnect and connection status reporting.

## Tech Stack

- Kotlin + Jetpack Compose
- Material 3
- OkHttp WebSocket client
- Moshi JSON serialization
- AndroidX lifecycle, ViewModel, and coroutines
- Room dependencies are present for local persistence support, though the current UI is centered on live control and telemetry

## Project Layout

- [app/src/main/java/com/example/MainActivity.kt](app/src/main/java/com/example/MainActivity.kt) launches the Compose UI.
- [app/src/main/java/com/example/ui/screens/WmrDashboard.kt](app/src/main/java/com/example/ui/screens/WmrDashboard.kt) contains the main dashboard, tabs, and test screens.
- [app/src/main/java/com/example/ui/viewmodel/RobotViewModel.kt](app/src/main/java/com/example/ui/viewmodel/RobotViewModel.kt) owns connection state, command streaming, telemetry history, and sensor handling.
- [app/src/main/java/com/example/network/RobotWebSocketManager.kt](app/src/main/java/com/example/network/RobotWebSocketManager.kt) manages the socket connection and message routing.
- [app/src/main/java/com/example/network/Models.kt](app/src/main/java/com/example/network/Models.kt) defines the JSON messages shared with the robot.

## Requirements

- Android Studio with a recent Android SDK installed.
- A device or emulator running Android 7.0+ (`minSdk = 24`).
- A robot firmware build that exposes a WebSocket server on the robot AP, usually at `ws://192.168.4.1/`.

## Run Locally

1. Open Android Studio.
2. Select **Open** and choose this project directory.
3. Let Android Studio sync Gradle and download dependencies.
4. Run the app on an emulator or physical device.

If you are connecting to the robot hardware, make sure the phone or tablet is joined to the robot's Wi-Fi access point and that the robot IP in the app matches the firmware's WebSocket address.

## Release Signing

Debug builds run with the standard Android debug keystore.

For release builds, configure the signing environment used by [app/build.gradle.kts](app/build.gradle.kts):

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

If `KEYSTORE_PATH` is not set, the build falls back to `my-upload-key.jks` in the project root.

## Connection Notes

- Default robot IP: `192.168.4.1`
- WebSocket URL format: `ws://<robot-ip>/`
- The controller sends command packets periodically while the robot is connected and power is on.
- Telemetry and test results are received continuously and shown in the dashboard.

## Message Types

The app sends these outbound payloads:

- `cmd` for drive commands and top-level mode state
- `config` for speed, acceleration, obstacle, PID, and kinematics parameters
- `waypoints` for path planning uploads
- `test` for LED, motor, and odometry bring-up actions

It also listens for inbound:

- `telemetry`
- `test_result`

## Notes On AI Studio Files

This repository originated from an AI Studio template, which is why `.env.example` and Firebase AI dependencies are still present. The current app UI does not require a Gemini key to launch.

## Testing

The project includes unit, Robolectric, screenshot, and instrumentation test scaffolding under `app/src/test` and `app/src/androidTest`.

Run them from Android Studio or with Gradle as needed.

## Related Project

This app is one half of the full WMR system. The robot firmware repository owns the low-level control loop and the authoritative communication protocol documentation.
