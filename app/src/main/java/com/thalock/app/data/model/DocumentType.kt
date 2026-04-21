package com.thalock.app.data.model

enum class DocumentType(val displayName: String, val icon: String) {
    IDENTITY("Identity", "badge"),

    BANK_ACCOUNT("Bank Account", "account_balance"),
    DEBIT_CARD("Debit Card", "credit_card"),
    CREDIT_CARD("Credit Card", "credit_card"),

    AUTO_INSURANCE("Auto Insurance", "directions_car"),
    HOME_INSURANCE("Home Insurance", "home"),
    HEALTH_INSURANCE("Health Insurance", "health_and_safety"),
    OTHER_INSURANCE("Other Insurance", "shield"),
}
