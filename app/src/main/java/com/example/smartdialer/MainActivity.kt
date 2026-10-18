package com.example.smartdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        requestDefaultDialerRole()

        val etNumber = findViewById<EditText>(R.id.etNumber)
        val btnCall = findViewById<Button>(R.id.btnCall)
        val tvRecordings = findViewById<TextView>(R.id.tvRecordings)

        btnCall.setOnClickListener {
            val number = etNumber.text.toString()
            if (number.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_CALL)
                intent.data = android.net.Uri.parse("tel:$number")
                startActivity(intent)
            }
        }

        // সেভ করা রেকর্ডিংয়ের তালিকা স্ক্রিনে দেখাবে
        showRecordings(tvRecordings)
    }

    private fun showRecordings(tv: TextView) {
        val folder = File(getExternalFilesDir(null), "CallRecordings")
        if (folder.exists()) {
            val files = folder.listFiles()
            if (!files.isNullOrEmpty()) {
                val text = StringBuilder("📁 Auto Saved Recordings (${files.size}):\n\n")
                files.forEach { text.append("• ").append(it.name).append("\n") }
                tv.text = text.toString()
            } else {
                tv.text = "রেকর্ডিং ফোল্ডার প্রস্তুত। কল করলে স্বয়ংক্রিয়ভাবে এখানে ফাইল জমা হবে।"
            }
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, 101)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )
        val list = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (list.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, list.toTypedArray(), 102)
        }
    }
}
