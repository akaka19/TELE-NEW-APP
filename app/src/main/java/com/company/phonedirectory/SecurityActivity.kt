package com.company.phonedirectory
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityActivity : Activity() {
    private val p by lazy { getSharedPreferences("security", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_security)
        val pin = findViewById<EditText>(R.id.newPinInput); val confirm = findViewById<EditText>(R.id.confirmPinInput)
        val status = findViewById<TextView>(R.id.securityStatus); refresh(status)
        findViewById<Button>(R.id.savePinButton).setOnClickListener {
            val s = pin.text.toString()
            if (s.length !in 4..12 || s.any { !it.isDigit() }) { pin.error = "Use 4 to 12 digits"; return@setOnClickListener }
            if (s != confirm.text.toString()) { confirm.error = "PINs do not match"; return@setOnClickListener }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            p.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).putString("hash", Base64.encodeToString(hash(s, salt), Base64.NO_WRAP)).putBoolean("enabled", true).apply()
            pin.text.clear(); confirm.text.clear(); Toast.makeText(this, "PIN lock enabled", Toast.LENGTH_SHORT).show(); refresh(status)
        }
        findViewById<Button>(R.id.biometricSetupButton).setOnClickListener { p.edit().putBoolean("biometric", true).apply(); Toast.makeText(this, "Biometric unlock enabled if supported and enrolled", Toast.LENGTH_LONG).show(); refresh(status) }
        findViewById<Button>(R.id.disableButton).setOnClickListener {
            val input = EditText(this).apply { hint = "Current PIN"; inputType = 18 }
            AlertDialog.Builder(this).setTitle("Disable app lock").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Disable") { _, _ ->
                if (verify(input.text.toString())) { p.edit().clear().apply(); refresh(status); Toast.makeText(this, "Lock disabled", Toast.LENGTH_SHORT).show() } else Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }.show()
        }
        findViewById<Button>(R.id.securityDoneButton).setOnClickListener { finish() }
    }
    private fun refresh(v: TextView) { v.text = "App lock: ${if (p.getBoolean("enabled", false)) "Enabled" else "Disabled"}\nFingerprint: ${if (p.getBoolean("biometric", false)) "Enabled if supported" else "Disabled"}" }
    private fun verify(s: String): Boolean {
        val salt = p.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = p.getString("hash", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return MessageDigest.isEqual(expected, hash(s, salt))
    }
    private fun hash(s: String, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(s.toCharArray(), salt, 120000, 256)).encoded
}
