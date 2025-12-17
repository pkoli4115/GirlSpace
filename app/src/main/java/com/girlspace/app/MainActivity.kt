package com.girlspace.app

import android.Manifest
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.facebook.CallbackManager
import com.girlspace.app.data.notifications.NotificationBootstrapper
import com.girlspace.app.ui.GirlSpaceApp
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.ui.theme.VibeTheme
import com.girlspace.app.utils.DeepLinkStore
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        val fbCallbackManager: CallbackManager by lazy {
            CallbackManager.Factory.create()
        }
    }

    private lateinit var appUpdateManager: AppUpdateManager
    private val FLEXIBLE_UPDATE_REQUEST = 1234
    private val IMMEDIATE_UPDATE_REQUEST = 5678

    // ✅ Android 13+ notification permission request
    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // No UI changes; just log if needed
        // If not granted, system notifications won't show when app is killed.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Handle notification tap (cold start)
// ✅ Handle notification tap (cold start)
        handleNotificationIntent(intent)

// ✅ Handle share-in (cold start)
        handleIncomingShareIntent(intent)

        // ✅ Step 1 bootstrap
        NotificationBootstrapper.bootstrap(applicationContext)

        // ✅ Ask notification permission (Android 13+)
        ensureNotificationPermission()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = true

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()

        setContent {
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            val themeMode by onboardingViewModel.themeMode.collectAsState()

            VibeTheme(themeMode = themeMode) {
                GirlSpaceApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val threadId = intent?.getStringExtra("open_chat_thread_id")?.trim()
        if (!threadId.isNullOrBlank()) {
            DeepLinkStore.openChat(threadId)
        }
    }
    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action ?: return
        val type = intent.type

        when (action) {
            Intent.ACTION_SEND -> {
                // TEXT / URL share
                if (type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                    if (!sharedText.isNullOrBlank()) {
                        DeepLinkStore.setSharedText(sharedText)
                    }
                }

                // VIDEO share (single)
                if (type?.startsWith("video/") == true) {
                    val uri = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }

                    if (uri != null) {
                        DeepLinkStore.setSharedVideoUri(uri.toString())
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                // MULTIPLE videos (take first for now)
                if (type?.startsWith("video/") == true) {
                    val uris = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    }

                    val first = uris?.firstOrNull()
                    if (first != null) {
                        DeepLinkStore.setSharedVideoUri(first.toString())
                    }
                }
            }
        }

        // ✅ Prevent re-processing if activity recreates
        intent.action = null
        intent.type = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_STREAM)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { info ->
            when {
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.FLEXIBLE,
                        this,
                        FLEXIBLE_UPDATE_REQUEST
                    )
                }

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

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fbCallbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
