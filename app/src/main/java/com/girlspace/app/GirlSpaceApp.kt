package com.girlspace.app

import android.app.Application
import com.girlspace.app.moderation.BadWordsProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GirlSpaceApp : Application() {

    override fun onCreate() {
        super.onCreate()

    }
}