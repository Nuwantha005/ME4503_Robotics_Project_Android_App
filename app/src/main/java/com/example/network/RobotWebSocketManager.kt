package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class RobotWebSocketManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null
    private var activeIp = "192.168.4.1"
    private var isClosedPurposefully = false

    // Moshi instance to deserialize JSON payloads
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val telemetryAdapter = moshi.adapter(TelemetryMessage::class.java)
    private val testResultAdapter = moshi.adapter(TestResultMessage::class.java)

    // Flow states
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _telemetryFlow = MutableSharedFlow<TelemetryMessage>(replay = 1)
    val telemetryFlow = _telemetryFlow.asSharedFlow()

    private val _testResultFlow = MutableSharedFlow<TestResultMessage>(extraBufferCapacity = 16)
    val testResultFlow = _testResultFlow.asSharedFlow()

    // Counts for packet rate
    private val _packetsReceivedPerSec = MutableStateFlow(0)
    val packetsReceivedPerSec = _packetsReceivedPerSec.asStateFlow()
    private var messageCount = 0
    private var statsJob: Job? = null

    init {
        startStatsTracker()
    }

    fun connect(ip: String) {
        activeIp = ip
        isClosedPurposefully = false
        reconnectJob?.cancel()

        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            return
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING
        val url = "ws://$activeIp/"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("RobotWS", "Connected to robot at $url")
                _connectionStatus.value = ConnectionStatus.CONNECTED
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                messageCount++
                try {
                    // Peek inside the JSON type to route properly
                    if (text.contains("\"type\":\"telemetry\"")) {
                        val telemetry = telemetryAdapter.fromJson(text)
                        if (telemetry != null) {
                            scope.launch { _telemetryFlow.emit(telemetry) }
                        }
                    } else if (text.contains("\"type\":\"test_result\"")) {
                        val testResult = testResultAdapter.fromJson(text)
                        if (testResult != null) {
                            scope.launch { _testResultFlow.emit(testResult) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RobotWS", "Error parsing message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RobotWS", "WebSocket closing: $code / $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RobotWS", "WebSocket closed: $code / $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                triggerReconnectIfNeeded()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("RobotWS", "WebSocket Failure: ${t.message}", t)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                triggerReconnectIfNeeded()
            }
        })
    }

    fun disconnect() {
        isClosedPurposefully = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun send(messageJson: String): Boolean {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) return false
        return webSocket?.send(messageJson) ?: false
    }

    private fun triggerReconnectIfNeeded() {
        if (isClosedPurposefully) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            delay(3000) // Wait 3 seconds before trying to reconnect
            Log.d("RobotWS", "Attempting automatic reconnect to $activeIp")
            connect(activeIp)
        }
    }

    private fun startStatsTracker() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                delay(1000)
                _packetsReceivedPerSec.value = messageCount
                messageCount = 0
            }
        }
    }
}
