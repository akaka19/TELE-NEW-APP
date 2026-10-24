
package com.company.phonedirectory

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LockActivity : FragmentActivity() {

    private val prefs by lazy {
        getSharedPreferences("security", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Match the setting saved by SecurityActivity.kt
        if (!prefs.getBoolean("enabled", false)) {
            openMain()
            return
        }

        showLockScreen()
    }

    private fun showLockScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.rgb(15, 23, 42))
        }

        root.addView(TextView(this).apply {
            text = "HiPath Directory"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = "Unlock to continue"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 16, 0, 24)
        })

        val pinInput = EditText(this).apply {
            hint = "Enter PIN"
            inputType = 18
            setSingleLine(true)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.LTGRAY)
        }

        root.addView(pinInput, matchWidth())

        root.addView(Button(this).apply {
            text = "Unlock with PIN"
            setOnClickListener {
                val pin = pinInput.text.toString()
                if (verifyPin(pin)) {
                    openMain()
                } else {
                    pinInput.text.clear()
                    pinInput.error = "Incorrect PIN"
                }
            }
        }, matchWidth())

        val biometricEnabled = prefs.getBoolean("biometric", false)
        val biometricAvailable = biometricAvailable()

        root.addView(Button(this).apply {
            text = "Unlock with fingerprint"
            isEnabled = biometricEnabled && biometricAvailable
            setOnClickListener { authenticateBiometric() }
        }, matchWidth())

        if (biometricEnabled && !biometricAvailable) {
            root.addView(TextView(this).apply {
                text = "Fingerprint unavailable. Use your PIN."
                setTextColor(android.graphics.Color.LTGRAY)
                gravity = Gravity.CENTER
            })
        }

        setContentView(root)

        if (biometricEnabled && biometricAvailable) {
            // Show the real Android biometric prompt on the lock screen.
            authenticateBiometric()
        }
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 12
        }

    private fun biometricAvailable(): Boolean {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK

        return BiometricManager.from(this)
            .canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticateBiometric() {
        if (!prefs.getBoolean("biometric", false) || !biometricAvailable()) {
            Toast.makeText(
                this,
                "Fingerprint unavailable. Use your PIN.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    runOnUiThread { openMain() }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        this@LockActivity,
                        "Fingerprint not recognized",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(
                        this@LockActivity,
                        errString,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("HiPath Directory")
            .setSubtitle("Verify your identity")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(info)
    }

    private fun verifyPin(pin: String): Boolean {
        if (pin.isEmpty()) return false

        return try {
            val saltText = prefs.getString("salt", null) ?: return false
            val hashText = prefs.getString("hash", null) ?: return false

            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = hashPin(pin, salt)

            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
