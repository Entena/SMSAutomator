package com.example.smssender

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "sms_limit.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value LONG)")
        // Initialize the last_sent_time to 0
        val values = ContentValues().apply {
            put("key", "last_sent_time")
            put("value", 0L)
        }
        db.insert("metadata", null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun updateLastSentTime() {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("value", System.currentTimeMillis()) }
        db.update("metadata", values, "key = ?", arrayOf("last_sent_time"))
        db.close()
    }

    fun getLastSentTime(): Long {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT value FROM metadata WHERE key = ?", arrayOf("last_sent_time"))
        var time = 0L
        if (cursor.moveToFirst()) {
            time = cursor.getLong(0)
        }
        cursor.close()
        db.close()
        return time
    }
}