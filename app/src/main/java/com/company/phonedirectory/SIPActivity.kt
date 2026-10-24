
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
                callState.text = message
                if (incoming) {
                    status.text = "Incoming call"
                }
                acceptButton.visibility = if (incoming) View.VISIBLE else View.GONE
                rejectButton.visibility = if (incoming || SIPEngine.activeCall != null) View.VISIBLE else View.GONE
                rejectButton.text = if (incoming) "✕  Reject call" else "✕  END CALL"
                if (!incoming && SIPEngine.activeCall == null) {
                    rejectButton.visibility = View.GONE
                    acceptButton.visibility = View.GONE
                }
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
