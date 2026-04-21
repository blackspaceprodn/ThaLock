package com.thalock.app.data.model

/**
 * Top-level grouping for documents shown in the vault and scan flows.
 */
enum class DocumentCategory(
    val displayName: String,
    val subtitle: String,
    val iconName: String
) {
    IDENTITY("Identity", "IDs, passports, licences", "badge"),
    FINANCIAL("Financial", "Bank accounts, cards", "account_balance"),
    INSURANCE("Insurance", "Auto, home, health", "shield");

    companion object {
        fun forDocumentType(type: DocumentType): DocumentCategory = when (type) {
            DocumentType.IDENTITY -> IDENTITY

            DocumentType.BANK_ACCOUNT,
            DocumentType.DEBIT_CARD,
            DocumentType.CREDIT_CARD -> FINANCIAL

            DocumentType.AUTO_INSURANCE,
            DocumentType.HOME_INSURANCE,
            DocumentType.HEALTH_INSURANCE,
            DocumentType.OTHER_INSURANCE -> INSURANCE
        }

        fun typesIn(category: DocumentCategory): List<DocumentType> = when (category) {
            IDENTITY -> listOf(DocumentType.IDENTITY)
            FINANCIAL -> listOf(
                DocumentType.BANK_ACCOUNT,
                DocumentType.DEBIT_CARD,
                DocumentType.CREDIT_CARD
            )
            INSURANCE -> listOf(
                DocumentType.AUTO_INSURANCE,
                DocumentType.HOME_INSURANCE,
                DocumentType.HEALTH_INSURANCE,
                DocumentType.OTHER_INSURANCE
            )
        }
    }
}
