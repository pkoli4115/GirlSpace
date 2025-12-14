package com.girlspace.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GirlSpaceApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ AdMob init (safe for test + prod)
        MobileAds.initialize(this) {}
    }
}
