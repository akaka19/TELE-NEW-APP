
package com.company.phonedirectory

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        loadSavedAccount()

        number.setText(intent.getStringExtra("dial_number").orEmpty())

        SIPEngine.listener = { message, incoming ->
            runOnUiThread {
                callState.text = message

                if (incoming) {
                    status.text = "Incoming call"
                    status.setTextColor(cyan)
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

                status.setTextColor(
                    when {
                        message.contains("Registered", true) ->
                            green

                        message.contains("failed", true) ||
                            message.contains("error", true) ->
                            red

                        else -> cyan
                    }
                )
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
            SIPEngine.activeCall?.let {
                try {
                    it.accept()
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

            muteButton.text =
                if (muted) "Unmute microphone" else "Mute microphone"
        }

        speakerButton.setOnClickListener {
            speaker = !speaker
            SIPEngine.setSpeaker(speaker)

            speakerButton.text =
                if (speaker) "Speaker: ON" else "Speaker: OFF"
        }
    }

    // ---------------------------------------------------------
    // MODERN SIP USER INTERFACE
    // ---------------------------------------------------------

    private fun buildUi() {
        val dark = Ui.isDark(this)
        val bg = Ui.background(this)
        val cardColor = if (dark) {
            Color.rgb(20, 30, 46)
        } else {
            Color.WHITE
        }

        val textColor = Ui.text(this)
        val mutedColor = Ui.muted(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root)
        }

        setContentView(scroll)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "☎"
            textSize = 28f
            gravity = android.view.Gravity.CENTER
            setTextColor(cyan)
            background = rounded(
                if (dark) Color.rgb(19, 54, 70)
                else Color.rgb(225, 248, 253),
                16
            )
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(58), dp(58))
        )

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        heading.addView(TextView(this).apply {
            text = "HiPath Directory"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        })

        heading.addView(TextView(this).apply {
            text = "SIP Calling · VoIP"
            textSize = 13f
            setTextColor(mutedColor)
            setPadding(0, dp(3), 0, 0)
        })

        header.addView(
            heading,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Make and receive calls"
            textSize = 14f
            setTextColor(mutedColor)
            setPadding(0, dp(12), 0, dp(20))
        })

        // SIP account card
        val accountCard = cardContainer(cardColor)

        accountCard.addView(sectionTitle("SIP ACCOUNT"))
        accountCard.addView(space(14))

        username = field(
            accountCard,
            "Extension / Username",
            "e.g. 861"
        )

        domain = field(
            accountCard,
            "SIP Server / IP Address",
            "e.g. 192.168.1.247"
        )

        password = field(
            accountCard,
            "Password",
            "SIP account password",
            true
        )

        port = field(
            accountCard,
            "SIP Port",
            "5060"
        )

        accountCard.addView(label("Transport"))

        transport = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SIPActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("UDP", "TCP", "TLS")
            )
        }

        accountCard.addView(
            transport,
            LinearLayout.LayoutParams(
                -1,
                dp(48)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        connectButton = button(
            "Save account & Connect",
            cyan,
            Color.rgb(7, 25, 38)
        )

accountCard.addView(connectButton, matchParams(4))

val statusPanel = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(12), dp(12), dp(12), dp(12))
    background = rounded(
        if (dark) Color.rgb(13, 43, 48)
        else Color.rgb(232, 249, 247),
        12
    )
}

val statusTitle = TextView(this).apply {
    text = "CONNECTION STATUS"
    textSize = 10f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(mutedColor)
    visibility = View.VISIBLE
}

statusPanel.addView(
    statusTitle,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

status = TextView(this).apply {
    text = "Not connected"
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(if (dark) cyan else Color.rgb(0, 120, 135))
    setPadding(0, dp(6), 0, 0)
    visibility = View.VISIBLE
}

statusPanel.addView(
    status,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

accountCard.addView(statusPanel, matchParams(12))

root.addView(
    accountCard,
    LinearLayout.LayoutParams(
        -1,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

accountCard.addView(statusPanel, matchParams(12))

root.addView(
    accountCard,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

        // Dialer card
        val dialCard = cardContainer(cardColor)

        dialCard.addView(sectionTitle("MAKE A CALL"))
        dialCard.addView(space(14))

        number = field(
            dialCard,
            "Extension / SIP Number",
            "Enter extension, e.g. 100"
        )

        callButton = button(
            "☎   Call",
            green,
            Color.rgb(5, 35, 26)
        )

        dialCard.addView(callButton, matchParams(8))

        callState = TextView(this).apply {
            text = "Ready to call"
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setTextColor(mutedColor)
            setPadding(0, dp(12), 0, dp(8))
        }

        dialCard.addView(callState)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        muteButton = button(
            "Mute microphone",
            Color.rgb(48, 62, 82),
            Color.WHITE
        )

        speakerButton = button(
            "Speaker: OFF",
            Color.rgb(48, 62, 82),
            Color.WHITE
        )

        controls.addView(
            muteButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(5)
            }
        )

        controls.addView(
            speakerButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(5)
            }
        )

        dialCard.addView(controls, matchParams(8))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        acceptButton = button(
            "Answer",
            green,
            Color.rgb(5, 35, 26)
        )

        rejectButton = button(
            "Reject / Hang up",
            red,
            Color.WHITE
        )

        acceptButton.visibility = View.GONE
        rejectButton.visibility = View.GONE

        actions.addView(
            acceptButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(5)
            }
        )

        actions.addView(
            rejectButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(5)
            }
        )

        dialCard.addView(actions, matchParams(8))

        root.addView(
            dialCard,
            LinearLayout.LayoutParams(
                -1,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        )

        root.addView(TextView(this).apply {
            text = "Incoming calls while the app is closed may require " +
                "additional background-service configuration."
            textSize = 12f
            setTextColor(mutedColor)
            setPadding(dp(4), dp(16), dp(4), 0)
        })
    }

    private fun cardContainer(color: Int) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = rounded(color, 20)
            elevation = dp(2).toFloat()
        }

    private fun sectionTitle(value: String) =
        TextView(this).apply {
            text = value
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        }

    private fun space(height: Int) =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
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
            textSize = 14f
            setSingleLine(true)
            setTextColor(Ui.text(this@SIPActivity))
            setHintTextColor(Ui.muted(this@SIPActivity))

            background = rounded(
                if (Ui.isDark(this@SIPActivity)) {
                    Color.rgb(30, 41, 59)
                } else {
                    Color.rgb(245, 247, 250)
                },
                12
            )

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
                bottomMargin = dp(12)
            }
        )

        return edit
    }

    private fun label(value: String) =
        TextView(this).apply {
            text = value
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@SIPActivity))
            setPadding(0, 0, 0, dp(6))
        }

    private fun button(
        textValue: String,
        color: Int,
        textColor: Int
    ) = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        background = rounded(color, 14)
        minHeight = dp(48)
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun matchParams(top: Int) =
        LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(top)
        }

    // ---------------------------------------------------------
    // SIP ACCOUNT AND CALL CONTROLS
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
                portNumber,
                t
            )

            status.text = "Connecting to $d:$p ($t)…"
            callState.text = "Waiting for SIP registration"
        } catch (e: Exception) {
            status.text = "Connection failed"
            status.setTextColor(red)
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        SIPEngine.listener = null
        SIPEngine.statusListener = null
        super.onDestroy()
    }
}

// -------------------------------------------------------------
// SIP ENGINE — kept separate from the UI
// -------------------------------------------------------------

private object SIPEngine {

    var core: Core? = null
    var activeCall: Call? = null
    var status: String = "Not connected"

    var listener: ((String, Boolean) -> Unit)? = null
    var statusListener: ((String) -> Unit)? = null

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

                val callLabel = when (state) {
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

                listener?.invoke(callLabel, incoming)
                statusListener?.invoke(callLabel)
            }
        })

        val identity = factory.createAddress("sip:$username@$domain")
            ?: throw IllegalArgumentException("Invalid SIP identity or domain")

        val server = factory.createAddress(
            "sip:$domain:$port;transport=${transport.lowercase()}"
        ) ?: throw IllegalArgumentException("Invalid SIP server address")

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
            val domainName =
                c.defaultAccount?.params?.identityAddress?.domain ?: ""

            if (domainName.isBlank()) {
                throw IllegalStateException("SIP domain is missing")
            }

            "sip:$clean@$domainName"
        }

        val created = c.invite(uri)
            ?: throw IllegalStateException(
                "The SIP engine rejected the call request"
            )

        activeCall = created
    }

    fun setSpeaker(enabled: Boolean) {
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
