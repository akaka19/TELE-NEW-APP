
package com.company.phonedirectory

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ContactDetailActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private var contactId = 0L
    private var current: Contact? = null
    private lateinit var fieldsBox: LinearLayout

    private val cyan = Color.rgb(53, 215, 242)
    private val green = Color.rgb(34, 197, 139)
    private val red = Color.rgb(239, 83, 80)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_detail)

        db = DatabaseHelper(this)
        contactId = intent.getLongExtra("contact_id", 0L)
        fieldsBox = findViewById(R.id.detailFields)

        findViewById<Button>(R.id.callButton).setOnClickListener {
            current?.let { contact ->
                startActivity(
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:${Uri.encode(contact.phoneNumber)}")
                    )
                )
            }
        }

        findViewById<Button>(R.id.sipCallButton).setOnClickListener {
            current?.let { contact ->
                startActivity(
                    Intent(this, SIPActivity::class.java)
                        .putExtra("dial_number", contact.phoneNumber)
                )
            }
        }

        findViewById<Button>(R.id.copyButton).setOnClickListener {
            current?.let { contact ->
                val clipboard =
                    getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Phone", contact.phoneNumber)
                )

                Toast.makeText(
                    this,
                    "Number copied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<Button>(R.id.editButton).setOnClickListener {
            startActivity(
                Intent(this, AddEditActivity::class.java)
                    .putExtra("contact_id", contactId)
            )
        }

        findViewById<Button>(R.id.deleteButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete contact?")
                .setMessage("Are you sure you want to delete this contact?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    db.delete(contactId)

                    Toast.makeText(
                        this,
                        "Contact deleted",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
                .show()
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) {
            render()
        }
    }

    private fun render() {
        current = db.getById(contactId)

        val c = current ?: run {
            finish()
            return
        }

        val title = c.name.ifBlank {
            c.beneficiary.ifBlank {
                c.service.ifBlank { "Contact details" }
            }
        }

        findViewById<TextView>(R.id.detailName).apply {
            text = title
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@ContactDetailActivity))
        }

        findViewById<TextView>(R.id.detailPhone).apply {
            text = c.phoneNumber
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        }

        styleButton(findViewById(R.id.callButton), green)
        styleButton(findViewById(R.id.sipCallButton), green)
        styleButton(findViewById(R.id.copyButton), cyan)
        styleButton(findViewById(R.id.editButton), cyan)
        styleButton(findViewById(R.id.deleteButton), red)

        fieldsBox.removeAllViews()

        val values = listOf(
            Triple("CONTACT", "Name", c.name),
            Triple("PERSON", "Beneficiary", c.beneficiary),
            Triple("ORGANIZATION", "Service / Department", c.service),
            Triple("LOCATION", "Building", c.building),
            Triple("LOCATION", "Floor", c.floor),
            Triple("LOCATION", "Office", c.office),
            Triple("TELEPHONE", "Boîte / Mailbox", c.mailbox),
            Triple("SYSTEM", "HiPath Port", c.hipathPort),
            Triple("SYSTEM", "Port H", c.portH)
        )

        values.filter { it.third.isNotBlank() }.forEach { item ->
            fieldsBox.addView(
                createDetailCard(
                    item.second,
                    item.third,
                    item.first
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }

        if (fieldsBox.childCount == 0) {
            fieldsBox.addView(
                TextView(this).apply {
                    text = "No additional details available."
                    textSize = 14f
                    setTextColor(Ui.muted(this@ContactDetailActivity))
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(24), dp(12), dp(24))
                }
            )
        }
    }

    private fun createDetailCard(
        label: String,
        value: String,
        category: String
    ): LinearLayout {

        val dark = Ui.isDark(this)

        val cardColor = if (dark) {
            Color.rgb(30, 41, 59)
        } else {
            Color.WHITE
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(cardColor, 16)
        }

        val icon = TextView(this).apply {
            text = when (category) {
                "CONTACT" -> "●"
                "PERSON" -> "♙"
                "ORGANIZATION" -> "▦"
                "LOCATION" -> "⌖"
                "TELEPHONE" -> "☎"
                else -> "◇"
            }

            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(cyan)

            val iconColor = if (dark) {
                Color.rgb(25, 49, 68)
            } else {
                Color.rgb(225, 246, 251)
            }

            background = roundedBackground(iconColor, 12)
        }

        card.addView(
            icon,
            LinearLayout.LayoutParams(dp(44), dp(44))
        )

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        textBox.addView(
            TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Ui.muted(this@ContactDetailActivity))
            }
        )

        textBox.addView(
            TextView(this).apply {
                text = value
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(this@ContactDetailActivity))
                setPadding(0, dp(4), 0, 0)
            }
        )

        card.addView(
            textBox,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        return card
    }

    private fun styleButton(button: Button, buttonColor: Int) {
        button.setTextColor(
            if (buttonColor == cyan) {
                Color.rgb(8, 24, 38)
            } else {
                Color.WHITE
            }
        )

        button.typeface = Typeface.DEFAULT_BOLD
        button.background = roundedBackground(buttonColor, 12)
        button.isAllCaps = false
        button.minHeight = dp(46)
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
