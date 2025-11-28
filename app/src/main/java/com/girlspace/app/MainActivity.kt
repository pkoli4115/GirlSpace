package com.girlspace.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.facebook.CallbackManager
import com.girlspace.app.ui.GirlSpaceApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        // Used by LoginScreen for Facebook login
        val fbCallbackManager: CallbackManager by lazy {
            CallbackManager.Factory.create()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ❌ NO MORE FacebookSdk.sdkInitialize(...)
        // The SDK reads ApplicationId from manifest automatically.

        setContent {
            GirlSpaceApp()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fbCallbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
