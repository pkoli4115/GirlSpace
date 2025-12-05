package com.girlspace.app.utils

import android.content.Context
import android.os.Build

object AppInfo {

    fun versionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun versionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode else pInfo.versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    fun deviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun androidVersion(): String {
        return "Android ${Build.VERSION.RELEASE}"
    }
}
