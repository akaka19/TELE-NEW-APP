
package com.company.phonedirectory

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CompanyPeopleActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var items: LinearLayout
    private lateinit var search: EditText
    private var companyId = 0L
    private var companyName = ""

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun makeText(
        value: String,
        size: Float,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (Ui.isDark(this@CompanyPeopleActivity)) Color.WHITE else Color.BLACK)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = DatabaseHelper(this)
        companyId = intent.getLongExtra("company_id", 0L)
        companyName = intent.getStringExtra("company_name").orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Ui.background(this@CompanyPeopleActivity))
        }

        root.addView(makeText(companyName, 27f, true))
        root.addView(
            makeText("Contacts de la société", 14f).apply {
                setTextColor(Ui.muted(this@CompanyPeopleActivity))
            }
        )

        search = EditText(this).apply {
            hint = "Search name, phone or service..."
            setSingleLine(true)
            setTextColor(if (Ui.isDark(this@CompanyPeopleActivity)) Color.WHITE else Color.BLACK)
            setHintTextColor(Ui.muted(this@CompanyPeopleActivity))
        }
        root.addView(
            search,
            LinearLayout.LayoutParams(-1, dp(50)).apply {
                topMargin = dp(12)
                bottomMargin = dp(8)
            }
        )

        val scroll = ScrollView(this)
        items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(items)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "+  Ajouter une personne"
            isAllCaps = false
            setOnClickListener {
                startActivity(
                    Intent(
                        this@CompanyPeopleActivity,
                        PersonFormActivity::class.java
                    )
                        .putExtra("company_id", companyId)
                        .putExtra("company_name", companyName)
                )
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))

        root.addView(Button(this).apply {
            text = "Retour aux sociétés"
            isAllCaps = false
            setOnClickListener { finish() }
        })

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

    private fun render() {
        items.removeAllViews()
        val people = db.getPeople(companyId, search.text.toString())

        if (people.isEmpty()) {
            items.addView(
                makeText("Aucune personne enregistrée.", 15f).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(30), dp(12), dp(30))
                }
            )
            return
        }

        people.forEach { person ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(14), dp(12), dp(14))
                background = Ui.card(this@CompanyPeopleActivity)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(
                            this@CompanyPeopleActivity,
                            PersonDetailActivity::class.java
                        ).putExtra("person_id", person.id)
                    )
                }
            }

            card.addView(TextView(this).apply {
                text = "●"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(0xFF35D7F2.toInt())
            }, LinearLayout.LayoutParams(dp(40), dp(44)))

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
            }
            info.addView(makeText(person.name, 17f, true))

            if (person.phoneNumber.isNotBlank()) {
                info.addView(makeText(person.phoneNumber, 14f))
            }
            if (person.service.isNotBlank()) {
                info.addView(
                    makeText(person.service, 12f).apply {
                        setTextColor(Ui.muted(this@CompanyPeopleActivity))
                    }
                )
            }

            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(makeText("›", 26f, true))

            items.addView(
                card,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(9)
                }
            )
        }
    }
}
