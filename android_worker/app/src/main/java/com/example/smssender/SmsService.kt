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

class SmsService : Service() {

    private lateinit var dbHelper: DatabaseHelper
    private val taskExecutor = Executors.newSingleThreadScheduledExecutor()
    private val urlEndpoint = "http://192.168.8.204:8080/api/v0/ready"
    private val SMS_SENT_ACTION = "SMS_SENT_ACTION"

    private val MIN_DELAY_MS = 2000L

    // Global variable initialized to current time on start
    private var lastSentTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
        // Populate global variable with current time to start the gap immediately
        lastSentTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        // Start the first poll
        enqueueNextPoll()

        return START_STICKY
    }

    /**
     * This is the only way to trigger a new poll.
     * It calculates the required delay based on the global lastSentTime.
     */
    private fun enqueueNextPoll() {
        val now = System.currentTimeMillis()
        val timePassed = now - lastSentTime

        // Calculate how much longer we must wait to satisfy the 2-second rule
        val delay = if (timePassed < MIN_DELAY_MS) {
            MIN_DELAY_MS - timePassed
        } else {
            500L // Minimum idle time between cycles
        }

        Log.d("SMS_SYNC", "Scheduling next poll in ${delay}ms")

        taskExecutor.schedule({
            performNetworkCycle()
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun performNetworkCycle() {
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

            // Send SMS and stop the loop.
            // The loop ONLY restarts once the hardware/network chain is finished.
            sendSmsAndWait(id, number, message, responseString)

        } catch (e: Exception) {
            Log.e("SMS_SYNC", "Poll empty or error: ${e.message}")
            // On error, we still treat it as a cycle. Restart poll calculation.
            enqueueNextPoll()
        }
    }

    private fun sendSmsAndWait(id: String, number: String, message: String, rawJson: String) {
        val smsManager = getSystemService(SmsManager::class.java)
        val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val sentIntent = PendingIntent.getBroadcast(
            this, requestCode,
            Intent(SMS_SENT_ACTION).apply { putExtra("sms_id", id) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val receivedId = intent?.getStringExtra("sms_id") ?: id
                context?.unregisterReceiver(this)

                // Back to the single thread to finalize status and schedule next
                taskExecutor.execute {
                    try {
                        if (resultCode == Activity.RESULT_OK) {
                            Log.d("SMS_SYNC", "Radio success. Updating lastSentTime.")
                            // Update both Global and Database
                            lastSentTime = System.currentTimeMillis()
                            dbHelper.updateLastSentTime()

                            performPatch(receivedId, "sent")
                        } else {
                            Log.e("SMS_SYNC", "Radio failure for $receivedId")
                            performPatch(receivedId, "error")
                        }
                    } finally {
                        // THE CRITICAL STEP: Only now do we schedule the next poll.
                        // enqueueNextPoll will check the lastSentTime and wait if necessary.
                        enqueueNextPoll()
                    }
                }
                broadcastUpdate(timeLabel, formatJSON(rawJson))
            }
        }

        registerReceiver(receiver, IntentFilter(SMS_SENT_ACTION), RECEIVER_EXPORTED)
        smsManager.sendTextMessage(number, null, message, sentIntent, null)
    }

    private fun performPatch(id: String, status: String) {
        try {
            val conn = URL("http://192.168.8.204:8080/api/v0/smsrequest?id=$id").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply { put("status", status) }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("SMS_NET", "PATCH Error: ${e.message}")
        }
    }

    // --- Helpers ---
    private fun broadcastUpdate(time: String, json: String) {
        sendBroadcast(Intent("UPDATE_SMS_UI").apply {
            putExtra("time", time)
            putExtra("json", json)
        })
    }

    private fun formatJSON(jsonString: String): String =
        try { JSONObject(jsonString).toString(4) } catch (e: Exception) { jsonString }

    private fun createNotification(): Notification {
        val channelId = "sms_channel_kt"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "SMS", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Automator Active")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        taskExecutor.shutdown()
        super.onDestroy()
    }
}