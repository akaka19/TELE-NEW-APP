
package com.company.phonedirectory

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
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
                }

                acceptButton.visibility =
                    if (incoming) View.VISIBLE else View.GONE

                rejectButton.visibility =
                    if (incoming || SIPEngine.activeCall != null) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                rejectButton.text =
                    if (incoming) "✕  Reject call" else "✕  END CALL"

                if (!incoming && SIPEngine.activeCall == null) {
                    acceptButton.visibility = View.GONE
                    rejectButton.visibility = View.GONE
                }
            }
        }

        SIPEngine.statusListener = { message ->
            runOnUiThread {
                status.text = message
                connectButton.isEnabled = true

                when {
                    message.contains("Registered", true) ||
                    message.contains("registration successful", true) -> {
                        status.setTextColor(green)
                    }

                    message.contains("failed", true) ||
                    message.contains("error", true) -> {
                        status.setTextColor(red)
                    }

                    else -> {
                        status.setTextColor(cyan)
                    }
                }
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
            val call = SIPEngine.activeCall
            if (call != null) {
                try {
                    call.accept()
                    callState.text = "Answering call…"
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

    private fun buildUi() {
        window.statusBarColor = Color.rgb(8, 13, 24)
        window.navigationBarColor = Color.rgb(8, 13, 24)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(8, 13, 24))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }

        setContentView(scroll)

        root.addView(
            TextView(this).apply {
                text = "HiPath Directory"
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        root.addView(
            TextView(this).apply {
                text = "BUSINESS SIP PHONE  •  PREMIUM"
                textSize = 11f
                letterSpacing = 0.12f
                setTextColor(cyan)
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(18))
            }
        )

        val accountCard = premiumCard(root)
        accountCard.addView(sectionTitle("SIP ACCOUNT"))

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

        transport = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SIPActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("UDP", "TCP", "TLS")
            )
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(Color.rgb(30, 41, 59), 12)
        }

        accountCard.addView(label("Transport"))

        accountCard.addView(
            transport,
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(10)
            }
        )

        connectButton = button(
            "●  Save account & Connect",
            cyan,
            Color.rgb(8, 24, 38)
        )

        accountCard.addView(connectButton, matchParams(8))

        status = TextView(this).apply {
            text = "Not connected"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            setPadding(0, dp(14), 0, dp(2))
        }

        accountCard.addView(status)

        val callCard = premiumCard(root)
        callCard.addView(sectionTitle("CALL CONTROL"))

        callCard.addView(
            TextView(this).apply {
                text = "Make a call"
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(4))
            }
        )

        callState = TextView(this).apply {
            text = "Ready to call"
            textSize = 14f
            setTextColor(Color.rgb(148, 163, 184))
            setPadding(0, dp(4), 0, dp(10))
        }

        callCard.addView(callState)

        number = field(
            callCard,
            "EXTENSION / SIP ADDRESS",
            "e.g. 2550"
        )

        callButton = button(
            "☎   CALL",
            green,
            Color.rgb(5, 30, 23)
        )

        callCard.addView(
            callButton,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                topMargin = dp(8)
            }
        )

        acceptButton = button(
            "✓   ANSWER",
            green,
            Color.rgb(5, 30, 23)
        ).apply {
            visibility = View.GONE
        }

        callCard.addView(acceptButton, matchParams(8))

        rejectButton = button(
            "✕   END CALL",
            red,
            Color.WHITE
        ).apply {
            visibility = View.GONE
        }

        callCard.addView(
            rejectButton,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                topMargin = dp(8)
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        muteButton = button(
            "Mute mic",
            Color.rgb(40, 52, 73),
            Color.WHITE
        )

        speakerButton = button(
            "Speaker: OFF",
            Color.rgb(40, 52, 73),
            Color.WHITE
        )

        controls.addView(
            muteButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(6)
            }
        )

        controls.addView(
            speakerButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(6)
            }
        )

        callCard.addView(controls, matchParams(8))

        root.addView(
            TextView(this).apply {
                text =
                    "Background calling stays active while the SIP service is running. Android battery restrictions and server configuration can still affect reachability."
                textSize = 12f
                setTextColor(Color.rgb(100, 116, 139))
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(6))
            }
        )
    }

    private fun premiumCard(root: LinearLayout): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.rgb(18, 28, 44), 20)
        }

        root.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            }
        )

        return card
    }

    private fun sectionTitle(value: String) =
        TextView(this).apply {
            text = value
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            setPadding(0, 0, 0, dp(10))
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
            setTextColor(Ui.text(this@SIPActivity))
            setHintTextColor(Ui.muted(this@SIPActivity))
            background = rounded(
                if (Ui.isDark(this@SIPActivity)) {
                    Color.rgb(30, 41, 59)
                } else {
                    Color.WHITE
                },
                12
            )
            setPadding(dp(13), 0, dp(13), 0)
            inputType = if (isPassword) {
                129
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        parent.addView(
            edit,
            LinearLayout.LayoutParams(-1, dp(49)).apply {
                bottomMargin = dp(9)
            }
        )

        return edit
    }

    private fun label(value: String) =
        TextView(this).apply {
            text = value
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@SIPActivity))
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
        background = rounded(color, 13)
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

    private fun loadSavedAccount() {
        username.setText(prefs.getString("username", ""))
        domain.setText(prefs.getString("domain", ""))
        password.setText(prefs.getString("password", ""))
        port.setText(prefs.getString("port", "5060"))

        val savedTransport = prefs.getString("transport", "UDP")
        val index = listOf("UDP", "TCP", "TLS").indexOf(savedTransport)

        transport.setSelection(if (index >= 0) index else 0)
    }

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

        connectButton.isEnabled = false
        status.text = "Starting SIP service…"
        callState.text = "Connecting to $d:$portNumber ($t)"

        try {
            val serviceIntent = Intent(
                this,
                SipForegroundService::class.java
            ).apply {
                action = SipForegroundService.ACTION_CONNECT
                putExtra(SipForegroundService.EXTRA_USERNAME, u)
                putExtra(SipForegroundService.EXTRA_DOMAIN, d)
                putExtra(SipForegroundService.EXTRA_PASSWORD, pw)
                putExtra(SipForegroundService.EXTRA_PORT, portNumber)
                putExtra(SipForegroundService.EXTRA_TRANSPORT, t)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            connectButton.isEnabled = true
            status.text = "Connection failed"
            callState.text = e.javaClass.simpleName
            showMessage(
                "Could not start SIP: ${e.message ?: e.javaClass.simpleName}"
            )
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
            rejectButton.text = "✕  END CALL"
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

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        SIPEngine.listener = null
        SIPEngine.statusListener = null
        super.onDestroy()
    }
}

internal object SIPEngine {

    @Volatile
    var core: Core? = null

    @Volatile
    var activeCall: Call? = null

    @Volatile
    var status: String = "Not connected"

    var listener: ((String, Boolean) -> Unit)? = null
    var statusListener: ((String) -> Unit)? = null
    var backgroundListener: ((String, Boolean) -> Unit)? = null

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
        val factory = Factory.instance()

        try {
            core?.stop()
        } catch (_: Exception) {
        }

        core = null
        activeCall = null

        val appContext = context.applicationContext

        val newCore = factory.createCore(
            null,
            null,
            appContext
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
                val newStatus = when (state) {
                    org.linphone.core.RegistrationState.Ok ->
                        "Registered • $username@$domain"

                    org.linphone.core.RegistrationState.Progress ->
                        "Registering…"

                    org.linphone.core.RegistrationState.Failed ->
                        "Registration failed: $message"

                    org.linphone.core.RegistrationState.Cleared ->
                        "Disconnected"

                    else -> "SIP: $state $message"
                }

                status = newStatus
                statusListener?.invoke(newStatus)
                listener?.invoke(newStatus, false)
                backgroundListener?.invoke(newStatus, false)
            }

            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State?,
                message: String
            ) {
                activeCall =
                    if (
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
                backgroundListener?.invoke(label, incoming)
            }
        })

        try {
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

            // Store the core before start so the UI can see its state.
            core = newCore
            status = "Starting SIP engine…"
            statusListener?.invoke(status)
            listener?.invoke(status, false)
            backgroundListener?.invoke(status, false)

            newCore.start()

            status = "SIP engine started — registering…"
            statusListener?.invoke(status)
            listener?.invoke(status, false)
            backgroundListener?.invoke(status, false)

        } catch (e: Exception) {
            try {
                newCore.stop()
            } catch (_: Exception) {
            }

            core = null
            status = "SIP connection failed: ${e.message ?: e.javaClass.simpleName}"
            statusListener?.invoke(status)
            listener?.invoke(status, false)
            backgroundListener?.invoke(status, false)

            throw e
        }
    }

    fun call(target: String) {
        val c = core ?: throw IllegalStateException(
            "SIP account is not connected"
        )

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

        val desiredType =
            if (enabled) {
                org.linphone.core.AudioDevice.Type.Speaker
            } else {
                org.linphone.core.AudioDevice.Type.Earpiece
            }

        val device = c.audioDevices.firstOrNull {
            it.type == desiredType
        }

        if (device != null) {
            activeCall?.outputAudioDevice = device
            c.defaultOutputAudioDevice = device
        }
    }
}

class SipForegroundService : android.app.Service() {

    companion object {
        const val ACTION_CONNECT =
            "com.company.phonedirectory.sip.CONNECT"

        const val ACTION_ANSWER =
            "com.company.phonedirectory.sip.ANSWER"

        const val ACTION_REJECT =
            "com.company.phonedirectory.sip.REJECT"

        const val EXTRA_USERNAME = "sip_username"
        const val EXTRA_DOMAIN = "sip_domain"
        const val EXTRA_PASSWORD = "sip_password"
        const val EXTRA_PORT = "sip_port"
        const val EXTRA_TRANSPORT = "sip_transport"

        private const val CHANNEL_ID = "hipath_sip_calls"
        private const val SERVICE_NOTIFICATION_ID = 4100
        private const val INCOMING_NOTIFICATION_ID = 4101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        SIPEngine.backgroundListener = { message, incoming ->
            if (incoming) {
                showIncomingCall(message)
            } else {
                val manager =
                    getSystemService(NotificationManager::class.java)

                if (
                    message.contains("Call ended", true) ||
                    message.contains("Call finished", true) ||
                    message.contains("Call error", true)
                ) {
                    manager.cancel(INCOMING_NOTIFICATION_ID)
                }

                updateServiceNotification(message)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        try {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                serviceNotification("SIP service running")
            )
        } catch (e: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_ANSWER -> {
                try {
                    SIPEngine.activeCall?.accept()
                } catch (_: Exception) {
                }

                getSystemService(NotificationManager::class.java)
                    .cancel(INCOMING_NOTIFICATION_ID)
            }

            ACTION_REJECT -> {
                try {
                    SIPEngine.activeCall?.terminate()
                } catch (_: Exception) {
                }

                getSystemService(NotificationManager::class.java)
                    .cancel(INCOMING_NOTIFICATION_ID)
            }

            ACTION_CONNECT -> {
                val user =
                    intent.getStringExtra(EXTRA_USERNAME).orEmpty()

                val host =
                    intent.getStringExtra(EXTRA_DOMAIN).orEmpty()

                val pass =
                    intent.getStringExtra(EXTRA_PASSWORD).orEmpty()

                val port = intent.getIntExtra(EXTRA_PORT, 5060)

                val transport = intent
                    .getStringExtra(EXTRA_TRANSPORT)
                    .orEmpty()
                    .ifBlank { "UDP" }

                if (
                    user.isBlank() ||
                    host.isBlank() ||
                    pass.isBlank()
                ) {
                    updateServiceNotification(
                        "SIP connection failed: account details missing"
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                updateServiceNotification("Connecting to $host…")

                try {
                    SIPEngine.connect(
                        applicationContext,
                        user,
                        host,
                        pass,
                        port,
                        transport
                    )
                } catch (e: Exception) {
                    updateServiceNotification(
                        "SIP connection failed: ${e.message ?: e.javaClass.simpleName}"
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
            }

            else -> {
                updateServiceNotification(SIPEngine.status)

                if (SIPEngine.core == null) {
                    val saved = getSharedPreferences(
                        "sip_account",
                        MODE_PRIVATE
                    )

                    val user =
                        saved.getString("username", "").orEmpty()

                    val host =
                        saved.getString("domain", "").orEmpty()

                    val pass =
                        saved.getString("password", "").orEmpty()

                    val port = saved
                        .getString("port", "5060")
                        ?.toIntOrNull() ?: 5060

                    val transport = saved
                        .getString("transport", "UDP")
                        .orEmpty()
                        .ifBlank { "UDP" }

                    if (
                        user.isNotBlank() &&
                        host.isNotBlank() &&
                        pass.isNotBlank()
                    ) {
                        try {
                            SIPEngine.connect(
                                applicationContext,
                                user,
                                host,
                                pass,
                                port,
                                transport
                            )
                        } catch (e: Exception) {
                            updateServiceNotification(
                                "SIP reconnect failed: ${e.message ?: e.javaClass.simpleName}"
                            )
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "HiPath SIP calls",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SIP registration and incoming-call alerts"

                setSound(
                    android.provider.Settings.System.DEFAULT_RINGTONE_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(
                            android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                        )
                        .build()
                )

                lockscreenVisibility =
                    android.app.Notification.VISIBILITY_PUBLIC
            }

            getSystemService(
                android.app.NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun serviceNotification(
        message: String
    ): android.app.Notification {
        val openIntent = Intent(
            this,
            SIPActivity::class.java
        ).addFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        val openPending = android.app.PendingIntent.getActivity(
            this,
            SERVICE_NOTIFICATION_ID,
            openIntent,
            pendingFlags()
        )

        return androidx.core.app.NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("HiPath Directory • SIP active")
            .setContentText(message)
            .setContentIntent(openPending)
            .setCategory(
                androidx.core.app.NotificationCompat.CATEGORY_SERVICE
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                androidx.core.app.NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateServiceNotification(message: String) {
        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            SERVICE_NOTIFICATION_ID,
            serviceNotification(message)
        )
    }

    private fun showIncomingCall(message: String) {
        val openIntent = Intent(
            this,
            SIPActivity::class.java
        ).addFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        val openPending = android.app.PendingIntent.getActivity(
            this,
            4101,
            openIntent,
            pendingFlags()
        )

        val answerIntent = Intent(
            this,
            SipForegroundService::class.java
        ).setAction(ACTION_ANSWER)

        val rejectIntent = Intent(
            this,
            SipForegroundService::class.java
        ).setAction(ACTION_REJECT)

        val answerPending = android.app.PendingIntent.getService(
            this,
            4102,
            answerIntent,
            pendingFlags()
        )

        val rejectPending = android.app.PendingIntent.getService(
            this,
            4103,
            rejectIntent,
            pendingFlags()
        )

        val notification =
            androidx.core.app.NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Incoming SIP call")
                .setContentText(message)
                .setContentIntent(openPending)
                .setCategory(
                    androidx.core.app.NotificationCompat.CATEGORY_CALL
                )
                .setPriority(
                    androidx.core.app.NotificationCompat.PRIORITY_MAX
                )
                .setVisibility(
                    androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
                )
                .setSound(
                    android.provider.Settings.System.DEFAULT_RINGTONE_URI
                )
                .setAutoCancel(false)
                .addAction(
                    android.R.drawable.sym_action_call,
                    "Answer",
                    answerPending
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Reject",
                    rejectPending
                )
                .build()

        getSystemService(NotificationManager::class.java).notify(
            INCOMING_NOTIFICATION_ID,
            notification
        )
    }

    private fun pendingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= 23) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        SIPEngine.backgroundListener = null
        super.onDestroy()
    }
}
