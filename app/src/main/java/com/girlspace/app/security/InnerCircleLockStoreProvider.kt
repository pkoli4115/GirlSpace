package com.girlspace.app.security

import android.content.Context

/**
 * Simple provider so ViewModels/UI can access a single InnerCircleLockStore instance.
 */
object InnerCircleLockStoreProvider {

    @Volatile private var instance: InnerCircleLockStore? = null

    fun get(context: Context): InnerCircleLockStore {
        return instance ?: synchronized(this) {
            instance ?: InnerCircleLockStore(context.applicationContext).also { instance = it }
        }
    }
}
