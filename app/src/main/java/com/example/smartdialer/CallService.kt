package com.example.smartdialer

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile

class CallService : InCallService() {

    companion object {
        var currentCall: Call? = null
        var instance: CallService? = null
        var onCallStateChanged: ((Call?, Int) -> Unit)? = null

        fun answerCall() {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun endCall() {
            currentCall?.disconnect()
        }

        @Suppress("DEPRECATION")
        fun toggleSpeaker(enable: Boolean) {
            val route = if (enable) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            instance?.setAudioRoute(route)
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            onCallStateChanged?.invoke(call, state)

            if (state == Call.STATE_ACTIVE) {
                val number = call.details.handle?.schemeSpecificPart
                CallRecorder.startRecording(applicationContext, number)
            } else if (state == Call.STATE_DISCONNECTED) {
                CallRecorder.stopRecording()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)

        val state = call.state
        onCallStateChanged?.invoke(call, state)

        if (state == Call.STATE_ACTIVE) {
            val number = call.details.handle?.schemeSpecificPart
            CallRecorder.startRecording(applicationContext, number)
        }

        // কল আসলে বা কল দিলে সাথে সাথে কলিং স্ক্রিন ওপেন করা
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallRecorder.stopRecording()
        if (currentCall == call) {
            currentCall = null
        }
        onCallStateChanged?.invoke(null, Call.STATE_DISCONNECTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
