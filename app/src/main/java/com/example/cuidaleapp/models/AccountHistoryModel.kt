package com.example.cuidaleapp.models

import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.IgnoreExtraProperties

@Immutable
@IgnoreExtraProperties
data class AccountHistoryEntry(
    val id: String = "",
    val action: String = "",
    val title: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
@IgnoreExtraProperties
data class LinkedAccountDetail(
    val providerId: String = "",
    val providerName: String = "",
    val accountName: String = "",
    val linkedAt: Long = System.currentTimeMillis(),
    val verificado: Boolean = false
)

@Immutable
@IgnoreExtraProperties
data class RateLimitEntry(
    val key: String = "",
    val attempts: Int = 0,
    val expiresAt: Long = 0L
)
