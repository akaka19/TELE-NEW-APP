package com.company.phonedirectory
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LockActivity : Activity() {
    private val p by lazy { getSharedPreferences("security", MODE_PRIVATE) }
    private lateinit var pin: EditText
    private lateinit var err: TextView
    private var opened = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_lock)
        pin = findViewById(R.id.pinInput); err = findViewById(R.id.lockError)
        val enabled = p.getBoolean("enabled", false); val bio = p.getBoolean("biometric", false)
        pin.visibility = if (enabled) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.pinButton).visibility = if (enabled) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.continueButton).visibility = if (enabled) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.biometricButton).visibility = if (enabled && bio) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.pinButton).setOnClickListener { if (verify(pin.text.toString())) open() else err.text = "Incorrect PIN. Try again." }
        findViewById<Button>(R.id.continueButton).setOnClickListener { open() }
        findViewById<Button>(R.id.biometricButton).setOnClickListener { biometric() }
        if (!enabled) open() else if (bio) biometric()
    }
    private fun biometric() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) { err.text = "Biometrics unavailable. Use PIN."; return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { open() }
            override fun onAuthenticationFailed() { err.text = "Not recognized. Try again or use PIN." }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { err.text = errString.toString() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("HiPath Directory").setSubtitle("Authenticate to continue").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).setNegativeButtonText("Cancel").build())
    }
    private fun verify(s: String): Boolean {
        return try {
            val salt = p.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
            val expected = p.getString("hash", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
            MessageDigest.isEqual(expected, SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(s.toCharArray(), salt, 120000, 256)).encoded)
        } catch (_: Exception) { false }
    }
    private fun open() { if (opened || isFinishing) return; opened = true; startActivity(Intent(this, MainActivity::class.java)); finish() }
    @Deprecated("Lock screen ignores Back") override fun onBackPressed() {}
}
