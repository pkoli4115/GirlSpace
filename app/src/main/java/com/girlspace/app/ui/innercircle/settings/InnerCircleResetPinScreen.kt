package com.girlspace.app.ui.innercircle.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.girlspace.app.MainActivity.Companion.fbCallbackManager
import com.girlspace.app.security.InnerCircleLockStore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleResetPinScreen(
    onVerified: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val store = remember { InnerCircleLockStore(context.applicationContext) }

    // Providers linked on this user (google.com / facebook.com / phone)
    val providers = remember(user) {
        user?.providerData
            ?.mapNotNull { it.providerId }
            ?.filter { it.isNotBlank() && it != "firebase" }
            ?.distinct()
            .orEmpty()
    }

    // Helpful warning when user used multiple accounts/providers before
    val providerHint = remember(providers) {
        if (providers.size > 1) {
            "Use the same login account you used when you set your Inner Circle PIN."
        } else null
    }

    // ─────────────────────────────────────────
    // GOOGLE re-auth
    // ─────────────────────────────────────────
    val webClientId = remember {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else ""
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.result
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                Toast.makeText(context, "Google verification failed (no token)", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.currentUser?.reauthenticate(credential)
                ?.addOnSuccessListener {
                    scope.launch {
                        store.clearPin()
                        Toast.makeText(context, "Verified ✅ Now set a new PIN", Toast.LENGTH_SHORT).show()
                        onVerified()
                    }
                }
                ?.addOnFailureListener { e ->
                    Toast.makeText(context, "Google verify failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Google verify error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun startGoogleReauth() {
        val act = activity ?: run {
            Toast.makeText(context, "No activity context", Toast.LENGTH_SHORT).show()
            return
        }
        if (webClientId.isBlank()) {
            Toast.makeText(context, "Missing default_web_client_id", Toast.LENGTH_LONG).show()
            return
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(act, gso)

        // Force fresh sign-in token
        client.signOut().addOnCompleteListener {
            googleLauncher.launch(client.signInIntent)
        }
    }

    // ─────────────────────────────────────────
    // FACEBOOK re-auth
    // ─────────────────────────────────────────
    fun startFacebookReauth() {
        val act = activity ?: run {
            Toast.makeText(context, "No activity context", Toast.LENGTH_SHORT).show()
            return
        }

        LoginManager.getInstance().registerCallback(
            fbCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken?.token
                    if (token.isNullOrBlank()) {
                        Toast.makeText(context, "Facebook verification failed", Toast.LENGTH_LONG).show()
                        return
                    }
                    val credential = FacebookAuthProvider.getCredential(token)
                    auth.currentUser?.reauthenticate(credential)
                        ?.addOnSuccessListener {
                            scope.launch {
                                store.clearPin()
                                Toast.makeText(context, "Verified ✅ Now set a new PIN", Toast.LENGTH_SHORT).show()
                                onVerified()
                            }
                        }
                        ?.addOnFailureListener { e ->
                            Toast.makeText(context, "Facebook verify failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }

                override fun onCancel() {
                    Toast.makeText(context, "Facebook verification cancelled", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: FacebookException) {
                    Toast.makeText(context, "Facebook error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        )

        LoginManager.getInstance().logInWithReadPermissions(act, listOf("email", "public_profile"))
    }

    // ─────────────────────────────────────────
    // PHONE OTP re-auth (REAL OTP)
    // ─────────────────────────────────────────
    var phoneStep by remember { mutableStateOf(0) } // 0=choose, 1=enterOtp
    var verificationId by remember { mutableStateOf<String?>(null) }
    var otp by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun startPhoneReauth() {
        val act = activity ?: run {
            Toast.makeText(context, "No activity context", Toast.LENGTH_SHORT).show()
            return
        }

        val phoneNumber = auth.currentUser?.phoneNumber
        if (phoneNumber.isNullOrBlank()) {
            Toast.makeText(
                context,
                "This account does not have a phone number linked. Use Google/Facebook verification instead.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        busy = true
        otp = ""
        verificationId = null

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification (sometimes happens). Re-auth immediately.
                auth.currentUser?.reauthenticate(credential)
                    ?.addOnSuccessListener {
                        scope.launch {
                            store.clearPin()
                            Toast.makeText(context, "Verified ✅ Now set a new PIN", Toast.LENGTH_SHORT).show()
                            onVerified()
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Toast.makeText(context, "Phone verify failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                    ?.addOnCompleteListener { busy = false }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                busy = false
                Toast.makeText(context, "OTP failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                busy = false
                verificationId = vid
                phoneStep = 1
                Toast.makeText(context, "OTP sent to $phoneNumber", Toast.LENGTH_SHORT).show()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(act)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun confirmOtp() {
        val actVid = verificationId
        if (actVid.isNullOrBlank()) {
            Toast.makeText(context, "Please request OTP again.", Toast.LENGTH_SHORT).show()
            return
        }
        if (otp.length < 4) {
            Toast.makeText(context, "Enter the OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        busy = true
        val credential = PhoneAuthProvider.getCredential(actVid, otp)
        auth.currentUser?.reauthenticate(credential)
            ?.addOnSuccessListener {
                scope.launch {
                    store.clearPin()
                    Toast.makeText(context, "Reset successful ✅ Set a new PIN", Toast.LENGTH_SHORT).show()
                    onVerified()
                }
            }
            ?.addOnFailureListener { e ->
                Toast.makeText(context, "OTP verify failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            ?.addOnCompleteListener { busy = false }
    }

    // ─────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reset Inner Circle PIN") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "To reset your Inner Circle PIN, verify your account.",
                style = MaterialTheme.typography.bodyMedium
            )

            providerHint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (user == null) {
                Text("Not logged in.", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            if (providers.isEmpty()) {
                Text("No linked providers found.", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            // Phone OTP step UI
            if (phoneStep == 1) {
                Text(
                    text = "Enter the OTP sent to your phone number.",
                    style = MaterialTheme.typography.titleSmall
                )

                OutlinedTextField(
                    value = otp,
                    onValueChange = { v ->
                        otp = v.filter { it.isDigit() }.take(6)
                    },
                    label = { Text("OTP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )

                Button(
                    onClick = { confirmOtp() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Verifying..." else "Verify OTP") }

                Button(
                    onClick = { startPhoneReauth() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Sending..." else "Resend OTP") }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "After verification, you’ll set a new 4-digit PIN.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // Provider buttons
            if (providers.contains(GoogleAuthProvider.PROVIDER_ID)) {
                Button(onClick = { startGoogleReauth() }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("Verify with Google")
                }
            }

            if (providers.contains(FacebookAuthProvider.PROVIDER_ID)) {
                Button(onClick = { startFacebookReauth() }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("Verify with Facebook")
                }
            }

            if (providers.contains(PhoneAuthProvider.PROVIDER_ID)) {
                Button(onClick = { startPhoneReauth() }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (busy) "Sending OTP..." else "Verify via SMS (OTP)")
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "After verification, your old PIN will be cleared and you’ll set a new one.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
