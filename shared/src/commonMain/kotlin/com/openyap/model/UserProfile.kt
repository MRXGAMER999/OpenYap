package com.openyap.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val customAliases: Map<String, String> = emptyMap(),
) {
    val aliasMap: Map<String, String>
        get() = buildMap {
            if (name.isNotBlank()) put("my name", name)
            if (email.isNotBlank()) put("my email", email)
            if (phone.isNotBlank()) {
                put("my phone", phone)
                put("my phone number", phone)
                put("my number", phone)
            }
            if (jobTitle.isNotBlank()) put("my job title", jobTitle)
            if (company.isNotBlank()) {
                put("my company", company)
                put("my organization", company)
            }
            putAll(customAliases)
        }
}
