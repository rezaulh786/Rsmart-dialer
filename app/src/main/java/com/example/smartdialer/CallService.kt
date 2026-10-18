package com.example.smartdialer

import android.telecom.Call
import android.telecom.InCallService

class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                
                // কল রিসিভ হওয়া মাত্র (STATE_ACTIVE) অটোমেটিক রেকর্ডিং শুরু হবে
                if (state == Call.STATE_ACTIVE) {
                    val number = call.details.handle?.schemeSpecificPart
                    CallRecorder.startRecording(applicationContext, number)
                } 
                // কল কেটে দেওয়া মাত্র (STATE_DISCONNECTED) রেকর্ডিং বন্ধ হয়ে সেভ হবে
                else if (state == Call.STATE_DISCONNECTED) {
                    CallRecorder.stopRecording()
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallRecorder.stopRecording()
    }
}
