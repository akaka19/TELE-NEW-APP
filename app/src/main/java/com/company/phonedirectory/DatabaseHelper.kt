package com.company.phonedirectory
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "hipath_directory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT, phone_number TEXT NOT NULL COLLATE NOCASE UNIQUE, name TEXT NOT NULL DEFAULT '', beneficiary TEXT NOT NULL DEFAULT '', service TEXT NOT NULL DEFAULT '', building TEXT NOT NULL DEFAULT '', floor TEXT NOT NULL DEFAULT '', office TEXT NOT NULL DEFAULT '', mailbox TEXT NOT NULL DEFAULT '', hipath_port TEXT NOT NULL DEFAULT '', port_h TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX idx_contacts_phone ON contacts(phone_number)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun getAll(query: String = ""): List<Contact> {
        val out = mutableListOf<Contact>()
        val sql = if (query.isBlank()) "SELECT * FROM contacts ORDER BY phone_number COLLATE NOCASE" else
            "SELECT * FROM contacts WHERE phone_number LIKE ? OR name LIKE ? OR beneficiary LIKE ? OR service LIKE ? OR building LIKE ? OR floor LIKE ? OR office LIKE ? OR mailbox LIKE ? OR hipath_port LIKE ? OR port_h LIKE ? ORDER BY phone_number COLLATE NOCASE"
        val args = if (query.isBlank()) null else Array(10) { "%$query%" }
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(Contact(
                c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("phone_number")) ?: "",
                c.getString(c.getColumnIndexOrThrow("name")) ?: "", c.getString(c.getColumnIndexOrThrow("beneficiary")) ?: "",
                c.getString(c.getColumnIndexOrThrow("service")) ?: "", c.getString(c.getColumnIndexOrThrow("building")) ?: "",
                c.getString(c.getColumnIndexOrThrow("floor")) ?: "", c.getString(c.getColumnIndexOrThrow("office")) ?: "",
                c.getString(c.getColumnIndexOrThrow("mailbox")) ?: "", c.getString(c.getColumnIndexOrThrow("hipath_port")) ?: "",
                c.getString(c.getColumnIndexOrThrow("port_h")) ?: ""
            ))
        }
        return out
    }
    fun getById(id: Long): Contact? = getAll().firstOrNull { it.id == id }
    fun save(c: Contact): Result<Long> = runCatching {
        val v = ContentValues().apply {
            put("phone_number", c.phoneNumber.trim()); put("name", c.name.trim()); put("beneficiary", c.beneficiary.trim())
            put("service", c.service.trim()); put("building", c.building.trim()); put("floor", c.floor.trim())
            put("office", c.office.trim()); put("mailbox", c.mailbox.trim()); put("hipath_port", c.hipathPort.trim()); put("port_h", c.portH.trim())
        }
        if (c.id == 0L) writableDatabase.insertOrThrow("contacts", null, v) else {
            if (writableDatabase.update("contacts", v, "id=?", arrayOf(c.id.toString())) == 0) error("Contact no longer exists")
            c.id
        }
    }.recoverCatching { e -> if (e is SQLiteConstraintException) throw DuplicatePhoneException() else throw e }
    fun delete(id: Long) = writableDatabase.delete("contacts", "id=?", arrayOf(id.toString()))
}
class DuplicatePhoneException : Exception("That phone number or extension already exists.")
