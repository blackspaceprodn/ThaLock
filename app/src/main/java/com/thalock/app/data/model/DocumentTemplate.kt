package com.thalock.app.data.model

object DocumentTemplate {

    fun fieldsFor(type: DocumentType): List<DocumentField> = when (type) {
        DocumentType.IDENTITY -> listOf(
            DocumentField("name", "Full Name", ""),
            DocumentField("id_number", "ID / Document Number", "", isSensitive = true),
            DocumentField("dob", "Date of Birth", ""),
            DocumentField("issue_date", "Issue Date", ""),
            DocumentField("expiry_date", "Expiry Date", ""),
            DocumentField("address", "Address", ""),
        )

        DocumentType.BANK_ACCOUNT -> listOf(
            DocumentField("bank_name", "Bank Name", ""),
            DocumentField("account_holder", "Account Holder", ""),
            DocumentField("account_number", "Account Number", "", isSensitive = true),
            DocumentField("ifsc", "IFSC / SWIFT / Routing", ""),
            DocumentField("branch", "Branch", ""),
            DocumentField("account_type", "Account Type", ""),
        )

        DocumentType.DEBIT_CARD -> listOf(
            DocumentField("bank_name", "Bank / Issuer", ""),
            DocumentField("card_holder", "Card Holder", ""),
            DocumentField("card_number", "Card Number", "", isSensitive = true),
            DocumentField("expiry", "Expiry (MM/YY)", ""),
            DocumentField("cvv", "CVV", "", isSensitive = true),
            DocumentField("pin", "ATM PIN", "", isSensitive = true),
        )

        DocumentType.CREDIT_CARD -> listOf(
            DocumentField("bank_name", "Bank / Issuer", ""),
            DocumentField("card_holder", "Card Holder", ""),
            DocumentField("card_number", "Card Number", "", isSensitive = true),
            DocumentField("expiry", "Expiry (MM/YY)", ""),
            DocumentField("cvv", "CVV", "", isSensitive = true),
            DocumentField("credit_limit", "Credit Limit", ""),
        )

        DocumentType.AUTO_INSURANCE -> listOf(
            DocumentField("provider", "Insurance Provider", ""),
            DocumentField("policy_number", "Policy Number", "", isSensitive = true),
            DocumentField("policy_holder", "Policy Holder", ""),
            DocumentField("vehicle", "Vehicle (Make / Model)", ""),
            DocumentField("registration", "Registration Number", ""),
            DocumentField("start_date", "Start Date", ""),
            DocumentField("expiry_date", "Expiry Date", ""),
        )

        DocumentType.HOME_INSURANCE -> listOf(
            DocumentField("provider", "Insurance Provider", ""),
            DocumentField("policy_number", "Policy Number", "", isSensitive = true),
            DocumentField("policy_holder", "Policy Holder", ""),
            DocumentField("property_address", "Property Address", ""),
            DocumentField("sum_insured", "Sum Insured", ""),
            DocumentField("start_date", "Start Date", ""),
            DocumentField("expiry_date", "Expiry Date", ""),
        )

        DocumentType.HEALTH_INSURANCE -> listOf(
            DocumentField("provider", "Insurance Provider", ""),
            DocumentField("policy_number", "Policy Number", "", isSensitive = true),
            DocumentField("policy_holder", "Policy Holder", ""),
            DocumentField("members_covered", "Members Covered", ""),
            DocumentField("sum_insured", "Sum Insured", ""),
            DocumentField("start_date", "Start Date", ""),
            DocumentField("expiry_date", "Expiry Date", ""),
        )

        DocumentType.OTHER_INSURANCE -> listOf(
            DocumentField("provider", "Insurance Provider", ""),
            DocumentField("policy_number", "Policy Number", "", isSensitive = true),
            DocumentField("policy_holder", "Policy Holder", ""),
            DocumentField("policy_type", "Policy Type", ""),
            DocumentField("sum_insured", "Sum Insured", ""),
            DocumentField("start_date", "Start Date", ""),
            DocumentField("expiry_date", "Expiry Date", ""),
        )
    }
}
