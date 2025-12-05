package com.girlspace.app.ui.login
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
import androidx.compose.ui.res.painterResource
data class LoginBackgroundSlide(
    @DrawableRes val imageRes: Int,
    val title: String,
    val subtitle: String
)

private val togetherlyLoginSlides = listOf(
    LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_friends,
        title = "Be together.",
        subtitle = "Real people. Real connections."
    ),
        LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_chat,
        title = "Say what you feel.",
        subtitle = "Fast, private conversations."
    ),
LoginBackgroundSlide(
imageRes = R.drawable.login_bg_spiritual,
title = "Be Spiritual.",
subtitle = "Grow with Devotion."
),
    LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_community,
        title = "Find your people.",
        subtitle = "Communities that feel like home."
    ),
    LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_families,
        title = "Find your people.",
        subtitle = "Families that come together."
    ),
    LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_fun,
        title = "Moments that matter.",
        subtitle = "Share life as it happens."
    ),
    LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_global,
        title = "One world. One space.",
        subtitle = "Everyone belongs."
    ),
    LoginBackgroundSlide(
        imageRes = R.drawable.login_bg_abstract,
        title = "Togetherly",
        subtitle = "Your social world."
    )
)

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
                            Toast.makeText(
                                context,
                                "Google login success",
                                Toast.LENGTH_SHORT
                            ).show()
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
                Toast.makeText(
                    context,
                    "Google login failed: No ID token",
                    Toast.LENGTH_LONG
                ).show()
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
                    Toast.makeText(
                        context,
                        "Facebook login cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
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

    val slides = remember { togetherlyLoginSlides }
    var currentIndex by remember { mutableStateOf(0) }

    // Rotate background + quote
    LaunchedEffect(Unit) {
        while (slides.isNotEmpty()) {
            delay(2000L) // 5 seconds per slide
            currentIndex = (currentIndex + 1) % slides.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // BACKGROUND: image + dark gradient overlay
        Crossfade(
            targetState = slides[currentIndex],
            label = "loginBackgroundCrossfade"
        ) { slide ->
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = slide.imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xCC000000),
                                    Color(0x99000000),
                                    Color(0xE6000000)
                                )
                            )
                        )
                )
            }
        }

        // FOREGROUND CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 64.dp // extra bottom padding to lift the form
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top branding
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Togetherly",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your social world.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Middle: current slide quote
            val slide = slides[currentIndex]
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = slide.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = slide.subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Bottom: login form
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Welcome to Togetherly",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Continue with Togetherly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 🔹 Google Login
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
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
                            .logInWithReadPermissions(
                                activity,
                                listOf("public_profile", "email")
                            )
                    }
                ) {
                    Text("Continue with Facebook")
                }

                // 🔹 Phone number login
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Or use phone OTP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone number (+91...)", color = Color.White) },
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
                        label = { Text("Enter OTP", color = Color.White) },
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
                // Preserve existing photoUrl if already stored
                val existingPhoto = snapshot.getString("photoUrl")
                put("photoUrl", existingPhoto ?: (user.photoUrl?.toString() ?: ""))
            }.apply {
                put("plan", existingPlan ?: "free")
                put("isPremium", existingIsPremium ?: false)
                // Only default hasVibe=false when doc is new or field missing
                put("hasVibe", existingHasVibe ?: false)
            }

            userDocRef.set(profile, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(
                        "GirlSpace",
                        "User profile saved for uid=$uid (exists=$alreadyExists)"
                    )
                }
                .addOnFailureListener { e ->
                    Log.e("GirlSpace", "Failed to save user profile", e)
                }
        }
        .addOnFailureListener { e ->
            Log.e("GirlSpace", "Failed to fetch existing user profile", e)
        }
}
