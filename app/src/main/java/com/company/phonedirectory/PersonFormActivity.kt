
package com.company.phonedirectory

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class PersonFormActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var fields: Map<String, EditText>
    private var companyId = 0L
    private var personId = 0L
    private var companyName = ""

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)

        companyId = intent.getLongExtra("company_id", 0L)
        personId = intent.getLongExtra("person_id", 0L)
        companyName = intent.getStringExtra("company_name").orEmpty()

        val dark = Ui.isDark(this)
        val textColor = if (dark) Color.WHITE else Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Ui.background(this@PersonFormActivity))
        }

        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        form.addView(TextView(this).apply {
            text = if (personId == 0L) "Ajouter une personne" else "Modifier la personne"
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
        })

        form.addView(TextView(this).apply {
            text = "Société : $companyName"
            textSize = 15f
            setTextColor(Ui.muted(this@PersonFormActivity))
            setPadding(0, dp(8), 0, dp(12))
        })

        fun field(key: String, hint: String, phone: Boolean = false): EditText {
            val edit = EditText(this).apply {
                this.hint = hint
                textSize = 16f
                setTextColor(textColor)
                setHintTextColor(Ui.muted(this@PersonFormActivity))
                setPadding(dp(13), dp(8), dp(13), dp(8))
                if (phone) {
                    inputType = InputType.TYPE_CLASS_PHONE
                } else {
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                if (key != "notes") {
                    setSingleLine(true)
                } else {
                    minLines = 3
                    gravity = android.view.Gravity.TOP
                }
                background = Ui.card(this@PersonFormActivity)
            }
            fieldsMap[key] = edit
            form.addView(
                edit,
                LinearLayout.LayoutParams(-1, if (key == "notes") dp(100) else dp(54)).apply {
                    bottomMargin = dp(10)
                }
            )
            return edit
        }

        fieldsMap = linkedMapOf()
        field("name", "Nom et prénom *")
        field("phone", "Numéro de téléphone", true)
        field("service", "Service / Département")
        field("notes", "Informations supplémentaires")

        scroll.addView(form)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "Enregistrer"
            isAllCaps = false
            setOnClickListener { savePerson() }
        }, LinearLayout.LayoutParams(-1, dp(52)))

        root.addView(Button(this).apply {
            text = "Annuler"
            isAllCaps = false
            setOnClickListener { finish() }
        })

        setContentView(root)

        if (personId != 0L) {
            val person = db.getPersonById(personId)
            if (person == null) {
                finish()
                return
            }
            fieldsMap["name"]?.setText(person.name)
            fieldsMap["phone"]?.setText(person.phoneNumber)
            fieldsMap["service"]?.setText(person.service)
            fieldsMap["notes"]?.setText(person.notes)
            companyId = person.companyId
        }

        fields = fieldsMap
    }

    private val fieldsMap = linkedMapOf<String, EditText>()

    private fun savePerson() {
        val name = fields["name"]?.text?.toString()?.trim().orEmpty()
        val phone = fields["phone"]?.text?.toString()?.trim().orEmpty()
        val service = fields["service"]?.text?.toString()?.trim().orEmpty()
        val notes = fields["notes"]?.text?.toString()?.trim().orEmpty()

        if (name.isBlank()) {
            fields["name"]?.error = "Le nom est obligatoire"
            return
        }

        val person = CompanyPerson(
            id = personId,
            companyId = companyId,
            name = name,
            phoneNumber = phone,
            service = service,
            notes = notes
        )

        db.savePerson(person)
            .onSuccess {
                Toast.makeText(
                    this, "Personne enregistrée", Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .onFailure {
                Toast.makeText(
                    this,
                    it.message ?: "Impossible d'enregistrer",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
