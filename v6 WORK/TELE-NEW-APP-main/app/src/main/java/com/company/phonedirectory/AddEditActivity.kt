package com.company.phonedirectory
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class AddEditActivity : Activity() {
    private lateinit var db: DatabaseHelper
    private var contactId = 0L
    private lateinit var f: Map<String, EditText>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_add_edit); db = DatabaseHelper(this)
        contactId = intent.getLongExtra("contact_id", 0L)
        f = mapOf("phone" to findViewById(R.id.phoneInput), "name" to findViewById(R.id.nameInput),
            "beneficiary" to findViewById(R.id.beneficiaryInput), "service" to findViewById(R.id.serviceInput),
            "building" to findViewById(R.id.buildingInput), "floor" to findViewById(R.id.floorInput),
            "office" to findViewById(R.id.officeInput), "mailbox" to findViewById(R.id.mailboxInput),
            "hipathPort" to findViewById(R.id.hipathPortInput), "portH" to findViewById(R.id.portHInput))
        findViewById<TextView>(R.id.formTitle).text = if (contactId == 0L) "Add Contact" else "Edit Contact"
        if (contactId != 0L) db.getById(contactId)?.let { c ->
            listOf("phone" to c.phoneNumber, "name" to c.name, "beneficiary" to c.beneficiary, "service" to c.service, "building" to c.building, "floor" to c.floor, "office" to c.office, "mailbox" to c.mailbox, "hipathPort" to c.hipathPort, "portH" to c.portH).forEach { (k,v) -> f[k]?.setText(v) }
        }
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }
    }
    private fun save() {
        val number = f["phone"]?.text.toString().trim()
        if (number.isBlank()) { f["phone"]?.error = "Phone number / extension is required"; return }
        fun v(k: String) = f[k]?.text?.toString().orEmpty()
        val c = Contact(contactId, number, v("name"), v("beneficiary"), v("service"), v("building"), v("floor"), v("office"), v("mailbox"), v("hipathPort"), v("portH"))
        db.save(c).onSuccess { Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show(); finish() }
            .onFailure { Toast.makeText(this, it.message ?: "Could not save contact", Toast.LENGTH_LONG).show() }
    }
}
