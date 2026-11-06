package com.jarvis.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Name, greeting text and the on/off switch for the voice greeting. */
object Greeting {
    private fun prefs(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun name(c: Context): String = prefs(c).getString("name", "Suryansh") ?: "Suryansh"
    fun setName(c: Context, n: String) = prefs(c).edit().putString("name", n).apply()

    fun voiceOn(c: Context): Boolean = prefs(c).getBoolean("voice", true)
    fun setVoice(c: Context, on: Boolean) = prefs(c).edit().putBoolean("voice", on).apply()

    fun hello(c: Context): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when {
            h < 5 -> "Hello"
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        return "$part, ${name(c)}."
    }

    fun spoken(c: Context): String {
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val bm = c.getSystemService(BatteryManager::class.java)
        val bat = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return hello(c) + " It is $time." + if (bat in 1..100) " Battery is at $bat percent." else ""
    }
}

/** Runs quietly in the background and speaks the greeting each time the phone is unlocked. */
class GreetService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_TEST = "com.jarvis.launcher.TEST"
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var last = 0L

    private val unlockRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            greet(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        val f = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlockRx, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(unlockRx, f)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale("en", "IN")
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_TEST) greet(true)
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jarvis", "IONE", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, "jarvis")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("IONE is active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun greet(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - last < 60_000) return
            if (!Greeting.voiceOn(this)) return
            val am = getSystemService(AudioManager::class.java)
            if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        }
        if (!ready) return
        last = now
        tts?.speak(Greeting.spoken(this), TextToSpeech.QUEUE_FLUSH, null, "greet")
    }

    override fun onDestroy() {
        unregisterReceiver(unlockRx)
        tts?.shutdown()
        super.onDestroy()
    }
}

/** Starts the greeting service again after the phone restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                context.startForegroundService(Intent(context, GreetService::class.java))
            } catch (e: Exception) {
                // the launcher will start it the next time it opens
            }
        }
    }
}
