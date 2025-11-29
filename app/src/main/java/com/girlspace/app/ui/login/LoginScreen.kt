package com.girlspace.app.ui.login

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.girlspace.app.MainActivity
import com.girlspace.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

@Composable
fun LoginScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val activity = context as Activity

    val firebaseAuth = remember { FirebaseAuth.getInstance() }

    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }

    // ---------- GOOGLE SIGN-IN SETUP ----------

    val webClientId = remember {
        // Comes from google-services.json / strings.xml
        context.getString(R.string.default_web_client_id)
    }

    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            val idToken = account.idToken

            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential)
                    .addOnCompleteListener(activity) { authResult ->
                        if (authResult.isSuccessful) {
                            Toast.makeText(context, "Google login success", Toast.LENGTH_SHORT)
                                .show()
                            handleAuthSuccess(
                                navController = navController,
                                firebaseAuth = firebaseAuth,
                                provider = "google",
                                phoneNumberOverride = null
                            )
                        } else {
                            Log.e(
                                "GirlSpace",
                                "Google login: Firebase signIn failed",
                                authResult.exception
                            )
                            Toast.makeText(
                                context,
                                "Google login failed: ${authResult.exception?.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            } else {
                Toast.makeText(context, "Google login failed: No ID token", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: Exception) {
            Log.e("GirlSpace", "Google sign-in error", e)
            Toast.makeText(
                context,
                "Google sign-in error: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------- FACEBOOK LOGIN CALLBACK SETUP ----------

    LaunchedEffect(Unit) {
        LoginManager.getInstance().registerCallback(
            MainActivity.fbCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val credential =
                        com.google.firebase.auth.FacebookAuthProvider.getCredential(
                            result.accessToken.token
                        )
                    firebaseAuth.signInWithCredential(credential)
                        .addOnCompleteListener(activity) { authResult ->
                            if (authResult.isSuccessful) {
                                Toast.makeText(
                                    context,
                                    "Facebook login success",
                                    Toast.LENGTH_SHORT
                                ).show()
                                handleAuthSuccess(
                                    navController = navController,
                                    firebaseAuth = firebaseAuth,
                                    provider = "facebook",
                                    phoneNumberOverride = null
                                )
                            } else {
                                Log.e(
                                    "GirlSpace",
                                    "Facebook login: Firebase signIn failed",
                                    authResult.exception
                                )
                                Toast.makeText(
                                    context,
                                    "Facebook login failed: ${authResult.exception?.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }

                override fun onCancel() {
                    Toast.makeText(context, "Facebook login cancelled", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: FacebookException) {
                    Log.e("GirlSpace", "Facebook login error", error)
                    Toast.makeText(
                        context,
                        "Facebook login error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // ---------- PHONE AUTH CALLBACKS ----------

    val phoneCallbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                firebaseAuth.signInWithCredential(credential)
                    .addOnCompleteListener(activity) { authResult ->
                        isSendingOtp = false
                        isVerifying = false
                        if (authResult.isSuccessful) {
                            Toast.makeText(
                                context,
                                "Phone login success (auto)",
                                Toast.LENGTH_SHORT
                            ).show()
                            handleAuthSuccess(
                                navController = navController,
                                firebaseAuth = firebaseAuth,
                                provider = "phone",
                                phoneNumberOverride = phoneNumber
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Phone login failed: ${authResult.exception?.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                isSendingOtp = false
                isVerifying = false
                Log.e("GirlSpace", "Phone verification failed", e)
                Toast.makeText(
                    context,
                    "Phone verification failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onCodeSent(
                verId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                isSendingOtp = false
                verificationId = verId
                resendToken = token
                otpSent = true
                Toast.makeText(context, "OTP sent", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- UI ----------

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Welcome to GirlSpace",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Login to continue. You can always change your vibe later.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 🔹 Google Login
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // Clear last Google account so picker shows
                    googleSignInClient.signOut().addOnCompleteListener {
                        val signInIntent = googleSignInClient.signInIntent
                        googleLauncher.launch(signInIntent)
                    }
                }
            ) {
                Text("Continue with Google")
            }

            // 🔹 Facebook Login
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    LoginManager.getInstance()
                        .logInWithReadPermissions(activity, listOf("public_profile", "email"))
                }
            ) {
                Text("Continue with Facebook")
            }

            // 🔹 Phone number login
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Or use phone OTP",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone number (+91...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Phone
                )
            )

            if (otpSent) {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("Enter OTP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    )
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val verId = verificationId
                        if (verId.isNullOrEmpty()) {
                            Toast.makeText(
                                context,
                                "No verification ID, please resend OTP",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (otpCode.isBlank()) {
                            Toast.makeText(
                                context,
                                "Enter OTP",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            isVerifying = true
                            val credential =
                                PhoneAuthProvider.getCredential(verId, otpCode.trim())
                            firebaseAuth.signInWithCredential(credential)
                                .addOnCompleteListener(activity) { authResult ->
                                    isVerifying = false
                                    if (authResult.isSuccessful) {
                                        Toast.makeText(
                                            context,
                                            "Phone login success",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        handleAuthSuccess(
                                            navController = navController,
                                            firebaseAuth = firebaseAuth,
                                            provider = "phone",
                                            phoneNumberOverride = phoneNumber
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "OTP verify failed: ${authResult.exception?.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        }
                    }
                ) {
                    Text(if (isVerifying) "Verifying..." else "Verify & Continue")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (phoneNumber.isBlank()) {
                            Toast.makeText(
                                context,
                                "Enter phone number",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        isSendingOtp = true
                        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                            .setPhoneNumber(phoneNumber.trim())
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(phoneCallbacks)
                            .apply {
                                resendToken?.let {
                                    this.setForceResendingToken(it)
                                }
                            }
                            .build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    }
                ) {
                    Text(if (isSendingOtp) "Sending OTP..." else "Send OTP")
                }
            }
        }
    }
}

// After any successful login: save profile + decide Home vs Onboarding per user
private fun handleAuthSuccess(
    navController: NavHostController,
    firebaseAuth: FirebaseAuth,
    provider: String,
    phoneNumberOverride: String? = null
) {
    val user = firebaseAuth.currentUser
    saveUserProfileToFirestore(user, provider, phoneNumberOverride)

    if (user == null) return
    val db = FirebaseFirestore.getInstance()
    val uid = user.uid

    db.collection("users").document(uid)
        .get()
        .addOnSuccessListener { snapshot ->
            val hasVibe = snapshot.getBoolean("hasVibe") ?: false
            if (hasVibe) {
                // Already chose vibe earlier → go straight to Home
                navController.navigate("home_root") {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                // New user (or never picked vibe) → go to onboarding
                navController.navigate("onboarding") {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        .addOnFailureListener { e ->
            Log.e("GirlSpace", "Failed to read hasVibe flag", e)
            // Safe fallback: send to onboarding
            navController.navigate("onboarding") {
                popUpTo("login") { inclusive = true }
                launchSingleTop = true
            }
        }
}

// Create/update /users/{uid} document with basic info
// For new users, also default hasVibe = false
private fun saveUserProfileToFirestore(
    user: FirebaseUser?,
    provider: String,
    phoneNumberOverride: String? = null
) {
    if (user == null) return

    val db = FirebaseFirestore.getInstance()
    val uid = user.uid
    val userDocRef = db.collection("users").document(uid)

    userDocRef.get()
        .addOnSuccessListener { snapshot ->
            val alreadyExists = snapshot.exists()

            val existingPlan = snapshot.getString("plan")
            val existingIsPremium = snapshot.getBoolean("isPremium")
            val existingHasVibe = snapshot.getBoolean("hasVibe")

            val profile = hashMapOf(
                "uid" to uid,
                "name" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "phone" to (phoneNumberOverride ?: user.phoneNumber ?: ""),
                "provider" to provider,
                "updatedAt" to FieldValue.serverTimestamp()
            ).apply {
                // 📌 Preserve existing photoUrl if it exists (from Firestore)
                val existingPhoto = snapshot.getString("photoUrl")
                put("photoUrl", existingPhoto ?: (user.photoUrl?.toString() ?: ""))
            }
                .apply {
                put("plan", existingPlan ?: "free")
                put("isPremium", existingIsPremium ?: false)
                // Only default hasVibe=false when doc is new or field missing
                put("hasVibe", existingHasVibe ?: false)
            }

            userDocRef.set(profile, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("GirlSpace", "User profile saved for uid=$uid (exists=$alreadyExists)")
                }
                .addOnFailureListener { e ->
                    Log.e("GirlSpace", "Failed to save user profile", e)
                }
        }
        .addOnFailureListener { e ->
            Log.e("GirlSpace", "Failed to fetch existing user profile", e)
        }
}
