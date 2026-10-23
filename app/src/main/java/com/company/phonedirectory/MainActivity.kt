
package com.company.phonedirectory

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private lateinit var directoryTitle: TextView
    private lateinit var contactsTitle: TextView

    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)

        list = findViewById(R.id.contactList)
        count = findViewById(R.id.contactCount)
        empty = findViewById(R.id.emptyText)
        search = findViewById(R.id.searchInput)
        directoryTitle = findViewById(R.id.directoryTitle)
        contactsTitle = findViewById(R.id.contactsTitle)

        applyTheme()

        findViewById<Button>(R.id.addButton).setOnClickListener {
            startActivity(Intent(this, AddEditActivity::class.java))
        }

        findViewById<Button>(R.id.themeButton).setOnClickListener {
            val newDarkMode = !Ui.isDark(this)

            getSharedPreferences("ui", MODE_PRIVATE)
                .edit()
                .putBoolean("dark", newDarkMode)
                .apply()

            recreate()
        }

        findViewById<Button>(R.id.securityButton).setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                query = s?.toString().orEmpty()
                render()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) render()
    }

    private fun applyTheme() {
        val dark = Ui.isDark(this)
        val textColor = if (dark) Color.WHITE else Color.BLACK

        findViewById<View>(R.id.mainRoot)
            .setBackgroundColor(Ui.background(this))

        directoryTitle.setTextColor(textColor)
        contactsTitle.setTextColor(textColor)
        count.setTextColor(textColor)
        empty.setTextColor(if (dark) Color.WHITE else Color.DKGRAY)

        search.setTextColor(textColor)
        search.setHintTextColor(Ui.muted(this))

        findViewById<Button>(R.id.themeButton).text =
            if (dark) "☼" else "☾"
    }

    private fun render() {
        val contacts = db.getAll(query)

        count.text = contacts.size.toString()
        list.removeAllViews()

        empty.visibility =
            if (contacts.isEmpty()) View.VISIBLE else View.GONE

        if (contacts.isEmpty()) return

        val dark = Ui.isDark(this)
        val textColor = if (dark) Color.WHITE else Color.BLACK
        val mutedColor = if (dark) {
            Ui.muted(this)
        } else {
            Color.DKGRAY
        }

        var index = 0

        while (index < contacts.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                weightSum = 3f
            }

            for (column in 0 until 3) {
                if (index < contacts.size) {
                    val contact = contacts[index]

                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(5), dp(12), dp(5), dp(12))
                        background = Ui.card(this@MainActivity)
                        isClickable = true
                        isFocusable = true

                        setOnClickListener {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    ContactDetailActivity::class.java
                                ).putExtra("contact_id", contact.id)
                            )
                        }
                    }

                    val phoneIcon = TextView(this).apply {
                        text = "☎"
                        textSize = 25f
                        gravity = Gravity.CENTER
                        setTextColor(0xFF35D7F2.toInt())
                    }

                    val number = TextView(this).apply {
                        text = contact.phoneNumber
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                        maxLines = 1
                    }

                    val label = TextView(this).apply {
                        text = contact.name.ifBlank {
                            contact.service.ifBlank {
                                contact.beneficiary.ifBlank {
                                    "Extension"
                                }
                            }
                        }
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(mutedColor)
                        maxLines = 2
                    }

                    card.addView(
                        phoneIcon,
                        LinearLayout.LayoutParams(-1, dp(34))
                    )

                    card.addView(
                        number,
                        LinearLayout.LayoutParams(-1, -2).apply {
                            topMargin = dp(5)
                        }
                    )

                    card.addView(
                        label,
                        LinearLayout.LayoutParams(-1, -2).apply {
                            topMargin = dp(4)
                        }
                    )

                    val cardParams = LinearLayout.LayoutParams(
                        0, dp(112), 1f
                    ).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(8))
                    }

                    row.addView(card, cardParams)
                    index++
                } else {
                    row.addView(
                        View(this),
                        LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(8))
                        }
                    )
                }
            }

            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
