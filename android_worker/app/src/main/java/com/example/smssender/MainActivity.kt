package com.example.smssender

import android.Manifest
import android.content.*
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.util.Log


class MainActivity: AppCompatActivity() {
    private lateinit var txtTimestamp: TextView
    private lateinit var txtJson: TextView

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            txtTimestamp.text = intent?.getStringExtra("time")
            txtJson.text = intent?.getStringExtra("json")
            Log.v("SMSSender", "${txtTimestamp.text}:{$txtJson.text}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtTimestamp = findViewById(R.id.txtTimestamp)
        txtJson = findViewById(R.id.txtJson)
        val btnStart = findViewById<Button>(R.id.btnStart)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS), 1)

        btnStart.setOnClickListener {
            startForegroundService(Intent(this, SmsService::class.java))
            Log.v("SMSSender", "BUTTON CLICKED")
        }

        registerReceiver(updateReceiver, IntentFilter("UPDATE_SMS_UI"), RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }
    /**
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btn = Button(this).apply {
            text = "Start 1s Kotlin Loop"
        }
        setContentView(btn)

        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.POST_NOTIFICATIONS
        ), 1)

        btn.setOnClickListener {
            val intent = Intent(this, SmsService::class.java)
            startForegroundService(intent)
        }
    }**/
}