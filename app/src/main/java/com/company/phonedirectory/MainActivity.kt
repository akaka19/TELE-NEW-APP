package com.company.phonedirectory
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
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
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        db = DatabaseHelper(this); list = findViewById(R.id.contactList); count = findViewById(R.id.contactCount)
        empty = findViewById(R.id.emptyText); search = findViewById(R.id.searchInput); applyTheme()
        findViewById<Button>(R.id.addButton).setOnClickListener { startActivity(Intent(this, AddEditActivity::class.java)) }
        findViewById<Button>(R.id.themeButton).setOnClickListener {
            getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("dark", !Ui.isDark(this)).apply(); recreate()
        }
        findViewById<Button>(R.id.securityButton).setOnClickListener { startActivity(Intent(this, SecurityActivity::class.java)) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); render() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    override fun onResume() { super.onResume(); if (::db.isInitialized) render() }
    private fun applyTheme() {
        val dark = Ui.isDark(this); findViewById<View>(R.id.mainRoot).setBackgroundColor(Ui.background(this))
        findViewById<Button>(R.id.themeButton).text = if (dark) "☼" else "☾"
        search.setTextColor(Ui.text(this)); search.setHintTextColor(Ui.muted(this)); count.setTextColor(Ui.text(this))
    }
    private fun render() {
        val rows = db.getAll(query); count.text = rows.size.toString(); list.removeAllViews()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        rows.forEach { c ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(14), dp(12), dp(14)); background = Ui.card(this@MainActivity); isClickable = true; setOnClickListener { startActivity(Intent(this@MainActivity, ContactDetailActivity::class.java).putExtra("contact_id", c.id)) } }
            card.addView(TextView(this).apply { text = c.phoneNumber.take(4); gravity = Gravity.CENTER; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF35D7F2.toInt()) }, LinearLayout.LayoutParams(dp(52), dp(48)))
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
            details.addView(TextView(this).apply { text = c.name.ifBlank { c.beneficiary.ifBlank { "Extension ${c.phoneNumber}" } }; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Ui.text(this@MainActivity)) })
            details.addView(TextView(this).apply { text = listOf(c.phoneNumber, c.service, if (c.office.isNotBlank()) "Office ${c.office}" else "").filter { it.isNotBlank() }.joinToString("  •  "); textSize = 12f; setTextColor(Ui.muted(this@MainActivity)); setPadding(0, dp(5), 0, 0) })
            card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply { text = "›"; textSize = 28f; setTextColor(0xFF35D7F2.toInt()); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(24), dp(40)))
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
