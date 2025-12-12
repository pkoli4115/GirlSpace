package com.girlspace.app.data.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Runs at app start:
 * - Ensures default notification prefs exist for the logged-in user
 * - Fetches latest FCM token and saves it to Firestore (or caches if not logged-in yet)
 *
 * This makes token persistence automatic for all users.
 */
object NotificationBootstrapper {

    private const val TAG = "NotifBootstrapper"

    fun bootstrap(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = NotificationRepository()
                val uid = FirebaseAuth.getInstance().currentUser?.uid

                // 1) Always try to get the current FCM token (even if user not logged in yet)
                val token = try {
                    FirebaseMessaging.getInstance().token.await()
                } catch (e: Exception) {
                    Log.w(TAG, "FCM token fetch failed (will retry next launch): ${e.message}")
                    null
                }

                // 2) If not logged-in, cache token locally (so it can be flushed after login)
                if (uid.isNullOrBlank()) {
                    if (!token.isNullOrBlank()) {
                        FcmTokenCache.save(context, token)
                        Log.d(TAG, "Cached FCM token (not logged-in yet)")
                    }
                    return@launch
                }

                // 3) Logged-in: ensure prefs and flush any cached token + current token
                repo.ensureDefaultPrefs(uid)

                val cached = FcmTokenCache.get(context)
                if (!cached.isNullOrBlank()) {
                    repo.saveToken(uid, cached)
                    FcmTokenCache.clear(context)
                    Log.d(TAG, "Flushed cached FCM token to Firestore")
                }

                if (!token.isNullOrBlank()) {
                    repo.saveToken(uid, token)
                    Log.d(TAG, "Saved current FCM token to Firestore")
                } else {
                    Log.w(TAG, "FCM token was null/blank (nothing to save)")
                }
            } catch (e: Exception) {
                // Never crash app startup because of notifications
                Log.w(TAG, "bootstrap failed: ${e.message}")
            }
        }
    }
}
