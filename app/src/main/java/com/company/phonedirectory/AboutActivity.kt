
package com.company.phonedirectory

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dark = Ui.isDark(this)
        val backgroundColor = Ui.background(this)
        val textColor = if (dark) Color.WHITE else Color.rgb(25, 35, 50)
        val accent = Color.rgb(53, 215, 242)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "About"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = Gravity.CENTER
        }

        val appName = TextView(this).apply {
            text = "HiPath Directory"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(12))
        }

        val version = TextView(this).apply {
            text = "Version 1.0.5"
            textSize = 14f
            setTextColor(Ui.muted(this@AboutActivity))
            gravity = Gravity.CENTER
        }

        val developedBy = TextView(this).apply {
            text = "Developed by MED-K-97"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(32), 0, dp(8))
        }

        val backButton = TextView(this).apply {
            text = "←  Back"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(14), dp(24), dp(14))
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(appName)
        root.addView(version)
        root.addView(developedBy)

        root.addView(
            backButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(36)
            }
        )

        setContentView(root)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
