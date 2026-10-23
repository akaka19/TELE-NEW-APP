
package com.company.phonedirectory

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class PersonDetailActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private var personId = 0L
    private var current: CompanyPerson? = null

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
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

        root.addView(label("PERSON PROFILE", 12f, true).apply {
            setTextColor(0xFF35D7F2.toInt())
        })

        root.addView(label("", 27f, true).also {
            it.tag = "personName"
        })

        root.addView(label("", 18f, true).also {
            it.tag = "personPhone"
            it.setTextColor(0xFF35D7F2.toInt())
        })

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "details"
        }
        root.addView(details, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "Appeler"
            isAllCaps = false
            setOnClickListener {
                current?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                    startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(it)}"))
                    )
                } ?: Toast.makeText(
                    this@PersonDetailActivity,
                    "Aucun numéro enregistré",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Copier le numéro"
            isAllCaps = false
            setOnClickListener {
                current?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Phone", it))
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
        if (::db.isInitialized) render()
    }

    private fun render() {
        val person = db.getPersonById(personId)
        if (person == null) {
            finish()
            return
        }
        current = person

        (findViewById<TextViewByTag>("personName"))
    }

    private fun findViewByIdTextByTag(tagValue: String): TextView? {
        val root = window.decorView
        return findTaggedText(root as? android.view.ViewGroup, tagValue)
    }

    private fun findTaggedText(
        group: android.view.ViewGroup?,
        tagValue: String
    ): TextView? {
        if (group == null) return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is TextView && child.tag == tagValue) return child
            if (child is android.view.ViewGroup) {
                val found = findTaggedText(child, tagValue)
                if (found != null) return found
            }
        }
        return null
    }

    private fun renderDetails(person: CompanyPerson) {
        findViewByIdTextByTag("personName")?.text = person.name
        findViewByIdTextByTag("personPhone")?.text = person.phoneNumber

        val details = findViewByIdLinearByTag("details") ?: return
        details.removeAllViews()

        val companyName = db.getCompanyById(person.companyId)?.name.orEmpty()
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
            card.addView(label(key.uppercase(), 11f, true).apply {
                setTextColor(0xFF35D7F2.toInt())
            })
            card.addView(label(value, 16f).apply {
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

    private fun findViewByIdLinearByTag(tagValue: String): LinearLayout? {
        return findTaggedLinear(window.decorView as? android.view.ViewGroup, tagValue)
    }

    private fun findTaggedLinear(
        group: android.view.ViewGroup?,
        tagValue: String
    ): LinearLayout? {
        if (group == null) return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is LinearLayout && child.tag == tagValue) return child
            if (child is android.view.ViewGroup) {
                val found = findTaggedLinear(child, tagValue)
                if (found != null) return found
            }
        }
        return null
    }
}
