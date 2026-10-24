
package com.company.phonedirectory

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class PersonDetailActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var nameView: TextView
    private lateinit var phoneView: TextView
    private lateinit var details: LinearLayout
    private var personId = 0L
    private var current: CompanyPerson? = null

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun makeText(
        value: String,
        size: Float,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (Ui.isDark(this@PersonDetailActivity)) Color.WHITE else Color.BLACK)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        personId = intent.getLongExtra("person_id", 0L)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            setBackgroundColor(Ui.background(this@PersonDetailActivity))
        }

        root.addView(makeText("PERSON PROFILE", 12f, true).apply {
            setTextColor(0xFF35D7F2.toInt())
        })

        nameView = makeText("", 27f, true)
        root.addView(nameView)

        phoneView = makeText("", 18f, true).apply {
            setTextColor(0xFF35D7F2.toInt())
        }
        root.addView(phoneView)

        val scroll = android.widget.ScrollView(this)
        details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(details)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "Appeler"
            isAllCaps = false
            setOnClickListener {
                val phone = current?.phoneNumber.orEmpty()
                if (phone.isNotBlank()) {
                    startActivity(
                        Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${Uri.encode(phone)}")
                        )
                    )
                } else {
                    Toast.makeText(
                        this@PersonDetailActivity,
                        "Aucun numéro enregistré",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Copier le numéro"
            isAllCaps = false
            setOnClickListener {
                val phone = current?.phoneNumber.orEmpty()
                if (phone.isNotBlank()) {
                    val clipboard =
                        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Phone", phone)
                    )
                    Toast.makeText(
                        this@PersonDetailActivity,
                        "Numéro copié",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Modifier"
            isAllCaps = false
            setOnClickListener {
                current?.let { p ->
                    startActivity(
                        Intent(
                            this@PersonDetailActivity,
                            PersonFormActivity::class.java
                        )
                            .putExtra("person_id", p.id)
                            .putExtra("company_id", p.companyId)
                            .putExtra(
                                "company_name",
                                db.getCompanyById(p.companyId)?.name.orEmpty()
                            )
                    )
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Supprimer"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@PersonDetailActivity)
                    .setTitle("Supprimer cette personne ?")
                    .setMessage("Cette action ne peut pas être annulée.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Supprimer") { _, _ ->
                        db.deletePerson(personId)
                        finish()
                    }
                    .show()
            }
        })

        root.addView(Button(this).apply {
            text = "Retour"
            isAllCaps = false
            setOnClickListener { finish() }
        })

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized && ::nameView.isInitialized) render()
    }

    private fun render() {
        val person = db.getPersonById(personId)
        if (person == null) {
            finish()
            return
        }

        current = person
        nameView.text = person.name
        phoneView.text = person.phoneNumber

        details.removeAllViews()

        val companyName =
            db.getCompanyById(person.companyId)?.name.orEmpty()

        val values = listOf(
            "Société" to companyName,
            "Service / Département" to person.service,
            "Informations supplémentaires" to person.notes
        )

        values.filter { it.second.isNotBlank() }.forEach { (key, value) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = Ui.card(this@PersonDetailActivity)
            }

            card.addView(makeText(key.uppercase(), 11f, true).apply {
                setTextColor(0xFF35D7F2.toInt())
            })

            card.addView(makeText(value, 16f).apply {
                setPadding(0, dp(5), 0, 0)
            })

            details.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
        }
    }
}
