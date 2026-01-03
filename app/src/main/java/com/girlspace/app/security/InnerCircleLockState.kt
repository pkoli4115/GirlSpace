package com.girlspace.app.security

data class InnerCircleLockState(
    val enabled: Boolean = false,
    val hasPin: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null
)
