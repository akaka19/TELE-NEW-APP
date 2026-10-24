
package com.company.phonedirectory

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SocietyActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var items: LinearLayout
    private lateinit var search: EditText

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun makeText(
        value: String,
        size: Float,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (Ui.isDark(this@SocietyActivity)) Color.WHITE else Color.BLACK)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Ui.background(this@SocietyActivity))
        }

        root.addView(makeText("Société", 29f, true))
        root.addView(
            makeText("Manage companies and their contacts", 14f).apply {
                setTextColor(Ui.muted(this@SocietyActivity))
            }
        )

        search = EditText(this).apply {
            hint = "Search company..."
            setSingleLine(true)
            setTextColor(if (Ui.isDark(this@SocietyActivity)) Color.WHITE else Color.BLACK)
            setHintTextColor(Ui.muted(this@SocietyActivity))
            setPadding(dp(12), 0, dp(12), 0)
        }
        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(14)
                bottomMargin = dp(10)
            }
        )

        val scroll = ScrollView(this)
        items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(items)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val add = Button(this).apply {
            text = "+  Ajouter une société"
            isAllCaps = false
            setOnClickListener { addCompanyDialog() }
        }
        root.addView(add, LinearLayout.LayoutParams(-1, dp(52)))

        val back = Button(this).apply {
            text = "Retour au Directory"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        root.addView(back)

        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                render()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        render()
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized && ::items.isInitialized) render()
    }

    private fun addCompanyDialog() {
        val input = EditText(this).apply {
            hint = "Company name (e.g. IAM, INWI)"
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Ajouter une société")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(
                        this, "Enter a company name", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    db.saveCompany(name = name)
                        .onSuccess { render() }
                        .onFailure {
                            Toast.makeText(
                                this,
                                it.message ?: "Could not save company",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
            .show()
    }

    private fun render() {
        items.removeAllViews()
        val companies = db.getCompanies(search.text.toString())

        if (companies.isEmpty()) {
            items.addView(
                makeText(
                    "No companies yet. Tap Ajouter une société to start.",
                    15f
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(18), dp(32), dp(18), dp(32))
                }
            )
            return
        }

        companies.forEach { company ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(16))
                background = Ui.card(this@SocietyActivity)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(
                            this@SocietyActivity,
                            CompanyPeopleActivity::class.java
                        )
                            .putExtra("company_id", company.id)
                            .putExtra("company_name", company.name)
                    )
                }
            }

            val icon = TextView(this).apply {
                text = "▦"
                textSize = 29f
                gravity = Gravity.CENTER
                setTextColor(0xFF35D7F2.toInt())
            }
            card.addView(
                icon,
                LinearLayout.LayoutParams(dp(48), dp(48))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(4), 0)
            }
            info.addView(makeText(company.name, 18f, true))
            info.addView(
                makeText(
                    "${db.getPeople(company.id).size} contacts",
                    13f
                ).apply {
                    setTextColor(Ui.muted(this@SocietyActivity))
                }
            )
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(makeText("›", 28f, true))

            items.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                }
            )

            card.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(company.name)
                    .setItems(arrayOf("Renommer", "Supprimer")) { _, which ->
                        if (which == 0) renameCompany(company.id, company.name)
                        else confirmDeleteCompany(company.id, company.name)
                    }
                    .show()
                true
            }
        }
    }

    private fun renameCompany(id: Long, oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Renommer la société")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                db.saveCompany(id, input.text.toString())
                    .onSuccess { render() }
                    .onFailure {
                        Toast.makeText(
                            this, it.message ?: "Error", Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    private fun confirmDeleteCompany(id: Long, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer $name ?")
            .setMessage(
                "Les personnes enregistrées dans cette société seront également supprimées."
            )
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                db.deleteCompany(id)
                render()
            }
            .show()
    }
}
