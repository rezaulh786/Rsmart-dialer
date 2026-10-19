package com.example.smartdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    data class CallEntry(val display: String, val number: String)

    private val allRecents = mutableListOf<CallEntry>()
    private val shownRecents = mutableListOf<CallEntry>()
    private val allContacts = mutableListOf<CallEntry>()
    private val shownContacts = mutableListOf<CallEntry>()
    private var recordingFiles = listOf<File>()
    private var mediaPlayer: MediaPlayer? = null
    private var isSpeakerOn = false

    private lateinit var listRecents: ListView
    private lateinit var listContacts: ListView
    private lateinit var listRecordings: ListView
    private lateinit var tvDialNumber: TextView
    private lateinit var etSearch: EditText

    private lateinit var layoutRecents: LinearLayout
    private lateinit var layoutContacts: LinearLayout
    private lateinit var layoutKeypad: LinearLayout
    private lateinit var layoutRecordings: LinearLayout
    private lateinit var layoutInCall: LinearLayout

    private lateinit var tabRecents: Button
    private lateinit var tabContacts: Button
    private lateinit var tabKeypad: Button
    private lateinit var tabRecordings: Button

    private lateinit var tvCallStatus: TextView
    private lateinit var tvCallerNumber: TextView
    private lateinit var btnAnswerCall: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnEndCall: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTabs()
        setupKeypad()
        setupSearch()
        setupInCallUI()

        checkPermissions()
        requestDefaultDialerRole()
    }

    override fun onResume() {
        super.onResume()
        loadAllData()
        CallService.currentCall?.let {
            @Suppress("DEPRECATION")
            updateInCallUI(it, it.state)
        }
    }

    private fun initViews() {
        etSearch = findViewById(R.id.etSearch)
        listRecents = findViewById(R.id.listRecents)
        listContacts = findViewById(R.id.listContacts)
        listRecordings = findViewById(R.id.listRecordings)
        tvDialNumber = findViewById(R.id.tvDialNumber)

        layoutRecents = findViewById(R.id.layoutRecents)
        layoutContacts = findViewById(R.id.layoutContacts)
        layoutKeypad = findViewById(R.id.layoutKeypad)
        layoutRecordings = findViewById(R.id.layoutRecordings)
        layoutInCall = findViewById(R.id.layoutInCall)

        tabRecents = findViewById(R.id.tabRecents)
        tabContacts = findViewById(R.id.tabContacts)
        tabKeypad = findViewById(R.id.tabKeypad)
        tabRecordings = findViewById(R.id.tabRecordings)

        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvCallerNumber = findViewById(R.id.tvCallerNumber)
        btnAnswerCall = findViewById(R.id.btnAnswerCall)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnEndCall = findViewById(R.id.btnEndCall)

        listRecents.setOnItemClickListener { _, _, pos, _ ->
            if (pos < shownRecents.size) makeCall(shownRecents[pos].number)
        }
        listContacts.setOnItemClickListener { _, _, pos, _ ->
            if (pos < shownContacts.size) makeCall(shownContacts[pos].number)
        }
        listRecordings.setOnItemClickListener { _, _, pos, _ ->
            if (pos < recordingFiles.size) playRecording(recordingFiles[pos])
        }
    }

    private fun setupTabs() {
        tabRecents.setOnClickListener { switchTab(1) }
        tabContacts.setOnClickListener { switchTab(2) }
        tabKeypad.setOnClickListener { switchTab(3) }
        tabRecordings.setOnClickListener {
            loadRecordings()
            switchTab(4)
        }
    }

    private fun switchTab(tabIndex: Int) {
        layoutRecents.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        layoutContacts.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        layoutKeypad.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
        layoutRecordings.visibility = if (tabIndex == 4) View.VISIBLE else View.GONE

        val activeBg = ColorStateList.valueOf(Color.parseColor("#E8F0FE"))
        val inactiveBg = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
        val activeText = Color.parseColor("#1A73E8")
        val inactiveText = Color.parseColor("#5F6368")

        listOf(tabRecents, tabContacts, tabKeypad, tabRecordings).forEachIndexed { i, btn ->
            btn.backgroundTintList = if (i + 1 == tabIndex) activeBg else inactiveBg
            btn.setTextColor(if (i + 1 == tabIndex) activeText else inactiveText)
        }
    }

    private fun setupKeypad() {
        val buttons = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9", R.id.btnStar to "*", R.id.btnHash to "#"
        )
        buttons.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                tvDialNumber.text = tvDialNumber.text.toString() + digit
            }
        }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            val cur = tvDialNumber.text.toString()
            if (cur.isNotEmpty()) tvDialNumber.text = cur.dropLast(1)
        }
        findViewById<Button>(R.id.btnBackspace).setOnLongClickListener {
            tvDialNumber.text = ""
            true
        }
        findViewById<Button>(R.id.btnCall).setOnClickListener {
            val num = tvDialNumber.text.toString().trim()
            if (num.isNotEmpty()) makeCall(num)
            else Toast.makeText(this, "অনুগ্রহ করে নম্বর তুলুন", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterLists(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterLists(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        shownRecents.clear()
        shownRecents.addAll(if (q.isEmpty()) allRecents else allRecents.filter {
            it.display.lowercase(Locale.getDefault()).contains(q) || it.number.contains(q)
        })
        listRecents.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shownRecents.map { it.display })

        shownContacts.clear()
        shownContacts.addAll(if (q.isEmpty()) allContacts else allContacts.filter {
            it.display.lowercase(Locale.getDefault()).contains(q) || it.number.contains(q)
        })
        listContacts.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shownContacts.map { it.display })
    }

    private fun setupInCallUI() {
        btnAnswerCall.setOnClickListener { CallService.answerCall() }
        btnEndCall.setOnClickListener { CallService.endCall() }
        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            CallService.toggleSpeaker(isSpeakerOn)
            btnSpeaker.text = if (isSpeakerOn) "🔊 স্পিকার (চালু)" else "🔊 স্পিকার"
        }
        CallService.onCallStateChanged = { call, state ->
            runOnUiThread { updateInCallUI(call, state) }
        }
    }

    private fun updateInCallUI(call: Call?, state: Int) {
        if (call == null || state == Call.STATE_DISCONNECTED) {
            layoutInCall.visibility = View.GONE
            isSpeakerOn = false
            btnSpeaker.text = "🔊 স্পিকার"
            loadAllData()
            return
        }
        layoutInCall.visibility = View.VISIBLE
        tvCallerNumber.text = call.details.handle?.schemeSpecificPart ?: "অজানা নম্বর"
        when (state) {
            Call.STATE_RINGING -> {
                tvCallStatus.text = "📲 ইনকামিং কল আসছে..."
                btnAnswerCall.visibility = View.VISIBLE
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                tvCallStatus.text = "📞 কল যাচ্ছে..."
                btnAnswerCall.visibility = View.GONE
            }
            Call.STATE_ACTIVE -> {
                tvCallStatus.text = "✅ কথা চলছে (অটো রেকর্ড হচ্ছে...)"
                btnAnswerCall.visibility = View.GONE
            }
            else -> tvCallStatus.text = "কল স্ট্যাটাস: $state"
        }
    }

    private fun makeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } else {
            Toast.makeText(this, "কল করার পারমিশন দিন", Toast.LENGTH_SHORT).show()
            checkPermissions()
        }
    }

    private fun loadAllData() {
        loadCallLogs()
        loadContacts()
        loadRecordings()
    }

    private fun loadCallLogs() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return
        allRecents.clear()
        val cursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null, "${CallLog.Calls.DATE} DESC"
        )
        cursor?.use {
            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            var count = 0
            while (it.moveToNext() && count < 100) {
                val number = it.getString(numIdx) ?: ""
                val name = it.getString(nameIdx)
                val icon = when (it.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> "↙️ ইনকামিং"
                    CallLog.Calls.OUTGOING_TYPE -> "↗️ আউটগোয়িং"
                    CallLog.Calls.MISSED_TYPE -> "❌ মিসড কল"
                    else -> "📞 কল"
                }
                val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(it.getLong(dateIdx)))
                val title = if (!name.isNullOrEmpty()) "$name ($number)" else number
                allRecents.add(CallEntry("$title\n$icon • $timeStr", number))
                count++
            }
        }
        filterLists(etSearch.text.toString())
    }

    private fun loadContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        allContacts.clear()
        val seen = mutableSetOf<String>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "নামহীন"
                val number = (it.getString(numIdx) ?: "").replace(" ", "").replace("-", "")
                val key = "$name-$number"
                if (number.isNotEmpty() && !seen.contains(key)) {
                    seen.add(key)
                    allContacts.add(CallEntry("👤 $name\n📞 $number", number))
                }
            }
        }
        filterLists(etSearch.text.toString())
    }

    private fun loadRecordings() {
        val folder = File(getExternalFilesDir(null), "CallRecordings")
        if (folder.exists()) {
            recordingFiles = folder.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            val names = if (recordingFiles.isNotEmpty()) {
                recordingFiles.map { "🎙️ ${it.name}\n📅 সাইজ: ${it.length() / 1024} KB (শুনতে ক্লিক করুন)" }
            } else {
                listOf("এখনো কোনো কল রেকর্ড জমা হয়নি। কল কথা বললে স্বয়ংক্রিয়ভাবে এখানে জমা হবে।")
            }
            listRecordings.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        }
    }

    private fun playRecording(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "▶️ বাজানো হচ্ছে: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ফাইলটি বাজানো যাচ্ছে না: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                @Suppress("DEPRECATION")
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 101)
            }
        } else {
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            if (tm.defaultDialerPackage != packageName) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 101)
            }
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 102)
        } else {
            loadAllData()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadAllData()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
// END OF FILE
