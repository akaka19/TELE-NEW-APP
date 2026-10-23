package com.company.phonedirectory
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ContactDetailActivity : Activity() {
    private lateinit var db: DatabaseHelper
    private var contactId = 0L
    private var current: Contact? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_contact_detail)
        db = DatabaseHelper(this); contactId = intent.getLongExtra("contact_id", 0L)
        findViewById<Button>(R.id.callButton).setOnClickListener { current?.let { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(it.phoneNumber)}"))) } }
        findViewById<Button>(R.id.copyButton).setOnClickListener { current?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Phone", it.phoneNumber)); Toast.makeText(this, "Number copied", Toast.LENGTH_SHORT).show() } }
        findViewById<Button>(R.id.editButton).setOnClickListener { startActivity(Intent(this, AddEditActivity::class.java).putExtra("contact_id", contactId)) }
        findViewById<Button>(R.id.deleteButton).setOnClickListener { AlertDialog.Builder(this).setTitle("Delete contact?").setMessage("This action cannot be undone.").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> db.delete(contactId); finish() }.show() }
        render()
    }
    override fun onResume() { super.onResume(); if (::db.isInitialized) render() }
    private fun render() {
        current = db.getById(contactId); val c = current ?: run { finish(); return }
        findViewById<TextView>(R.id.detailName).text = c.name.ifBlank { c.beneficiary.ifBlank { "Contact details" } }
        findViewById<TextView>(R.id.detailPhone).text = c.phoneNumber
        val box = findViewById<LinearLayout>(R.id.detailFields); box.removeAllViews()
        val values = listOf("Name" to c.name, "Beneficiary" to c.beneficiary, "Service / Department" to c.service, "Building" to c.building, "Floor" to c.floor, "Office" to c.office, "Boîte / Mailbox" to c.mailbox, "HiPath Port" to c.hipathPort, "Port H" to c.portH)
        values.filter { it.second.isNotBlank() }.forEach { (label,value) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = Ui.card(this@ContactDetailActivity) }
            row.addView(TextView(this).apply { text = label.uppercase(); textSize = 10f; setTextColor(0xFF35D7F2.toInt()) })
            row.addView(TextView(this).apply { text = value; textSize = 16f; setTextColor(Ui.text(this@ContactDetailActivity)); setPadding(0, dp(4), 0, 0) })
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
