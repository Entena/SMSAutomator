package com.example.smssender

import android.app.*
import android.content.*
import android.os.*
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import android.util.Log

class SmsServiceBackup2 : Service() {

    private lateinit var dbHelper: DatabaseHelper
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val urlEndpoint = "http://192.168.8.204:8080/api/v0/ready"
    private val SMS_SENT_ACTION = "SMS_SENT_ACTION"
    private val activeIds = Collections.synchronizedSet(HashSet<String>())

    // Enforce 2 seconds between sends (60s / 30 msgs = 2s)
    private val MIN_DELAY_MS = 2000L

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        scheduleNextTask(0)
        return START_STICKY
    }

    private fun scheduleNextTask(delayMs: Long) {
        scheduler.schedule({ networkTask() }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun networkTask() {
        val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // --- SPACING LOGIC ---
        val lastSent = dbHelper.getLastSentTime()
        val timeSinceLast = System.currentTimeMillis() - lastSent

        if (timeSinceLast < MIN_DELAY_MS) {
            val waitNeeded = MIN_DELAY_MS - timeSinceLast
            Log.d("SMS_LIMIT", "Spacing out requests. Waiting ${waitNeeded}ms")
            scheduleNextTask(waitNeeded)
            return
        }

        var currentId: String? = null
        try {
            val connection = URL(urlEndpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            val responseString = connection.inputStream.bufferedReader().readText()

            val json = JSONObject(responseString)
            val smsRequest = json.getJSONObject("smsrequest")
            val id = smsRequest.getString("id")
            val number = smsRequest.getString("number")
            val message = smsRequest.getString("message")

            currentId = id
            if (activeIds.contains(id)) { scheduleNextTask(5000); return }
            activeIds.add(id)

            if (performPostWithBody(id, "taken")) {
                sendSmsWithCallback(id, number, message, currentTimeStr, responseString)
            } else {
                performPostWithBody(id, "error")
                activeIds.remove(id)
                scheduleNextTask(5000)
            }
        } catch (e: Exception) {
            currentId?.let { id ->
                Thread { performPostWithBody(id, "error"); activeIds.remove(id) }.start()
            }
            scheduleNextTask(5000)
        }
    }

    private fun sendSmsWithCallback(id: String, number: String, message: String, time: String, rawJson: String) {
        val smsManager = getSystemService(SmsManager::class.java)
        val sentIntent = PendingIntent.getBroadcast(
            this, id.hashCode(),
            Intent(SMS_SENT_ACTION).apply { putExtra("sms_id", id) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedId = intent?.getStringExtra("sms_id") ?: id
                Thread {
                    try {
                        if (resultCode == Activity.RESULT_OK) {
                            // Update the timestamp in DB to mark this as a "sent" slot
                            dbHelper.updateLastSentTime()
                            performPostWithBody(receivedId, "sent")
                        } else {
                            performPostWithBody(receivedId, "error")
                        }
                    } finally {
                        activeIds.remove(receivedId)
                        // After finishing, wait 5s to poll for the next available task
                        scheduleNextTask(5000)
                    }
                }.start()
                broadcastUpdate(time, formatJSON(rawJson))
                context?.unregisterReceiver(this)
            }
        }
        registerReceiver(receiver, IntentFilter(SMS_SENT_ACTION), RECEIVER_EXPORTED)
        smsManager.sendTextMessage(number, null, message, sentIntent, null)
    }

    private fun performPostWithBody(id: String, status: String): Boolean {
        var success = false
        try {
            val url = URL("http://192.168.8.204:8080/api/v0/ready?id=$id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply { put("status", status) }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            success = (conn.responseCode == 200 || conn.responseCode == 204)
            conn.disconnect()
        } catch (e: Exception) { Log.e("SMS_NET", "POST Error: ${e.message}") }
        return success
    }

    private fun broadcastUpdate(time: String, json: String) {
        sendBroadcast(Intent("UPDATE_SMS_UI").apply {
            putExtra("time", time)
            putExtra("json", json)
        })
    }

    private fun formatJSON(jsonString: String): String {
        return try { JSONObject(jsonString).toString(4) } catch (e: Exception) { jsonString }
    }

    private fun createNotification(): Notification {
        val channelId = "sms_channel_kt"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "SMS", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Service Active")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { scheduler.shutdown(); super.onDestroy() }
}