package com.company.phonedirectory

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.TransportType

/**
 * SIP calling screen for HiPath Directory.
 * Uses Liblinphone for SIP signalling and native audio.
 */
class SIPActivity : Activity() {
    private lateinit var username: EditText
    private lateinit var domain: EditText
    private lateinit var password: EditText
    private lateinit var port: EditText
    private lateinit var number: EditText
    private lateinit var status: TextView
    private lateinit var callState: TextView
    private lateinit var transport: Spinner
    private lateinit var connectButton: Button
    private lateinit var callButton: Button
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button
    private var muted = false
    private var speaker = false

    private val prefs by lazy { getSharedPreferences("sip_account", MODE_PRIVATE) }
    private val cyan = Color.rgb(53, 215, 242)
    private val green = Color.rgb(34, 197, 139)
    private val red = Color.rgb(239, 83, 80)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSavedAccount()
        number.setText(intent.getStringExtra("dial_number").orEmpty())
        SIPEngine.listener = { message, incoming ->
            runOnUiThread {
                status.text = message
                callState.text = if (incoming) "Incoming call — answer or reject" else message
                acceptButton.visibility = if (incoming) View.VISIBLE else View.GONE
                rejectButton.visibility = if (incoming || SIPEngine.activeCall != null) View.VISIBLE else View.GONE
            }
        }
        SIPEngine.statusListener = { message -> runOnUiThread { status.text = message } }
        refreshState()
        connectButton.setOnClickListener { connectAccount() }
        callButton.setOnClickListener { makeCall() }
        acceptButton.setOnClickListener {
            SIPEngine.activeCall?.let {
                try { it.accept(); callState.text = "Connecting..." }
                catch (e: Exception) { showMessage("Could not answer: ${e.message}") }
            }
        }
        rejectButton.setOnClickListener {
            try { SIPEngine.activeCall?.terminate() } catch (_: Exception) {}
            callState.text = "Call ended"
            acceptButton.visibility = View.GONE
            rejectButton.visibility = View.GONE
        }
        muteButton.setOnClickListener {
            muted = !muted
            SIPEngine.activeCall?.microphoneMuted = muted
            muteButton.text = if (muted) "Unmute microphone" else "Mute microphone"
        }
        speakerButton.setOnClickListener {
            speaker = !speaker
            SIPEngine.setSpeaker(speaker)
            speakerButton.text = if (speaker) "Speaker: ON" else "Speaker: OFF"
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(20))
            setBackgroundColor(Ui.background(this@SIPActivity))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "SIP CALLING"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })
        root.addView(TextView(this).apply {
            text = "VoIP account"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@SIPActivity))
            setPadding(0, dp(5), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Connect a SIP extension to place and receive audio calls."
            textSize = 14f
            setTextColor(Ui.muted(this@SIPActivity))
            setPadding(0, 0, 0, dp(14))
        })

        username = field(root, "Username / Extension", "e.g. 861")
        domain = field(root, "SIP server / Domain", "e.g. 192.168.1.247")
        password = field(root, "SIP password", "Account password", true)
        port = field(root, "SIP port", "5060")
        transport = Spinner(this)
        transport.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("UDP", "TCP", "TLS"))
        root.addView(label("Transport"))
        root.addView(transport, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })

        connectButton = button("Save account & Connect", cyan, Color.rgb(8, 24, 38))
        root.addView(connectButton, matchParams(10))
        status = label("Not connected")
        status.textSize = 14f
        status.setTextColor(green)
        status.setPadding(0, dp(10), 0, dp(12))
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "Dial pad"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@SIPActivity))
            setPadding(0, dp(6), 0, dp(8))
        })
        number = field(root, "Extension / SIP number", "e.g. 100 or 200")
        callButton = button("☎  Call", green, Color.rgb(5, 30, 23))
        root.addView(callButton, matchParams(8))
        callState = label("Ready")
        callState.setPadding(0, dp(10), 0, dp(8))
        root.addView(callState)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        muteButton = button("Mute microphone", Color.rgb(40, 52, 73), Color.WHITE)
        speakerButton = button("Speaker: OFF", Color.rgb(40, 52, 73), Color.WHITE)
        controls.addView(muteButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        controls.addView(speakerButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(controls, matchParams(8))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        acceptButton = button("Answer", green, Color.rgb(5, 30, 23))
        rejectButton = button("Hang up / Reject", red, Color.WHITE)
        acceptButton.visibility = View.GONE
        rejectButton.visibility = View.GONE
        actions.addView(acceptButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        actions.addView(rejectButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(actions, matchParams(8))

        root.addView(TextView(this).apply {
            text = "Note: incoming calls while the app is closed may require server push/foreground-service setup. Your SIP server must permit your chosen transport and audio codecs."
            textSize = 12f
            setTextColor(Ui.muted(this@SIPActivity))
            setPadding(0, dp(16), 0, dp(8))
        })
    }

    private fun field(parent: LinearLayout, title: String, hintText: String, isPassword: Boolean = false): EditText {
        parent.addView(label(title))
        val edit = EditText(this).apply {
            hint = hintText
            textSize = 15f
            setSingleLine(true)
            setTextColor(Ui.text(this@SIPActivity))
            setHintTextColor(Ui.muted(this@SIPActivity))
            background = rounded(if (Ui.isDark(this@SIPActivity)) Color.rgb(30, 41, 59) else Color.WHITE, 12)
            setPadding(dp(13), 0, dp(13), 0)
            inputType = if (isPassword) 129 else android.text.InputType.TYPE_CLASS_TEXT
        }
        parent.addView(edit, LinearLayout.LayoutParams(-1, dp(49)).apply { bottomMargin = dp(9) })
        return edit
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Ui.text(this@SIPActivity))
    }

    private fun button(textValue: String, color: Int, textColor: Int) = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        background = rounded(color, 13)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun matchParams(top: Int) = LinearLayout.LayoutParams(-1, dp(50)).apply {
        topMargin = dp(top)
    }

    private fun loadSavedAccount() {
        username.setText(prefs.getString("username", ""))
        domain.setText(prefs.getString("domain", ""))
        password.setText(prefs.getString("password", ""))
        port.setText(prefs.getString("port", "5060"))
        val savedTransport = prefs.getString("transport", "UDP")
        val index = listOf("UDP", "TCP", "TLS").indexOf(savedTransport)
        transport.setSelection(if (index >= 0) index else 0)
    }

    private fun hasSavedAccount() = prefs.getString("username", "").orEmpty().isNotBlank() &&
        prefs.getString("domain", "").orEmpty().isNotBlank()

    private fun connectAccount() {
        val u = username.text.toString().trim()
        val d = domain.text.toString().trim()
        val pw = password.text.toString()
        val p = port.text.toString().trim().ifBlank { "5060" }
        val t = transport.selectedItem.toString()
        if (u.isBlank() || d.isBlank() || pw.isBlank()) {
            showMessage("Enter username, server and password first.")
            return
        }
        val portNumber = p.toIntOrNull()
        if (portNumber == null || portNumber !in 1..65535) {
            showMessage("Enter a valid SIP port.")
            return
        }
        prefs.edit().putString("username", u).putString("domain", d)
            .putString("password", pw).putString("port", p)
            .putString("transport", t).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 501)
        }
        try {
            SIPEngine.connect(applicationContext, u, d, pw, p.toInt(), t)
            status.text = "Connecting to $d:$p ($t)…"
            callState.text = "Waiting for SIP registration"
        } catch (e: Exception) {
            status.text = "Connection failed"
            showMessage(e.message ?: "Could not start SIP")
        }
    }

    private fun makeCall() {
        val target = number.text.toString().trim()
        if (target.isBlank()) {
            showMessage("Enter an extension or SIP number.")
            return
        }
        if (SIPEngine.core == null) {
            showMessage("Save account & Connect first.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 501)
            return
        }
        try {
            SIPEngine.call(target)
            callState.text = "Calling $target…"
            rejectButton.visibility = View.VISIBLE
        } catch (e: Exception) {
            showMessage("Call failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun refreshState() {
        status.text = SIPEngine.status
        val call = SIPEngine.activeCall
        if (call != null) {
            callState.text = "Call state: ${call.state}"
            rejectButton.visibility = View.VISIBLE
            acceptButton.visibility = if (call.state == Call.State.IncomingReceived) View.VISIBLE else View.GONE
        }
    }

    private fun showMessage(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (SIPEngine.listener != null) SIPEngine.listener = null
        super.onDestroy()
    }
}

private object SIPEngine {
    var core: Core? = null
    var activeCall: Call? = null
    var status: String = "Not connected"
    var listener: ((String, Boolean) -> Unit)? = null
    var statusListener: ((String) -> Unit)? = null
    private var speakerOn = false

    @Synchronized
    fun connect(context: Context, username: String, domain: String, password: String, port: Int, transport: String) {
        try {
            core?.stop()
        } catch (_: Exception) {}
        core = null
        activeCall = null
        val factory = Factory.instance()
        val newCore = factory.createCore(null, null, context.applicationContext)
        newCore.isVideoCaptureEnabled = false
        newCore.isVideoDisplayEnabled = false
        newCore.addListener(object : CoreListenerStub() {
            override fun onRegistrationStateChanged(core: Core, proxyConfig: org.linphone.core.ProxyConfig, state: org.linphone.core.RegistrationState, message: String) {
                status = when (state) {
                    org.linphone.core.RegistrationState.Ok -> "Registered • $username@$domain"
                    org.linphone.core.RegistrationState.Progress -> "Registering…"
                    org.linphone.core.RegistrationState.Failed -> "Registration failed: $message"
                    org.linphone.core.RegistrationState.Cleared -> "Disconnected"
                    else -> "SIP: $state"
                }
                statusListener?.invoke(status)
                listener?.invoke(status, false)
            }

            override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
                activeCall = if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) null else call
                val incoming = state == Call.State.IncomingReceived
                val label = when (state) {
                    Call.State.IncomingReceived -> "Incoming call from ${call.remoteAddress?.username ?: "unknown"}"
                    Call.State.OutgoingInit -> "Starting call…"
                    Call.State.OutgoingProgress -> "Calling…"
                    Call.State.OutgoingRinging -> "Ringing…"
                    Call.State.Connected -> "Connected"
                    Call.State.StreamsRunning -> "Call in progress"
                    Call.State.End -> "Call ended"
                    Call.State.Error -> "Call error: $message"
                    Call.State.Released -> "Call finished"
                    else -> "Call: $state"
                }
                listener?.invoke(label, incoming)
                statusListener?.invoke(label)
            }
        })

        val identity = factory.createAddress("sip:$username@$domain")
            ?: throw IllegalArgumentException("Invalid SIP identity or domain")
        val server = factory.createAddress("sip:$domain:$port;transport=${transport.lowercase()}")
            ?: throw IllegalArgumentException("Invalid SIP server address")
        val auth = factory.createAuthInfo(username, null, password, null, null, domain)
        newCore.addAuthInfo(auth)
        val params = newCore.createAccountParams()
        params.identityAddress = identity
        params.serverAddress = server
        params.isRegisterEnabled = true
        val account: Account = newCore.createAccount(params)
        newCore.addAccount(account)
        newCore.defaultAccount = account
        newCore.start()
        core = newCore
        status = "SIP engine started — registering…"
        statusListener?.invoke(status)
        listener?.invoke(status, false)
    }

    fun call(target: String) {
        val c = core ?: throw IllegalStateException("SIP account is not connected")
        val clean = target.trim()
        val uri = if (clean.startsWith("sip:", true)) clean else {
            val d = c.defaultAccount?.params?.identityAddress?.domain ?: ""
            if (d.isBlank()) throw IllegalStateException("SIP domain is missing")
            "sip:$clean@$d"
        }
        val created = c.invite(uri)
        if (created == null) throw IllegalStateException("The SIP engine rejected the call request")
        activeCall = created
    }

    fun setSpeaker(enabled: Boolean) {
        speakerOn = enabled
        val c = core ?: return
        val device = c.audioDevices.firstOrNull {
            if (enabled) it.type == org.linphone.core.AudioDevice.Type.Speaker
            else it.type == org.linphone.core.AudioDevice.Type.Earpiece
        }
        if (device != null) {
            activeCall?.outputAudioDevice = device
            c.defaultOutputAudioDevice = device
        }
    }
}
