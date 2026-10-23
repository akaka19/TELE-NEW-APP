
package com.company.phonedirectory

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
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

        // If no PIN has been configured, continue to the main screen.
        if (!prefs.getBoolean("pin_enabled", false)) {
            openMain()
            return
        }

        showLockScreen()
    }

    private fun showLockScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(android.graphics.Color.rgb(15, 23, 42))
        }

        val title = TextView(this).apply {
            text = "HiPath Directory"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        }

        val subtitle = TextView(this).apply {
            text = "Unlock to continue"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 16, 0, 28)
        }

        val pinInput = android.widget.EditText(this).apply {
            hint = "Enter PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.LTGRAY)
        }

        val unlockButton = Button(this).apply {
            text = "Unlock with PIN"
            setOnClickListener {
                val pin = pinInput.text.toString()
                if (pin.isBlank()) {
                    pinInput.error = "Enter your PIN"
                } else if (verifyPin(pin)) {
                    openMain()
                } else {
                    pinInput.text?.clear()
                    Toast.makeText(
                        this@LockActivity,
                        "Incorrect PIN",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val biometricButton = Button(this).apply {
            text = "Unlock with fingerprint"
            setOnClickListener {
                authenticateBiometric()
            }
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            pinInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            unlockButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            biometricButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        if (!biometricAvailable()) {
            biometricButton.isEnabled = false
            biometricButton.text = "Fingerprint unavailable — use PIN"
        }
    }

    private fun biometricAvailable(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticateBiometric() {
        if (!biometricAvailable()) {
            Toast.makeText(
                this,
                "Fingerprint is unavailable. Use your PIN.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)

        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    openMain()
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

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("HiPath Directory")
            .setSubtitle("Verify your identity")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun verifyPin(pin: String): Boolean {
        return try {
            val saltString = prefs.getString("salt", null) ?: return false
            val hashString = prefs.getString("hash", null) ?: return false

            val salt = Base64.decode(saltString, Base64.NO_WRAP)
            val expectedHash = Base64.decode(hashString, Base64.NO_WRAP)

            val spec = PBEKeySpec(
                pin.toCharArray(),
                salt,
                120000,
                256
            )

            val actualHash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded

            spec.clearPassword()

            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
