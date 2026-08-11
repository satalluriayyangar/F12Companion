package com.f12companion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.app.RemoteInput
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.f12companion.databinding.ActivityMainBinding
import com.f12companion.model.BleLogEntry
import com.f12companion.model.BleState
import com.f12companion.model.Direction
import com.f12companion.call.CallRejectHandler
import com.f12companion.reply.NotificationReplyService
import com.f12companion.reply.VoiceReplyHandler
import com.f12companion.weather.WeatherForecast
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val manager = F12BleManager(this)
    private val adapter = LogAdapter()
    private var callAlarmEnabled = false
    private var msgAlarmEnabled = false

    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }

    private var notificationReplyService: NotificationReplyService? = null
    private var boundToNotificationService = false
    private lateinit var voiceReplyHandler: VoiceReplyHandler

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Permissions required for BLE and features", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: android.os.IBinder) {
            val binder = service as NotificationReplyService.LocalBinder
            notificationReplyService = binder.getService()
            boundToNotificationService = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundToNotificationService = false
            notificationReplyService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvLog.layoutManager = LinearLayoutManager(this)
        binding.rvLog.adapter = adapter

        voiceReplyHandler = VoiceReplyHandler(SpeechRecognizer.createSpeechRecognizer(this))

        ensurePermissions()
        observeState()
        observeLogs()
        setupBleCallbacks()

        binding.btnScan.setOnClickListener { startScan() }
        binding.btnConnect.setOnClickListener { /* set by scan */ }
        binding.btnSendGolden.setOnClickListener { sendGolden() }
        binding.btnSendWeather.setOnClickListener { sendWeather() }
        binding.btnCallAlarm.setOnClickListener { toggleCallAlarm() }
        binding.btnMsgAlarm.setOnClickListener { toggleMsgAlarm() }
        binding.btnSendNotice.setOnClickListener { sendTestNotice() }
        binding.btnVoiceReply.setOnClickListener { startVoiceReply() }
        binding.btnNotificationReply.setOnClickListener { sendNotificationReply() }
        binding.btnVoiceReply.setOnClickListener { startVoiceReply() }
    }

    private fun setupBleCallbacks() {
        manager.onUnknownCommand = { dataType, bytes ->
            runOnUiThread {
                Toast.makeText(this, "Unknown BLE cmd: $dataType", Toast.LENGTH_SHORT).show()
            }
        }

        manager.onCallControlReceived = { cmd ->
            runOnUiThread {
                if (cmd == 2) {
                    CallRejectHandler.rejectCall(this)
                }
            }
        }

        manager.onNotificationReplyTriggered = { text ->
            runOnUiThread {
                Toast.makeText(this, "Reply triggered: $text", Toast.LENGTH_SHORT).show()
                sendReplyToLatestNotification(text)
            }
        }

        manager.onVoiceReplyTriggered = {
            runOnUiThread {
                Toast.makeText(this, "Voice reply triggered", Toast.LENGTH_SHORT).show()
                startVoiceReply()
            }
        }
    }

    private fun ensurePermissions() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        required.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        required.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            required.add(Manifest.permission.READ_PHONE_STATE)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startScan() {
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            manager.scan().collect { device ->
                runOnUiThread {
                    binding.btnConnect.isEnabled = true
                    binding.btnConnect.text = "Connect ${device.address}"
                    binding.btnConnect.setOnClickListener {
                        connect(device)
                    }
                    Toast.makeText(this@MainActivity, "Found: ${device.address}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connect(device: android.bluetooth.BluetoothDevice) {
        lifecycleScope.launch {
            manager.connect(device).collect { /* flow is informational; state updates handled by observeState */ }
        }
    }

    private fun sendGolden() {
        manager.sendGoldenPacketAndWait { success, message ->
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendWeather() {
        val location = getLastLocation()
        if (location == null) {
            Toast.makeText(this, "Location not available for weather", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val provider = com.f12companion.weather.OpenMeteoWeatherProvider()
                val forecast = provider.forecast(location.latitude, location.longitude)
                manager.sendWeather(forecast) { success, message ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Weather fetch failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggleCallAlarm() {
        callAlarmEnabled = !callAlarmEnabled
        binding.btnCallAlarm.text = "Call Alarm: ${if (callAlarmEnabled) "On" else "Off"}"
        manager.sendCallAlarm(callAlarmEnabled) { success, message ->
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun toggleMsgAlarm() {
        msgAlarmEnabled = !msgAlarmEnabled
        binding.btnMsgAlarm.text = "Msg Alarm: ${if (msgAlarmEnabled) "On" else "Off"}"
        manager.sendMessageAlarm(msgAlarmEnabled) { success, message ->
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun sendTestNotice() {
        manager.sendMessageNotice("Test App", "Hello from F12 Companion", 2) { success, message ->
            runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun startVoiceReply() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Record audio permission required", Toast.LENGTH_SHORT).show()
            return
        }

        voiceReplyHandler.onSpeechResult = { text ->
            runOnUiThread {
                Toast.makeText(this, "Voice: $text", Toast.LENGTH_SHORT).show()
                sendReplyToLatestNotification(text)
            }
        }
        voiceReplyHandler.onSpeechError = { error ->
            runOnUiThread {
                Toast.makeText(this, "Voice error: $error", Toast.LENGTH_SHORT).show()
            }
        }
        voiceReplyHandler.startListening()
    }

    private fun sendNotificationReply() {
        val text = "OK"
        sendReplyToLatestNotification(text)
    }

    private fun sendReplyToLatestNotification(text: String) {
        val service = notificationReplyService
        if (service == null) {
            Toast.makeText(this, "Notification service not bound", Toast.LENGTH_SHORT).show()
            return
        }
        val pendingIntent = service.getLatestReplyPendingIntent()
        val remoteInput = service.getLatestReplyRemoteInput()
        if (pendingIntent == null || remoteInput == null) {
            Toast.makeText(this, "No notification with reply action found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val results = Bundle()
            results.putCharSequence(remoteInput.resultKey, text)
            val fillInIntent = Intent()
            fillInIntent.putExtra("android.remoteinput.resultsData", results)
            pendingIntent.send(this, 0, fillInIntent)
            Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Reply failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, NotificationReplyService::class.java), notificationServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (boundToNotificationService) {
            unbindService(notificationServiceConnection)
            boundToNotificationService = false
        }
    }

    private fun observeNotificationService() {
        lifecycleScope.launch {
            notificationReplyService?.notificationPosted?.collect { notification ->
                val (pkg, text) = notification ?: return@collect
                runOnUiThread {
                    Log.d("MainActivity", "Notification: $pkg - $text")
                }
            }
        }
    }

    private fun getLastLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            manager.state.collect { state ->
                val connected = state is BleState.Connected
                binding.tvStatus.text = when (state) {
                    is BleState.Idle -> getString(R.string.status_idle)
                    is BleState.Scanning -> getString(R.string.status_scanning)
                    is BleState.Connecting -> getString(R.string.status_connecting)
                    is BleState.Connected -> "${getString(R.string.status_connected)} ${state.address}"
                    is BleState.Disconnected -> "Disconnected: ${state.reason ?: "unknown"}"
                    is BleState.Error -> "Error: ${state.message}"
                }
                binding.btnConnect.isEnabled = !connected
                binding.btnSendGolden.isEnabled = connected
                binding.btnSendWeather.isEnabled = connected
                binding.btnSendNotice.isEnabled = connected
            }
        }
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            manager.logs.collect { entries ->
                adapter.submitList(entries)
                if (entries.isNotEmpty()) {
                    binding.rvLog.smoothScrollToPosition(entries.size - 1)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceReplyHandler.destroy()
    }
}
