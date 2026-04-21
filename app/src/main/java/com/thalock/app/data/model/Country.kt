package com.thalock.app.data.model

enum class Country(val displayName: String, val shortLabel: String, val code: String) {
    INDIA("India", "IN", "IN"),
    UAE("UAE", "AE", "AE"),
    SINGAPORE("Singapore", "SG", "SG"),
    UK("United Kingdom", "GB", "GB"),
    USA("United States", "US", "US"),
    CANADA("Canada", "CA", "CA"),
    AUSTRALIA("Australia", "AU", "AU"),
    GERMANY("Germany", "DE", "DE"),
    FRANCE("France", "FR", "FR"),
    JAPAN("Japan", "JP", "JP"),
    CHINA("China", "CN", "CN"),
    BRAZIL("Brazil", "BR", "BR"),
    SOUTH_AFRICA("South Africa", "ZA", "ZA"),
    OTHER("Other", "--", "XX");

    companion object {
        fun fromCode(code: String): Country =
            entries.find { it.code == code } ?: OTHER
    }
}
