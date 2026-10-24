
package com.company.phonedirectory

data class Company(
    val id: Long,
    val name: String
)

data class CompanyPerson(
    val id: Long = 0L,
    val companyId: Long,
    val name: String,
    val phoneNumber: String = "",
    val service: String = "",
    val notes: String = ""
)
