
package com.company.phonedirectory

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.json.JSONArray
import org.json.JSONObject

class SIPActivity : Activity() {

    private lateinit var usernameField: EditText
    private lateinit var domainField: EditText
    private lateinit var passwordField: EditText
    private lateinit var portField: EditText
    private lateinit var numberField: EditText

    private lateinit var transportSpinner: Spinner
    private lateinit var accountSpinner: Spinner
    private lateinit var settingsPanel: LinearLayout

    private lateinit var statusText: TextView
    private lateinit var callStateText: TextView

    private lateinit var settingsButton: Button
    private lateinit var connectButton: Button
    private lateinit var callButton: Button
    private lateinit var answerButton: Button
    private lateinit var rejectButton: Button
    private lateinit var hangupButton: Button
    private lateinit var muteButton: Button
    private lateinit var speakerButton: Button

    private var settingsVisible = false
    private var microphoneMuted = false
    private var speakerEnabled = false

    private val preferences by lazy {
        getSharedPreferences("sip_account", MODE_PRIVATE)
    }

    private val profiles = mutableListOf<SipProfile>()

    private var selectedProfileIndex = -1

    private val transportOptions = listOf("UDP", "TCP", "TLS")

    private data class SipProfile(
        var name: String,
        var username: String,
        var domain: String,
        var password: String,
        var port: Int,
        var transport: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        loadProfiles()
        refreshAccountSpinner()

        numberField.setText(
            intent.getStringExtra("dial_number").orEmpty()
        )

        SIPEngine.listener = { message, incoming ->
            runOnUiThread {
                callStateText.text = message

                answerButton.visibility =
                    if (incoming) View.VISIBLE else View.GONE

                rejectButton.visibility =
                    if (incoming) View.VISIBLE else View.GONE

                hangupButton.visibility =
                    if (SIPEngine.hasActiveCall()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }
        }

        SIPEngine.statusListener = { message ->
            runOnUiThread {
                statusText.text = message

                if (message.startsWith("Registered")) {
                    settingsVisible = false
                    settingsPanel.visibility = View.GONE
                    settingsButton.text = "SIP Settings"
                }
            }
        }

        settingsButton.setOnClickListener {
            settingsVisible = !settingsVisible

            settingsPanel.visibility =
                if (settingsVisible) View.VISIBLE else View.GONE

            settingsButton.text =
                if (settingsVisible) {
                    "Hide SIP Settings"
                } else {
                    "SIP Settings"
                }
        }

        connectButton.setOnClickListener {
            connectAccount()
        }

        callButton.setOnClickListener {
            makeCall()
        }

        answerButton.setOnClickListener {
            try {
                SIPEngine.answer()
            } catch (e: Exception) {
                showMessage(e.message ?: "Unable to answer call")
            }
        }

        rejectButton.setOnClickListener {
            endCall()
        }

        hangupButton.setOnClickListener {
            endCall()
        }

        muteButton.setOnClickListener {
            microphoneMuted = !microphoneMuted
            SIPEngine.setMicrophoneMuted(microphoneMuted)

            muteButton.text =
                if (microphoneMuted) {
                    "Unmute microphone"
                } else {
                    "Mute microphone"
                }
        }

        speakerButton.setOnClickListener {
            speakerEnabled = !speakerEnabled
            SIPEngine.setSpeaker(speakerEnabled)

            speakerButton.text =
                if (speakerEnabled) {
                    "Speaker: ON"
                } else {
                    "Speaker: OFF"
                }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 24)
            setBackgroundColor(0xFF101820.toInt())
        }

        val scroll = ScrollView(this)
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val title = TextView(this).apply {
            text = "SIP Calling"
            textSize = 26f
            setTextColor(0xFF00D4FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
        }
        root.addView(title)

        statusText = TextView(this).apply {
            text = "Disconnected"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 8, 0, 8)
        }
        root.addView(statusText)

        accountSpinner = Spinner(this)
        root.addView(accountSpinner)

        val accountActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val newAccountButton = makeButton("New account")
        val deleteAccountButton = makeButton("Delete account")

        accountActions.addView(
            newAccountButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        accountActions.addView(
            deleteAccountButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(accountActions)

        settingsButton = makeButton("SIP Settings")
        root.addView(settingsButton)

        settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        usernameField = addEditField(
            settingsPanel,
            "SIP username",
            "username"
        )

        domainField = addEditField(
            settingsPanel,
            "SIP server / domain",
            "sip.example.com"
        )

        passwordField = addEditField(
            settingsPanel,
            "SIP password",
            ""
        )

        passwordField.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        portField = addEditField(
            settingsPanel,
            "SIP port",
            "5060"
        )

        portField.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        addLabel(settingsPanel, "Transport")

        transportSpinner = Spinner(this)

        transportSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            transportOptions
        )

        settingsPanel.addView(transportSpinner)

        connectButton = makeButton("Save and connect")
        settingsPanel.addView(connectButton)

        root.addView(settingsPanel)

        callStateText = TextView(this).apply {
            text = "Ready"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 20, 0, 12)
        }

        root.addView(callStateText)

        numberField = addEditField(
            root,
            "Number or SIP address",
            "e.g. 2550"
        )

        callButton = makeButton("Call")
        root.addView(callButton)

        answerButton = makeButton("Answer")
        answerButton.visibility = View.GONE
        root.addView(answerButton)

        rejectButton = makeButton("Reject")
        rejectButton.visibility = View.GONE
        root.addView(rejectButton)

        hangupButton = makeButton("Hang up")
        hangupButton.visibility = View.GONE
        root.addView(hangupButton)

        muteButton = makeButton("Mute microphone")
        root.addView(muteButton)

        speakerButton = makeButton("Speaker: OFF")
        root.addView(speakerButton)

        setContentView(scroll)

        newAccountButton.setOnClickListener {
            selectedProfileIndex = -1
            clearAccountFields()

            settingsVisible = true
            settingsPanel.visibility = View.VISIBLE
            settingsButton.text = "Hide SIP Settings"
        }

        deleteAccountButton.setOnClickListener {
            deleteSelectedAccount()
        }

        accountSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position in profiles.indices) {
                        selectedProfileIndex = position
                        showProfile(profiles[position])
                    } else {
                        selectedProfileIndex = -1
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }
    }

    private fun addLabel(
        parent: LinearLayout,
        text: String
    ) {
        val label = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFFB8C7D9.toInt())
            setPadding(0, 12, 0, 4)
        }

        parent.addView(label)
    }

    private fun addEditField(
        parent: LinearLayout,
        label: String,
        hint: String
    ): EditText {
        addLabel(parent, label)

        val editText = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8292A5.toInt())
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xFF263442.toInt())
        }

        parent.addView(
            editText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
        )

        return editText
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF087EA4.toInt())
        }
    }

    private fun clearAccountFields() {
        usernameField.setText("")
        domainField.setText("")
        passwordField.setText("")
        portField.setText("5060")
        transportSpinner.setSelection(0)
    }

    // Reads saved profiles and supports an older port stored as String or Int.
    private fun loadProfiles() {
        profiles.clear()

        val stored = preferences.all["profiles"] as? String

        if (!stored.isNullOrBlank()) {
            try {
                val array = JSONArray(stored)

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    val savedPort = item.optInt("port", 5060)
                        .coerceIn(1, 65535)

                    profiles.add(
                        SipProfile(
                            name = item.optString("name"),
                            username = item.optString("username"),
                            domain = item.optString("domain"),
                            password = item.optString("password"),
                            port = savedPort,
                            transport = item.optString(
                                "transport",
                                "UDP"
                            )
                        )
                    )
                }
            } catch (_: Exception) {
                profiles.clear()
            }
        }

        if (profiles.isEmpty()) {
            val oldUsername =
                preferences.all["username"] as? String ?: ""

            val oldDomain =
                preferences.all["domain"] as? String ?: ""

            if (
                oldUsername.isNotBlank() &&
                oldDomain.isNotBlank()
            ) {
                val oldPort = when (
                    val value = preferences.all["port"]
                ) {
                    is String -> value.toIntOrNull() ?: 5060
                    is Int -> value
                    else -> 5060
                }.coerceIn(1, 65535)

                val oldPassword =
                    preferences.all["password"] as? String ?: ""

                val oldTransport =
                    preferences.all["transport"] as? String ?: "UDP"

                profiles.add(
                    SipProfile(
                        name = "$oldUsername@$oldDomain",
                        username = oldUsername,
                        domain = oldDomain,
                        password = oldPassword,
                        port = oldPort,
                        transport = oldTransport
                    )
                )

                saveProfiles()
            }
        }
    }

    private fun saveProfiles() {
        val array = JSONArray()

        profiles.forEach { profile ->
            val item = JSONObject()

            item.put("name", profile.name)
            item.put("username", profile.username)
            item.put("domain", profile.domain)
            item.put("password", profile.password)
            item.put("port", profile.port)
            item.put("transport", profile.transport)

            array.put(item)
        }

        preferences.edit()
            .putString("profiles", array.toString())
            .apply()
    }

    private fun refreshAccountSpinner() {
        val names = profiles.map {
            it.name
        }.toMutableList()

        if (names.isEmpty()) {
            names.add("No saved accounts")
        }

        accountSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        if (profiles.isNotEmpty()) {
            val index = selectedProfileIndex.coerceIn(
                0,
                profiles.lastIndex
            )

            accountSpinner.setSelection(index)
            showProfile(profiles[index])
        }
    }

    private fun showProfile(profile: SipProfile) {
        usernameField.setText(profile.username)
        domainField.setText(profile.domain)
        passwordField.setText(profile.password)
        portField.setText(profile.port.toString())

        val transportIndex = transportOptions.indexOf(
            profile.transport.uppercase()
        ).coerceAtLeast(0)

        transportSpinner.setSelection(transportIndex)
    }

    private fun deleteSelectedAccount() {
        if (selectedProfileIndex !in profiles.indices) {
            showMessage("Select an account first")
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Delete account")
            .setMessage("Delete this saved SIP account?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                profiles.removeAt(selectedProfileIndex)
                selectedProfileIndex = -1

                saveProfiles()
                refreshAccountSpinner()
                clearAccountFields()

                showMessage("Account deleted")
            }
            .show()
    }

    private fun connectAccount() {
        val username = usernameField.text.toString().trim()
        val domain = domainField.text.toString().trim()
        val password = passwordField.text.toString()
        val port = portField.text.toString().toIntOrNull() ?: 5060

        val transport =
            transportSpinner.selectedItem?.toString() ?: "UDP"

        if (
            username.isBlank() ||
            domain.isBlank() ||
            password.isBlank()
        ) {
            showMessage("Enter username, domain and password")
            return
        }

        if (port !in 1..65535) {
            showMessage("Enter a valid SIP port")
            return
        }

        val profileName = "$username@$domain"

        val existingIndex = profiles.indexOfFirst {
            it.username == username && it.domain == domain
        }

        val profile = SipProfile(
            name = profileName,
            username = username,
            domain = domain,
            password = password,
            port = port,
            transport = transport
        )

        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
            selectedProfileIndex = existingIndex
        } else {
            profiles.add(profile)
            selectedProfileIndex = profiles.lastIndex
        }

        saveProfiles()
        refreshAccountSpinner()

        try {
            SIPEngine.connect(
                applicationContext,
                username,
                domain,
                password,
                port,
                transport
            )

            statusText.text = "Connecting to SIP server…"
        } catch (e: Exception) {
            statusText.text = "Connection error: ${e.message}"
            showMessage(e.message ?: "Unable to connect")
        }
    }

    private fun makeCall() {
        val target = numberField.text.toString().trim()

        if (target.isBlank()) {
            showMessage("Enter a number first")
            return
        }

        if (!SIPEngine.isConnected()) {
            showMessage("Connect a SIP account first")
            return
        }

        try {
            SIPEngine.call(target)
            callStateText.text = "Starting call…"
        } catch (e: Exception) {
            callStateText.text = "Call failed: ${e.message}"
            showMessage(e.message ?: "Unable to start call")
        }
    }

    private fun endCall() {
        try {
            SIPEngine.endCall()
        } catch (e: Exception) {
            showMessage(e.message ?: "Unable to end call")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()

        SIPEngine.listener = null
        SIPEngine.statusListener = null
    }

    private object SIPEngine {

        private var core: Core? = null
        private var activeCall: Call? = null

        private var speakerOn = false
        private var microphoneMuted = false

        private var currentStatus = "Disconnected"

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
                    state: RegistrationState,
                    message: String
                ) {
                    currentStatus = when (state) {
                        RegistrationState.Ok ->
                            "Registered • $username@$domain"

                        RegistrationState.Progress ->
                            "Registering…"

                        RegistrationState.Failed ->
                            "Registration failed: $message"

                        RegistrationState.Cleared ->
                            "Disconnected"

                        else -> "SIP: $state"
                    }

                    statusListener?.invoke(currentStatus)
                    listener?.invoke(currentStatus, false)
                }

                override fun onCallStateChanged(
                    core: Core,
                    call: Call,
                    state: Call.State,
                    message: String
                ) {
                    activeCall = when (state) {
                        Call.State.End,
                        Call.State.Released,
                        Call.State.Error -> null

                        else -> call
                    }

                    val incoming =
                        state == Call.State.IncomingReceived

                    val label = when (state) {
                        Call.State.IncomingReceived ->
                            "Incoming call from " +
                                (
                                    call.remoteAddress?.username
                                        ?: "unknown"
                                )

                        Call.State.OutgoingInit ->
                            "Starting call…"

                        Call.State.OutgoingProgress ->
                            "Calling…"

                        Call.State.OutgoingRinging ->
                            "Ringing…"

                        Call.State.Connected ->
                            "Connected"

                        Call.State.StreamsRunning ->
                            "Call in progress"

                        Call.State.End ->
                            "Call ended"

                        Call.State.Error ->
                            "Call error: $message"

                        Call.State.Released ->
                            "Call finished"

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

            core = newCore
            newCore.start()

            currentStatus = "SIP engine started — registering…"

            statusListener?.invoke(currentStatus)
            listener?.invoke(currentStatus, false)
        }

        fun isConnected(): Boolean {
            return core != null && core?.defaultAccount != null
        }

        fun hasActiveCall(): Boolean {
            return activeCall != null
        }

        fun call(target: String) {
            val currentCore = core
                ?: throw IllegalStateException(
                    "SIP account is not connected"
                )

            val clean = target.trim()

            val uri = if (
                clean.startsWith("sip:", ignoreCase = true)
            ) {
                clean
            } else {
                val sipDomain = currentCore.defaultAccount
                    ?.params
                    ?.identityAddress
                    ?.domain
                    .orEmpty()

                if (sipDomain.isBlank()) {
                    throw IllegalStateException(
                        "SIP domain is missing"
                    )
                }

                "sip:$clean@$sipDomain"
            }

            val createdCall = currentCore.invite(uri)
                ?: throw IllegalStateException(
                    "The SIP engine rejected the call request"
                )

            activeCall = createdCall
        }

        fun answer() {
            val call = activeCall
                ?: throw IllegalStateException("No incoming call")

            call.accept()
        }

        fun endCall() {
            val call = activeCall
                ?: throw IllegalStateException(
                    "There is no active call"
                )

            call.terminate()
        }

        fun setMicrophoneMuted(muted: Boolean) {
            microphoneMuted = muted
            core?.isMicEnabled = !muted
        }

        fun setSpeaker(enabled: Boolean) {
            speakerOn = enabled

            val currentCore = core ?: return

            val desiredType = if (enabled) {
                AudioDevice.Type.Speaker
            } else {
                AudioDevice.Type.Earpiece
            }

            val device = currentCore.audioDevices.firstOrNull {
                it.type == desiredType
            }

            if (device != null) {
                activeCall?.outputAudioDevice = device
                currentCore.defaultOutputAudioDevice = device
            }
        }
    }
}
