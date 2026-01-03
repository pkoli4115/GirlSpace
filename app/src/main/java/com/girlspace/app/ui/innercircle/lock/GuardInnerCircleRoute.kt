package com.girlspace.app.ui.innercircle.lock

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.girlspace.app.security.InnerCircleLockState
import com.girlspace.app.security.InnerCircleLockStore
import com.girlspace.app.security.InnerCircleSession

@Composable
fun GuardInnerCircleRoute(
    navController: NavHostController,
    targetRoute: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val store = remember { InnerCircleLockStore(context) }

    val lockState by store.state.collectAsState(
        initial = InnerCircleLockState(enabled = false, hasPin = false)
    )

    val unlocked = InnerCircleSession.isUnlocked()

    LaunchedEffect(lockState.enabled, lockState.hasPin, unlocked, targetRoute) {
        if (lockState.enabled && !unlocked) {
            val encoded = Uri.encode(targetRoute)
            navController.navigate("inner_lock/$encoded") {
                launchSingleTop = true
            }
        }
    }


    if (!lockState.enabled || !lockState.hasPin || unlocked) {
        content()
    }
}
