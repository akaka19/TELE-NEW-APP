
package com.company.phonedirectory

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory

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

    private val prefs by lazy {
        getSharedPreferences("sip_account", MODE_PRIVATE)
    }

    private val cyan = Color.rgb(53, 215, 242)
    private val green = Color.rgb(34, 197, 139)
    private val red = Color.rgb(239, 83, 80)

    private val darkSurface = Color.rgb(20, 30, 48)
    private val darkField = Color.rgb(29, 41, 61)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        loadSavedAccount()

        number.setText(
            intent.getStringExtra("dial_number").orEmpty()
        )

        SIPEngine.listener = { message, incoming ->
            runOnUiThread {
                status.text = message

                callState.text = if (incoming) {
                    "Incoming call — answer or reject"
                } else {
                    message
                }

                acceptButton.visibility =
                    if (incoming) View.VISIBLE else View.GONE

                rejectButton.visibility =
                    if (incoming || SIPEngine.activeCall != null) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
        }

        SIPEngine.statusListener = { message ->
            runOnUiThread {
                status.text = message
            }
        }

        refreshState()

        connectButton.setOnClickListener {
            connectAccount()
        }

        callButton.setOnClickListener {
            makeCall()
        }

        acceptButton.setOnClickListener {
            SIPEngine.activeCall?.let { call ->
                try {
                    call.accept()
                    callState.text = "Connecting..."
                } catch (e: Exception) {
                    showMessage("Could not answer: ${e.message}")
                }
            }
        }

        rejectButton.setOnClickListener {
            try {
                SIPEngine.activeCall?.terminate()
            } catch (_: Exception) {
            }

            callState.text = "Call ended"
            acceptButton.visibility = View.GONE
            rejectButton.visibility = View.GONE
        }

        muteButton.setOnClickListener {
            muted = !muted
            SIPEngine.activeCall?.microphoneMuted = muted

            muteButton.text = if (muted) {
                "Unmute microphone"
            } else {
                "Mute microphone"
            }

            muteButton.background = rounded(
                if (muted) cyan else surfaceColor(),
                13
            )

            muteButton.setTextColor(
                if (muted) Color.rgb(8, 24, 38) else mainTextColor()
            )
        }

        speakerButton.setOnClickListener {
            speaker = !speaker
            SIPEngine.setSpeaker(speaker)

            speakerButton.text = if (speaker) {
                "Speaker: ON"
            } else {
                "Speaker: OFF"
            }

            speakerButton.background = rounded(
                if (speaker) cyan else surfaceColor(),
                13
            )

            speakerButton.setTextColor(
                if (speaker) Color.rgb(8, 24, 38) else mainTextColor()
            )
        }
    }

    // ---------------------------------------------------------
    // MODERN UI
    // ---------------------------------------------------------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            setBackgroundColor(Ui.background(this@SIPActivity))
        }

        val scroll = ScrollView(this).apply {
            fillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }

        scroll.addView(
            root,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scroll)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "☎"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            background = rounded(surfaceColor(), 16)
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(56), dp(56))
        )

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(4), 0)
        }

        heading.addView(TextView(this).apply {
            text = "HiPath"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainTextColor())
        })

        heading.addView(TextView(this).apply {
            text = "VOICE COMMUNICATIONS"
            textSize = 10f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })

        header.addView(
            heading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(TextView(this).apply {
            text = "SIP"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(cyan)
            background = rounded(surfaceColor(), 10)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        })

        root.addView(header)

        // Page title
        root.addView(TextView(this).apply {
            text = "SIP Calling"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainTextColor())
            setPadding(0, dp(25), 0, dp(5))
        })

        root.addView(TextView(this).apply {
            text = "Configure your extension and manage voice calls."
            textSize = 14f
            setTextColor(mutedTextColor())
        })

        // Account card
        val accountCard = createCard()

        accountCard.addView(sectionLabel("SIP ACCOUNT"))

        accountCard.addView(TextView(this).apply {
            text = "Connection settings"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainTextColor())
            setPadding(0, dp(6), 0, dp(16))
        })

        username = field(
            accountCard,
            "Username / Extension",
            "e.g. 861"
        )

        domain = field(
            accountCard,
            "SIP server / Domain",
            "e.g. 192.168.1.247"
        )

        password = field(
            accountCard,
            "SIP password",
            "Account password",
            true
        )

        port = field(
            accountCard,
            "SIP port",
            "5060"
        )

        accountCard.addView(label("Transport"))

        transport = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SIPActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("UDP", "TCP", "TLS")
            )

            background = rounded(fieldColor(), 12)
            setPadding(dp(10), 0, dp(10), 0)
        }

        accountCard.addView(
            transport,
            LinearLayout.LayoutParams(-1, dp(49)).apply {
                topMargin = dp(7)
                bottomMargin = dp(8)
            }
        )

        connectButton = button(
            "Save account & Connect",
            cyan,
            Color.rgb(8, 24, 38)
        )

        accountCard.addView(
            connectButton,
            fullButtonParams(12)
        )

        status = TextView(this).apply {
            text = "Not connected"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            background = rounded(fieldColor(), 12)
            setPadding(dp(13), dp(14), dp(13), dp(14))
        }

        accountCard.addView(
            status,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            }
        )

        root.addView(
            accountCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(23)
            }
        )

        // Dial card
        val callCard = createCard()

        callCard.addView(sectionLabel("CALLING"))

        callCard.addView(TextView(this).apply {
            text = "Dial extension"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainTextColor())
            setPadding(0, dp(6), 0, dp(14))
        })

        number = field(
            callCard,
            "Extension / SIP number",
            "e.g. 100 or 200"
        )

        callButton = button(
            "☎   Call",
            green,
            Color.rgb(5, 30, 23)
        )

        callCard.addView(
            callButton,
            fullButtonParams(10)
        )

        callState = TextView(this).apply {
            text = "Ready to call"
            textSize = 14f
            setTextColor(mutedTextColor())
            background = rounded(fieldColor(), 12)
            setPadding(dp(13), dp(14), dp(13), dp(14))
        }

        callCard.addView(
            callState,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            }
        )

        // Mute and speaker controls
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        muteButton = button(
            "Mute microphone",
            fieldColor(),
            mainTextColor()
        )

        speakerButton = button(
            "Speaker: OFF",
            fieldColor(),
            mainTextColor()
        )

        controls.addView(
            muteButton,
            LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                marginEnd = dp(5)
            }
        )

        controls.addView(
            speakerButton,
            LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                marginStart = dp(5)
            }
        )

        callCard.addView(
            controls,
            fullButtonParams(10)
        )

        // Answer and hang up
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        acceptButton = button(
            "Answer",
            green,
            Color.rgb(5, 30, 23)
        ).apply {
            visibility = View.GONE
        }

        rejectButton = button(
            "Hang up / Reject",
            red,
            Color.WHITE
        ).apply {
            visibility = View.GONE
        }

        actions.addView(
            acceptButton,
            LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                marginEnd = dp(5)
            }
        )

        actions.addView(
            rejectButton,
            LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                marginStart = dp(5)
            }
        )

        callCard.addView(
            actions,
            fullButtonParams(10)
        )

        root.addView(
            callCard,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(16)
            }
        )

        root.addView(TextView(this).apply {
            text = "HIPATH DIRECTORY  •  VOICE & EXTENSIONS"
            textSize = 10f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(mutedTextColor())
            setPadding(0, dp(24), 0, dp(8))
        })
    }

    // ---------------------------------------------------------
    // UI HELPERS
    // ---------------------------------------------------------

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = rounded(surfaceColor(), 18)
            elevation = dp(2).toFloat()
        }
    }

    private fun sectionLabel(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 11f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        }
    }

    private fun field(
        parent: LinearLayout,
        title: String,
        hintText: String,
        isPassword: Boolean = false
    ): EditText {
        parent.addView(label(title))

        val edit = EditText(this).apply {
            hint = hintText
            textSize = 15f
            setSingleLine(true)
            setTextColor(mainTextColor())
            setHintTextColor(mutedTextColor())
            background = rounded(fieldColor(), 12)
            setPadding(dp(13), 0, dp(13), 0)

            inputType = if (isPassword) {
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        parent.addView(
            edit,
            LinearLayout.LayoutParams(-1, dp(49)).apply {
                topMargin = dp(7)
                bottomMargin = dp(12)
            }
        )

        return edit
    }

    private fun label(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mainTextColor())
        }
    }

    private fun button(
        textValue: String,
        color: Int,
        textColor: Int
    ): Button {
        return Button(this).apply {
            text = textValue
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = rounded(color, 13)
            minHeight = dp(49)
            minimumHeight = dp(49)
            stateListAnimator = null
            elevation = 0f
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun fullButtonParams(top: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(top)
        }
    }

    private fun surfaceColor(): Int {
        return if (Ui.isDark(this)) darkSurface else Color.WHITE
    }

    private fun fieldColor(): Int {
        return if (Ui.isDark(this)) darkField else Color.rgb(240, 244, 249)
    }

    private fun mainTextColor(): Int = Ui.text(this)

    private fun mutedTextColor(): Int = Ui.muted(this)

    // ---------------------------------------------------------
    // SIP ACCOUNT SETTINGS — PRESERVED
    // ---------------------------------------------------------

    private fun loadSavedAccount() {
        username.setText(prefs.getString("username", ""))
        domain.setText(prefs.getString("domain", ""))
        password.setText(prefs.getString("password", ""))
        port.setText(prefs.getString("port", "5060"))

        val savedTransport = prefs.getString("transport", "UDP")
        val index = listOf("UDP", "TCP", "TLS").indexOf(savedTransport)

        transport.setSelection(if (index >= 0) index else 0)
    }

    private fun hasSavedAccount() =
        prefs.getString("username", "").orEmpty().isNotBlank() &&
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

        prefs.edit()
            .putString("username", u)
            .putString("domain", d)
            .putString("password", pw)
            .putString("port", p)
            .putString("transport", t)
            .apply()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                501
            )
        }

        try {
            SIPEngine.connect(
                applicationContext,
                u,
                d,
                pw,
                p.toInt(),
                t
            )

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

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                501
            )
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

            acceptButton.visibility =
                if (call.state == Call.State.IncomingReceived) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        if (SIPEngine.listener != null) {
            SIPEngine.listener = null
        }

        super.onDestroy()
    }
}

// -------------------------------------------------------------
// SIP ENGINE — KEEP THIS LOGIC INTACT
// -------------------------------------------------------------

private object SIPEngine {

    var core: Core? = null
    var activeCall: Call? = null
    var status: String = "Not connected"

    var listener: ((String, Boolean) -> Unit)? = null
    var statusListener: ((String) -> Unit)? = null

    private var speakerOn = false

    @Synchronized
    fun connect(
        context: Context,
        username: String,
        domain: String,
        password: String,
        port: Int,
        transport: String
    ) {
        try {
            core?.stop()
        } catch (_: Exception) {
        }

        core = null
        activeCall = null

        val factory = Factory.instance()
        val newCore = factory.createCore(
            null,
            null,
            context.applicationContext
        )

        newCore.isVideoCaptureEnabled = false
        newCore.isVideoDisplayEnabled = false

        newCore.addListener(object : CoreListenerStub() {

            override fun onRegistrationStateChanged(
                core: Core,
                proxyConfig: org.linphone.core.ProxyConfig,
                state: org.linphone.core.RegistrationState,
                message: String
            ) {
                status = when (state) {
                    org.linphone.core.RegistrationState.Ok ->
                        "Registered • $username@$domain"

                    org.linphone.core.RegistrationState.Progress ->
                        "Registering…"

                    org.linphone.core.RegistrationState.Failed ->
                        "Registration failed: $message"

                    org.linphone.core.RegistrationState.Cleared ->
                        "Disconnected"

                    else -> "SIP: $state"
                }

                statusListener?.invoke(status)
                listener?.invoke(status, false)
            }

            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State?,
                message: String
            ) {
                activeCall = if (
                    state == Call.State.End ||
                    state == Call.State.Released ||
                    state == Call.State.Error
                ) {
                    null
                } else {
                    call
                }

                val incoming = state == Call.State.IncomingReceived

                val label = when (state) {
                    Call.State.IncomingReceived ->
                        "Incoming call from ${call.remoteAddress?.username ?: "unknown"}"

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

        val identity = factory.createAddress(
            "sip:$username@$domain"
        ) ?: throw IllegalArgumentException(
            "Invalid SIP identity or domain"
        )

        val server = factory.createAddress(
            "sip:$domain:$port;transport=${transport.lowercase()}"
        ) ?: throw IllegalArgumentException(
            "Invalid SIP server address"
        )

        val auth = factory.createAuthInfo(
            username,
            null,
            password,
            null,
            null,
            domain
        )

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
        val c = core
            ?: throw IllegalStateException("SIP account is not connected")

        val clean = target.trim()

        val uri = if (clean.startsWith("sip:", true)) {
            clean
        } else {
            val d = c.defaultAccount
                ?.params
                ?.identityAddress
                ?.domain
                ?: ""

            if (d.isBlank()) {
                throw IllegalStateException("SIP domain is missing")
            }

            "sip:$clean@$d"
        }

        val created = c.invite(uri)
            ?: throw IllegalStateException(
                "The SIP engine rejected the call request"
            )

        activeCall = created
    }

    fun setSpeaker(enabled: Boolean) {
        speakerOn = enabled

        val c = core ?: return

        val device = c.audioDevices.firstOrNull {
            if (enabled) {
                it.type == org.linphone.core.AudioDevice.Type.Speaker
            } else {
                it.type == org.linphone.core.AudioDevice.Type.Earpiece
            }
        }

        if (device != null) {
            activeCall?.outputAudioDevice = device
            c.defaultOutputAudioDevice = device
        }
    }
}
