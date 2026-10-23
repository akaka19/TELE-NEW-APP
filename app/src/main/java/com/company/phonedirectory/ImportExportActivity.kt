
package com.company.phonedirectory

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

class ImportExportActivity : Activity() {

    private lateinit var db: DatabaseHelper

    private val CREATE_CSV = 1001
    private val OPEN_CSV = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = DatabaseHelper(this)

        val dark = Ui.isDark(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(
                if (dark) Color.rgb(13, 20, 36) else Color.WHITE
            )
        }

        val title = TextView(this).apply {
            text = "Import / Export Contacts"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Transfer your phone directory using CSV files."
            textSize = 14f
            setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        }

        val exportButton = Button(this).apply {
            text = "Export Contacts (CSV)"
            setOnClickListener { exportContacts() }
        }

        val importButton = Button(this).apply {
            text = "Import Contacts (CSV)"
            setOnClickListener { importContacts() }
        }

        val backButton = Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        }

        root.addView(title, buttonParams())
        root.addView(subtitle, buttonParams())
        root.addView(exportButton, buttonParams())
        root.addView(importButton, buttonParams())
        root.addView(backButton, buttonParams())

        setContentView(root)

        when (intent.getStringExtra("mode")) {
            "import" -> root.post { importContacts() }
            "export" -> root.post { exportContacts() }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }

    private fun exportContacts() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "phone_directory.csv")
        }

        startActivityForResult(intent, CREATE_CSV)
    }

    private fun importContacts() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/vnd.ms-excel",
                    "text/plain"
                )
            )
        }

        startActivityForResult(intent, OPEN_CSV)
    }

    @Deprecated("Uses the compatible Activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return

        val uri = data?.data ?: return

        when (requestCode) {
            CREATE_CSV -> writeCsv(uri)
            OPEN_CSV -> readCsv(uri)
        }
    }

    private fun writeCsv(uri: Uri) {
        try {
            val contacts = db.getAll("")

            val output = contentResolver.openOutputStream(uri)
                ?: throw Exception("Could not open the selected file")

            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                writer.appendLine(
                    "phone_number,name,beneficiary,service,building,floor,office,mailbox,hipath_port,port_h"
                )

                contacts.forEach { contact ->
                    val values = listOf(
                        contact.phoneNumber,
                        contact.name,
                        contact.beneficiary,
                        contact.service,
                        contact.building,
                        contact.floor,
                        contact.office,
                        contact.mailbox,
                        contact.hipathPort,
                        contact.portH
                    )

                    writer.appendLine(
                        values.joinToString(",") { csvEscape(it) }
                    )
                }
            }

            toast("Export completed: ${contacts.size} contacts")
        } catch (e: Exception) {
            toast("Export failed: ${e.message ?: "Unknown error"}")
        }
    }

    private fun readCsv(uri: Uri) {
        var added = 0
        var skipped = 0

        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw Exception("Could not open the selected file")

            BufferedReader(
                InputStreamReader(input, Charsets.UTF_8)
            ).use { reader ->
                var lineNumber = 0
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue

                    if (lineNumber == 0) {
                        lineNumber++
                        continue
                    }

                    lineNumber++

                    if (currentLine.isBlank()) continue

                    val row = parseCsvLine(currentLine)

                    if (row.size < 10 || row[0].isBlank()) {
                        skipped++
                        continue
                    }

                    val contact = Contact(
                        phoneNumber = row[0].trim(),
                        name = row[1],
                        beneficiary = row[2],
                        service = row[3],
                        building = row[4],
                        floor = row[5],
                        office = row[6],
                        mailbox = row[7],
                        hipathPort = row[8],
                        portH = row[9]
                    )

                    val result = db.save(contact)

                    if (result.isSuccess) {
                        added++
                    } else {
                        skipped++
                    }
                }
            }

            toast("Import finished. Added: $added, skipped: $skipped")
        } catch (e: Exception) {
            toast("Import failed: ${e.message ?: "Check the CSV format"}")
        }
    }

    private fun csvEscape(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]

            when {
                ch == '"' && quoted &&
                    i + 1 < line.length &&
                    line[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }

                ch == '"' -> quoted = !quoted

                ch == ',' && !quoted -> {
                    result.add(field.toString())
                    field.setLength(0)
                }

                else -> field.append(ch)
            }

            i++
        }

        result.add(field.toString())
        return result
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
