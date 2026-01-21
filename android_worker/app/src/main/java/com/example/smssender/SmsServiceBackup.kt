package com.example.smssender

import android.R
import android.app.*
import android.content.Intent
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


class SmsServiceBackup : Service() {
    private fun formatJSON(jsonString: String): String {
        val jsonObject = JSONObject(jsonString)
        return jsonObject.toString(4) // Indentation level is 4 spaces
    }
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val urlEndpoint = "http://192.168.8.204:8080/api/v0"
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.v("SMSSender", "SMSService started")
        startForeground(1, createNotification())

        // Start the very first request immediately
        scheduleNextTask(0)

        return START_STICKY
    }

    private fun scheduleNextTask(delayMs: Long) {
        scheduler.schedule({
            networkTask()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun networkTask() {
        var responseString = ""
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        try {
            // Get the message ready to send and grab it's ID
            val connection = URL(urlEndpoint+"/ready").openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // Increased timeout for stability
            responseString = connection.inputStream.bufferedReader().readText()

            val json = JSONObject(responseString)
            // Extracting the ID (Assuming it's inside 'smsrequest' or at the root)
            // Adjust the key path if 'id' is located elsewhere in your specific JSON structure
            val smsRequest = json.getJSONObject("smsrequest")
            val id = smsRequest.getString("id")
            val number = smsRequest.getString("number")
            val message = smsRequest.getString("message")
            Log.v("SMSSender", "JSON:${responseString}")
            val smsManager = getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(number, null, message, null, null)

            broadcastUpdate(currentTime, this.formatJSON(responseString))
            Log.d("SMS_KOTLIN", "Success. Waiting 5s...")

        } catch (e: Exception) {
            Log.v("SMSSender", "${responseString}")
            Log.e("SMS_KOTLIN", "Error: ${e.message}")
            broadcastUpdate(currentTime, "Error: ${e.message}")
        } finally {
            // THE KEY CHANGE:
            // Schedule the next hit only AFTER this one is finished.
            scheduleNextTask(5000)
        }
    }

    private fun performPostRequest(id: String) {
        val postUrl = urlEndpoint+"/smsrequest?id=\"$id\""
        try {
            val url = URL(postUrl)
            val postConn = url.openConnection() as HttpURLConnection
            postConn.requestMethod = "POST"
            postConn.connectTimeout = 5000

            // Trigger the request
            val responseCode = postConn.responseCode
            Log.d("SMSSender", "POST Sent for ID: $id. Response Code: $responseCode")

            postConn.disconnect()
        } catch (e: Exception) {
            Log.e("SMSSender", "POST Failed for ID $id: ${e.message}")
        }
    }

    private fun broadcastUpdate(time: String, json: String) {
        val updateIntent = Intent("UPDATE_SMS_UI").apply {
            putExtra("time", time)
            putExtra("json", json)
            setPackage(packageName)
        }
        sendBroadcast(updateIntent)
    }

    private fun createNotification(): Notification {
        val channelId = "sms_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "SMS Service", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Automator: 5s Delay Active")
            .setSmallIcon(R.drawable.ic_menu_send)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduler.shutdownNow()
        super.onDestroy()}

    /**override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        scheduler.scheduleAtFixedRate({ networkTask() }, 0, 1, TimeUnit.SECONDS)
        return START_STICKY
    }

    private fun networkTask() {
        try {
            val connection = URL(urlEndpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            val response = connection.inputStream.bufferedReader().readText()

            val json = JSONObject(response)
            val phone = json.getString("phone")
            val message = json.getString("text")

            // Send SMS
            //val smsManager = getSystemService(SmsManager::class.java)
            //smsManager.sendTextMessage(phone, null, message, null, null)

            // Update UI via Broadcast
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val updateIntent = Intent("UPDATE_SMS_UI").apply {
                putExtra("time", currentTime)
                putExtra("json", response)
                setPackage(packageName)
            }
            sendBroadcast(updateIntent)

        } catch (e: Exception) {
            val errorIntent = Intent("UPDATE_SMS_UI").apply {
                putExtra("time", "Error at " + SimpleDateFormat("HH:mm:ss").format(Date()))
                putExtra("json", e.message)
                setPackage(packageName)
            }
            sendBroadcast(errorIntent)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "sms_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Service", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Automator Active")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scheduler.shutdown(); super.onDestroy() }**/
    /**
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        // Execute every 1 second
        scheduler.scheduleAtFixedRate({
            networkTask()
        }, 0, 1, TimeUnit.SECONDS)

        return START_STICKY
    }

    private fun networkTask() {
        try {
            val connection = URL(urlEndpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 800
            val response = connection.inputStream.bufferedReader().readText()

            val json = JSONObject(response)
            val phone = json.getString("phone")
            val message = json.getString("text")

            val smsManager = getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phone, null, message, null, null)

            Log.d("SMS_KOTLIN", "Sent to \$phone")
        } catch (e: Exception) {
            Log.e("SMS_KOTLIN", "Error: \${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        val channelId = "sms_channel_kt"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "SMS Service", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kotlin Automator Running")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduler.shutdown()
        super.onDestroy()
    }**/
}