
package com.company.phonedirectory

import android.app.Activity
import android.content.Intent
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

    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)

        list = findViewById(R.id.contactList)
        count = findViewById(R.id.contactCount)
        empty = findViewById(R.id.emptyText)
        search = findViewById(R.id.searchInput)

        applyTheme()

        findViewById<Button>(R.id.addButton).setOnClickListener {
            startActivity(
                Intent(this, AddEditActivity::class.java)
            )
        }

        findViewById<Button>(R.id.themeButton).setOnClickListener {
            getSharedPreferences("ui", MODE_PRIVATE)
                .edit()
                .putBoolean("dark", !Ui.isDark(this))
                .apply()

            recreate()
        }

        findViewById<Button>(R.id.securityButton).setOnClickListener {
            startActivity(
                Intent(this, SecurityActivity::class.java)
            )
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int,
                count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int,
                before: Int, count: Int
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

        findViewById<View>(R.id.mainRoot)
            .setBackgroundColor(Ui.background(this))

        findViewById<Button>(R.id.themeButton).text =
            if (dark) "☼" else "☾"

        search.setTextColor(Ui.text(this))
        search.setHintTextColor(Ui.muted(this))
        count.setTextColor(Ui.text(this))
        empty.setTextColor(Ui.muted(this))
    }

    private fun render() {
        val contacts = db.getAll(query)

        count.text = contacts.size.toString()
        list.removeAllViews()

        empty.visibility =
            if (contacts.isEmpty()) View.VISIBLE else View.GONE

        if (contacts.isEmpty()) return

        // Three cards in each row
        val columns = 3
        var index = 0

        while (index < contacts.size) {

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                weightSum = columns.toFloat()
            }

            for (column in 0 until columns) {

                if (index >= contacts.size) {
                    // Empty space to keep the grid aligned
                    row.addView(
                        View(this),
                        LinearLayout.LayoutParams(0, 1, 1f)
                    )
                    continue
                }

                val contact = contacts[index++]

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(4), dp(12), dp(4), dp(12)
                    )
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

                // Phone icon, but no call button
                val icon = TextView(this).apply {
                    text = "☎"
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF35D7F2.toInt())
                }

                // Extension number
                val number = TextView(this).apply {
                    text = contact.phoneNumber
                    textSize = 17f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(Ui.text(this@MainActivity))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                // Name or short description
                val label = TextView(this).apply {
                    text = contact.name
                        .ifBlank {
                            contact.service.ifBlank {
                                contact.beneficiary.ifBlank {
                                    "Extension"
                                }
                            }
                        }

                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setTextColor(Ui.muted(this@MainActivity))
                }

                card.addView(
                    icon,
                    LinearLayout.LayoutParams(-1, dp(32))
                )

                card.addView(
                    number,
                    LinearLayout.LayoutParams(-1, dp(28))
                )

                card.addView(
                    label,
                    LinearLayout.LayoutParams(-1, dp(32))
                )

                val params = LinearLayout.LayoutParams(
                    0, dp(112), 1f
                ).apply {
                    leftMargin = if (column == 0) 0 else dp(4)
                    rightMargin = if (column == 2) 0 else dp(4)
                    bottomMargin = dp(8)
                }

                row.addView(card, params)
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
