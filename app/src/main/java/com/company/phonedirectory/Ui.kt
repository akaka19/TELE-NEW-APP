package com.company.phonedirectory
import android.content.Context
import android.graphics.drawable.GradientDrawable
object Ui {
    fun isDark(c: Context) = c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("dark", true)
    fun background(c: Context) = if (isDark(c)) 0xFF0B1020.toInt() else 0xFFF4F7FC.toInt()
    fun surface(c: Context) = if (isDark(c)) 0xFF151E31.toInt() else 0xFFFFFFFF.toInt()
    fun text(c: Context) = if (isDark(c)) 0xFFF4F7FF.toInt() else 0xFF182238.toInt()
    fun muted(c: Context) = if (isDark(c)) 0xFF9AA9C2.toInt() else 0xFF66748A.toInt()
    fun card(c: Context) = GradientDrawable().apply { setColor(surface(c)); cornerRadius = c.resources.displayMetrics.density * 15 }
}
