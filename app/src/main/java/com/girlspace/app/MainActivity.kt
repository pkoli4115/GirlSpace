package com.girlspace.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.CallbackManager
import com.girlspace.app.ui.GirlSpaceApp
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.theme.VibeTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

// 🔥 Play Store In-App Update imports
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.InstallStatus

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        // Facebook login callback
        val fbCallbackManager: CallbackManager by lazy {
            CallbackManager.Factory.create()
        }
    }

    // 🔥 App Update Manager
    private lateinit var appUpdateManager: AppUpdateManager
    private val FLEXIBLE_UPDATE_REQUEST = 1234
    private val IMMEDIATE_UPDATE_REQUEST = 5678

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Status bar icon mode
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        // 🔥 Initialize update manager
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()

        setContent {
            // Theme mode from DataStore
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            val themeMode by onboardingViewModel.themeMode.collectAsState()

            VibeTheme(themeMode = themeMode) {
                GirlSpaceApp()
            }
        }
    }

    // 🔥 Check for updates immediately when activity starts
    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { info ->

            when {
                // FLEXIBLE UPDATE available?
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {

                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.FLEXIBLE,
                        this,
                        FLEXIBLE_UPDATE_REQUEST
                    )
                }

                // IMMEDIATE UPDATE available?
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {

                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.IMMEDIATE,
                        this,
                        IMMEDIATE_UPDATE_REQUEST
                    )
                }
            }
        }
    }

    // 🔥 If the update was downloaded in background, prompt user to restart
    override fun onResume() {
        super.onResume()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                // Shows Google's official "Restart to update" UI
                appUpdateManager.completeUpdate()
            }
        }
    }

    // Facebook callback
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fbCallbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
