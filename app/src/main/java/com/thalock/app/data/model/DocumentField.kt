package com.thalock.app.data.model

data class DocumentField(
    val key: String,
    val label: String,
    val value: String,
    val isSensitive: Boolean = false
)
