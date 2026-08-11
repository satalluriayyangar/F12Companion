package com.f12companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.f12companion.MainActivity
import com.f12companion.R
import com.f12companion.weather.WeatherForecast
import com.f12companion.weather.WeatherProvider
import com.f12companion.weather.OpenMeteoWeatherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class F12SyncService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var weatherProvider: WeatherProvider
    private var syncJob: Job? = null

    companion object {
        const val CHANNEL_ID = "F12SyncChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
    }

    inner class LocalBinder : Binder() {
        fun getService(): F12SyncService = this@F12SyncService
    }

    override fun onCreate() {
        super.onCreate()
        weatherProvider = OpenMeteoWeatherProvider()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val lat = intent?.getDoubleExtra(EXTRA_LATITUDE, 0.0) ?: 0.0
        val lon = intent?.getDoubleExtra(EXTRA_LONGITUDE, 0.0) ?: 0.0
        startPeriodicSync(lat, lon)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
    }

    fun startPeriodicSync(latitude: Double, longitude: Double) {
        syncJob?.cancel()
        syncJob = serviceScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val forecast = weatherProvider.forecast(latitude, longitude)
                    val bleManager = com.f12companion.F12BleManager(applicationContext)
                    bleManager.sendWeather(forecast) { success, message ->
                        if (success) {
                            // Cache successful forecast
                            val prefs = getSharedPreferences("f12sync", MODE_PRIVATE)
                            prefs.edit().putString("last_forecast", forecast.toString()).apply()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(30 * 60 * 1000) // 30 minutes
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("F12 Companion")
            .setContentText("Weather sync active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "F12 Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
