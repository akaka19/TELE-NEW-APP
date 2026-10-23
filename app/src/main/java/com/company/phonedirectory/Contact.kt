package com.company.phonedirectory
data class Contact(
    val id: Long = 0, val phoneNumber: String, val name: String = "", val beneficiary: String = "",
    val service: String = "", val building: String = "", val floor: String = "", val office: String = "",
    val mailbox: String = "", val hipathPort: String = "", val portH: String = ""
)
