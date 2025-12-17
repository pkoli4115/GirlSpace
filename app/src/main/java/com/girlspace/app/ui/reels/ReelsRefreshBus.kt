package com.girlspace.app.ui.reels

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ReelsRefreshBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun notifyRefresh() {
        _events.tryEmit(Unit)
    }
}
