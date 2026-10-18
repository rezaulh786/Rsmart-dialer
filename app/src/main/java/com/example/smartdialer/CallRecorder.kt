package com.example.smartdialer

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallRecorder {
    private var recorder: MediaRecorder? = null
    private var isRecording = false

    fun startRecording(context: Context, phoneNumber: String?) {
        if (isRecording) return

        try {
            val folder = File(context.getExternalFilesDir(null), "CallRecordings")
            if (!folder.exists()) folder.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cleanNumber = phoneNumber?.replace("+", "") ?: "Unknown"
            val file = File(folder, "Call_${cleanNumber}_$timestamp.m4a")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                // VOICE_COMMUNICATION মেথড যা দুই পাশের অডিও বাইপাস করে ক্যাপচার করতে সাহায্য করে
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
                isRecording = true
                Log.d("CallRecorder", "Auto Recording started: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error starting recording: ${e.message}")
            // ফলব্যাক: সাধারণ MIC সোর্স দিয়ে চেষ্টা করা
            fallbackMicRecording(context)
        }
    }

    private fun fallbackMicRecording(context: Context) {
        try {
            val folder = File(context.getExternalFilesDir(null), "CallRecordings")
            val file = File(folder, "Fallback_Call_${System.currentTimeMillis()}.m4a")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
                isRecording = true
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "Fallback error: ${e.message}")
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            Log.d("CallRecorder", "Recording saved successfully.")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error stopping recording: ${e.message}")
        }
    }
}
