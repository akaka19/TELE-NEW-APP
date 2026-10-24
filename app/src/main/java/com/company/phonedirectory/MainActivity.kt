
package com.company.phonedirectory

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var db: DatabaseHelper
    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private lateinit var directoryTitle: TextView
    private lateinit var contactsTitle: TextView
    private lateinit var appUpdater: AppUpdater

    private var query = ""
    private var menuPopup: PopupWindow? = null

    private val accent = Color.rgb(53, 215, 242)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        appUpdater = AppUpdater(this)

        list = findViewById(R.id.contactList)
        count = findViewById(R.id.contactCount)
        empty = findViewById(R.id.emptyText)
        search = findViewById(R.id.searchInput)
        directoryTitle = findViewById(R.id.directoryTitle)
        contactsTitle = findViewById(R.id.contactsTitle)

        applyTheme()

        // Check GitHub Releases when the app opens.
        appUpdater.checkForUpdate()

        findViewById<Button>(R.id.addButton).setOnClickListener {
            startActivity(Intent(this, AddEditActivity::class.java))
        }

        findViewById<Button>(R.id.menuButton).setOnClickListener {
            showSideMenu()
        }

        findViewById<Button>(R.id.themeButton).setOnClickListener {
            val newDarkMode = !Ui.isDark(this)

            getSharedPreferences("ui", MODE_PRIVATE)
                .edit()
                .putBoolean("dark", newDarkMode)
                .apply()

            recreate()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                query = s?.toString().orEmpty()
                render()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showSideMenu() {
        if (menuPopup?.isShowing == true) return

        val dark = Ui.isDark(this)

        val panelColor = if (dark) {
            Color.rgb(15, 23, 42)
        } else {
            Color.rgb(245, 247, 252)
        }

        val textColor = if (dark) Color.WHITE else Color.BLACK

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(36), dp(18), dp(18))
            setBackgroundColor(panelColor)
        }

        panel.addView(TextView(this).apply {
            text = "HIPATH"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(0, 0, 0, dp(24))
        })

        addMenuItem(panel, "☎", "SIP Calling", textColor) {
            menuPopup?.dismiss()
            startActivity(Intent(this, SIPActivity::class.java))
        }

        addMenuItem(panel, "▣", "Société", textColor) {
            menuPopup?.dismiss()
            startActivity(Intent(this, SocietyActivity::class.java))
        }

        addMenuItem(panel, "⇧", "Import Contacts", textColor) {
            menuPopup?.dismiss()
            startActivity(
                Intent(this, ImportExportActivity::class.java)
                    .putExtra("mode", "import")
            )
        }

        addMenuItem(panel, "⇩", "Export Contacts", textColor) {
            menuPopup?.dismiss()
            startActivity(
                Intent(this, ImportExportActivity::class.java)
                    .putExtra("mode", "export")
            )
        }

        addMenuItem(panel, "⚙", "Settings", textColor) {
            menuPopup?.dismiss()
            startActivity(Intent(this, SecurityActivity::class.java))
        }

        // About page
        addMenuItem(panel, "ⓘ", "About", textColor) {
            menuPopup?.dismiss()
            startActivity(Intent(this, AboutActivity::class.java))
        }

        panel.addView(
            View(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        panel.addView(TextView(this).apply {
            text = "HIPATH / SYSTEMS"
            textSize = 11f
            setTextColor(Ui.muted(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(10))
        })

        val popup = PopupWindow(
            panel,
            dp(280),
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        )

        popup.setBackgroundDrawable(ColorDrawable(panelColor))
        popup.isOutsideTouchable = true
        popup.elevation = dp(18).toFloat()
        popup.setOnDismissListener { menuPopup = null }

        menuPopup = popup

        popup.showAtLocation(
            findViewById(R.id.mainRoot),
            Gravity.LEFT or Gravity.TOP,
            0,
            0
        )

        panel.startAnimation(
            TranslateAnimation(
                -dp(280).toFloat(),
                0f,
                0f,
                0f
            ).apply {
                duration = 220
                fillAfter = false
            }
        )
    }

    private fun addMenuItem(
        panel: LinearLayout,
        symbol: String,
        title: String,
        textColor: Int,
        action: () -> Unit
    ) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = Ui.card(this@MainActivity)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

        item.addView(
            TextView(this).apply {
                text = symbol
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(accent)
            },
            LinearLayout.LayoutParams(dp(38), dp(52))
        )

        item.addView(
            TextView(this).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )

        panel.addView(
            item,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) render()
    }

    override fun onDestroy() {
        menuPopup?.dismiss()
        menuPopup = null

        if (::appUpdater.isInitialized) {
            appUpdater.cleanup()
        }

        super.onDestroy()
    }

    private fun applyTheme() {
        val dark = Ui.isDark(this)

        val textColor = if (dark) {
            Color.WHITE
        } else {
            Color.rgb(25, 35, 50)
        }

        findViewById<View>(R.id.mainRoot)
            .setBackgroundColor(Ui.background(this))

        directoryTitle.setTextColor(textColor)
        contactsTitle.setTextColor(textColor)
        count.setTextColor(textColor)

        empty.setTextColor(
            if (dark) Color.WHITE else Color.DKGRAY
        )

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

        val textColor = if (dark) {
            Color.WHITE
        } else {
            Color.rgb(25, 35, 50)
        }

        val mutedColor = if (dark) {
            Color.rgb(160, 174, 192)
        } else {
            Color.rgb(95, 108, 125)
        }

        var index = 0

        while (index < contacts.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                weightSum = 3f
            }

            for (column in 0 until 3) {
                if (index >= contacts.size) {
                    row.addView(
                        View(this),
                        LinearLayout.LayoutParams(
                            0,
                            dp(142),
                            1f
                        ).apply {
                            setMargins(dp(4), dp(5), dp(4), dp(7))
                        }
                    )
                    continue
                }

                val contact = contacts[index]

                val label = contact.name.ifBlank {
                    contact.service.ifBlank {
                        contact.beneficiary.ifBlank {
                            "Extension"
                        }
                    }
                }

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(7), dp(12), dp(7), dp(10))
                    background = Ui.card(this@MainActivity)
                    elevation = dp(2).toFloat()
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

                val iconHolder = TextView(this).apply {
                    text = "☎"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(accent)

                    background = roundedBackground(
                        if (dark) {
                            Color.rgb(25, 49, 68)
                        } else {
                            Color.rgb(225, 246, 251)
                        },
                        13
                    )
                }

                val number = TextView(this).apply {
                    text = contact.phoneNumber
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    maxLines = 1
                    setPadding(0, dp(7), 0, 0)
                    includeFontPadding = false
                }

                val separator = View(this).apply {
                    setBackgroundColor(
                        if (dark) {
                            Color.rgb(55, 72, 91)
                        } else {
                            Color.rgb(225, 231, 239)
                        }
                    )
                }

                val name = TextView(this).apply {
                    text = label
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(mutedColor)
                    maxLines = 2
                    setPadding(dp(1), dp(7), dp(1), 0)
                }

                card.addView(
                    iconHolder,
                    LinearLayout.LayoutParams(dp(42), dp(42))
                )

                card.addView(
                    number,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                card.addView(
                    separator,
                    LinearLayout.LayoutParams(
                        dp(34),
                        dp(2)
                    ).apply {
                        topMargin = dp(8)
                    }
                )

                card.addView(
                    name,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )

                row.addView(
                    card,
                    LinearLayout.LayoutParams(
                        0,
                        dp(142),
                        1f
                    ).apply {
                        setMargins(dp(4), dp(5), dp(4), dp(7))
                    }
                )

                index++
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

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
