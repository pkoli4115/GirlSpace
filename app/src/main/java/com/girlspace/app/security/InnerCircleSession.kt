package com.girlspace.app.security

/**
 * Inner Circle in-memory session lock.
 * Reset on app restart / logout.
 */
object InnerCircleSession {

    private var unlocked: Boolean = false

    fun isUnlocked(): Boolean = unlocked

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }
}
