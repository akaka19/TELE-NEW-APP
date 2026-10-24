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
        val newCore = factory.createCore(null, null, context.applicationContext)
        newCore.isVideoCaptureEnabled = false
        newCore.isVideoDisplayEnabled = false

        newCore.addListener(object : CoreListenerStub() {
            override fun onRegistrationStateChanged(
                core: Core,
                proxyConfig: org.linphone.core.ProxyConfig,
                state: RegistrationState,
                message: String
            ) {
                status = when (state) {
                    RegistrationState.Ok -> "Registered • $username@$domain"
                    RegistrationState.Progress -> "Registering…"
                    RegistrationState.Failed -> "Registration failed: $message"
                    RegistrationState.Cleared -> "Disconnected"
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
                activeCall =
                    if (state == Call.State.End ||
                        state == Call.State.Released ||
                        state == Call.State.Error
                    ) null else call

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
        val uri = if (clean.startsWith("sip:", true)) {
            clean
        } else {
            val d = c.defaultAccount?.params?.identityAddress?.domain ?: ""
            if (d.isBlank()) throw IllegalStateException("SIP domain is missing")
            "sip:$clean@$d"
        }
        val created = c.invite(uri)
        if (created == null) {
            throw IllegalStateException("The SIP engine rejected the call request")
        }
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
