
package com.company.phonedirectory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "hipath_directory.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE contacts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone_number TEXT NOT NULL COLLATE NOCASE UNIQUE,
                name TEXT NOT NULL DEFAULT '',
                beneficiary TEXT NOT NULL DEFAULT '',
                service TEXT NOT NULL DEFAULT '',
                building TEXT NOT NULL DEFAULT '',
                floor TEXT NOT NULL DEFAULT '',
                office TEXT NOT NULL DEFAULT '',
                mailbox TEXT NOT NULL DEFAULT '',
                hipath_port TEXT NOT NULL DEFAULT '',
                port_h TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL(
            "CREATE INDEX idx_contacts_phone ON contacts(phone_number)"
        )
        createCompanyTables(db)
    }

    private fun createCompanyTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS companies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS company_people (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                company_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                phone_number TEXT NOT NULL DEFAULT '',
                service TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT ''
            )"""
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_company_people_company " +
                "ON company_people(company_id)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createCompanyTables(db)
        }
    }

    // Existing Directory functions

    fun getAll(query: String = ""): List<Contact> {
        val out = mutableListOf<Contact>()

        val sql = if (query.isBlank()) {
            "SELECT * FROM contacts ORDER BY phone_number COLLATE NOCASE"
        } else {
            """SELECT * FROM contacts
                WHERE phone_number LIKE ?
                OR name LIKE ?
                OR beneficiary LIKE ?
                OR service LIKE ?
                OR building LIKE ?
                OR floor LIKE ?
                OR office LIKE ?
                OR mailbox LIKE ?
                OR hipath_port LIKE ?
                OR port_h LIKE ?
                ORDER BY phone_number COLLATE NOCASE"""
        }

        val args = if (query.isBlank()) {
            null
        } else {
            Array(10) { "%$query%" }
        }

        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Contact(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        phoneNumber = c.getString(
                            c.getColumnIndexOrThrow("phone_number")
                        ) ?: "",
                        name = c.getString(
                            c.getColumnIndexOrThrow("name")
                        ) ?: "",
                        beneficiary = c.getString(
                            c.getColumnIndexOrThrow("beneficiary")
                        ) ?: "",
                        service = c.getString(
                            c.getColumnIndexOrThrow("service")
                        ) ?: "",
                        building = c.getString(
                            c.getColumnIndexOrThrow("building")
                        ) ?: "",
                        floor = c.getString(
                            c.getColumnIndexOrThrow("floor")
                        ) ?: "",
                        office = c.getString(
                            c.getColumnIndexOrThrow("office")
                        ) ?: "",
                        mailbox = c.getString(
                            c.getColumnIndexOrThrow("mailbox")
                        ) ?: "",
                        hipathPort = c.getString(
                            c.getColumnIndexOrThrow("hipath_port")
                        ) ?: "",
                        portH = c.getString(
                            c.getColumnIndexOrThrow("port_h")
                        ) ?: ""
                    )
                )
            }
        }
        return out
    }

    fun getById(id: Long): Contact? =
        getAll().firstOrNull { it.id == id }

    fun save(c: Contact): Result<Long> = runCatching {
        val values = ContentValues().apply {
            put("phone_number", c.phoneNumber.trim())
            put("name", c.name.trim())
            put("beneficiary", c.beneficiary.trim())
            put("service", c.service.trim())
            put("building", c.building.trim())
            put("floor", c.floor.trim())
            put("office", c.office.trim())
            put("mailbox", c.mailbox.trim())
            put("hipath_port", c.hipathPort.trim())
            put("port_h", c.portH.trim())
        }

        if (c.id == 0L) {
            writableDatabase.insertOrThrow("contacts", null, values)
        } else {
            if (
                writableDatabase.update(
                    "contacts", values, "id=?",
                    arrayOf(c.id.toString())
                ) == 0
            ) {
                error("Contact no longer exists")
            }
            c.id
        }
    }.recoverCatching { e ->
        if (e is SQLiteConstraintException) {
            throw DuplicatePhoneException()
        } else {
            throw e
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete(
            "contacts", "id=?", arrayOf(id.toString())
        )
    }

    // Companies

    fun getCompanies(query: String = ""): List<Company> {
        val result = mutableListOf<Company>()
        val sql = if (query.isBlank()) {
            "SELECT id, name FROM companies ORDER BY name COLLATE NOCASE"
        } else {
            "SELECT id, name FROM companies WHERE name LIKE ? " +
                "ORDER BY name COLLATE NOCASE"
        }
        val args = if (query.isBlank()) null else arrayOf("%$query%")

        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                result.add(
                    Company(
                        c.getLong(0),
                        c.getString(1) ?: ""
                    )
                )
            }
        }
        return result
    }

    fun getCompanyById(id: Long): Company? {
        readableDatabase.rawQuery(
            "SELECT id, name FROM companies WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                return Company(c.getLong(0), c.getString(1) ?: "")
            }
        }
        return null
    }

    fun saveCompany(id: Long = 0L, name: String): Result<Long> =
        runCatching {
            require(name.isNotBlank()) { "Company name is required" }

            val values = ContentValues().apply {
                put("name", name.trim())
            }

            if (id == 0L) {
                writableDatabase.insertOrThrow(
                    "companies", null, values
                )
            } else {
                val changed = writableDatabase.update(
                    "companies", values, "id=?",
                    arrayOf(id.toString())
                )
                if (changed == 0) error("Company no longer exists")
                id
            }
        }.recoverCatching { e ->
            if (e is SQLiteConstraintException) {
                throw Exception("This company already exists.")
            } else {
                throw e
            }
        }

    fun deleteCompany(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "company_people", "company_id=?",
                arrayOf(id.toString())
            )
            writableDatabase.delete(
                "companies", "id=?", arrayOf(id.toString())
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    // People belonging to a company

    fun getPeople(
        companyId: Long,
        query: String = ""
    ): List<CompanyPerson> {
        val result = mutableListOf<CompanyPerson>()

        val sql = if (query.isBlank()) {
            """SELECT * FROM company_people
                WHERE company_id=?
                ORDER BY name COLLATE NOCASE"""
        } else {
            """SELECT * FROM company_people
                WHERE company_id=?
                AND (name LIKE ? OR phone_number LIKE ?
                OR service LIKE ? OR notes LIKE ?)
                ORDER BY name COLLATE NOCASE"""
        }

        val args = if (query.isBlank()) {
            arrayOf(companyId.toString())
        } else {
            arrayOf(
                companyId.toString(),
                "%$query%", "%$query%", "%$query%", "%$query%"
            )
        }

        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                result.add(
                    CompanyPerson(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        companyId = c.getLong(
                            c.getColumnIndexOrThrow("company_id")
                        ),
                        name = c.getString(
                            c.getColumnIndexOrThrow("name")
                        ) ?: "",
                        phoneNumber = c.getString(
                            c.getColumnIndexOrThrow("phone_number")
                        ) ?: "",
                        service = c.getString(
                            c.getColumnIndexOrThrow("service")
                        ) ?: "",
                        notes = c.getString(
                            c.getColumnIndexOrThrow("notes")
                        ) ?: ""
                    )
                )
            }
        }
        return result
    }

    fun getPersonById(id: Long): CompanyPerson? {
        readableDatabase.rawQuery(
            "SELECT * FROM company_people WHERE id=?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                return CompanyPerson(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    companyId = c.getLong(
                        c.getColumnIndexOrThrow("company_id")
                    ),
                    name = c.getString(
                        c.getColumnIndexOrThrow("name")
                    ) ?: "",
                    phoneNumber = c.getString(
                        c.getColumnIndexOrThrow("phone_number")
                    ) ?: "",
                    service = c.getString(
                        c.getColumnIndexOrThrow("service")
                    ) ?: "",
                    notes = c.getString(
                        c.getColumnIndexOrThrow("notes")
                    ) ?: ""
                )
            }
        }
        return null
    }

    fun savePerson(person: CompanyPerson): Result<Long> =
        runCatching {
            require(person.name.isNotBlank()) {
                "Person name is required"
            }

            val values = ContentValues().apply {
                put("company_id", person.companyId)
                put("name", person.name.trim())
                put("phone_number", person.phoneNumber.trim())
                put("service", person.service.trim())
                put("notes", person.notes.trim())
            }

            if (person.id == 0L) {
                writableDatabase.insertOrThrow(
                    "company_people", null, values
                )
            } else {
                val changed = writableDatabase.update(
                    "company_people", values, "id=?",
                    arrayOf(person.id.toString())
                )
                if (changed == 0) error("Person no longer exists")
                person.id
            }
        }

    fun deletePerson(id: Long) {
        writableDatabase.delete(
            "company_people", "id=?", arrayOf(id.toString())
        )
    }
}

class DuplicatePhoneException :
    Exception("That phone number or extension already exists.")
