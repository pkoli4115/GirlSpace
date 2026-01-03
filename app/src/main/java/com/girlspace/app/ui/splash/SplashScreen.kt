package com.girlspace.app.ui.splash
import com.google.firebase.auth.FirebaseAuth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girlspace.app.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    firstLaunchDone: Boolean,
    onShowOnboarding: () -> Unit,
    onShowLogin: () -> Unit,
    onShowHome: () -> Unit
)
 {
    val startAnim = remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (startAnim.value) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (startAnim.value) 1f else 0.9f,
        animationSpec = tween(durationMillis = 800),
        label = "splashScale"
    )

     LaunchedEffect(Unit) {
         startAnim.value = true
         delay(2000L)

         val user = FirebaseAuth.getInstance().currentUser

         when {
             user != null -> {
                 // ✅ User already logged in → go straight to Home
                 onShowHome()
             }

             firstLaunchDone -> {
                 // ❌ Not logged in, but has opened app before
                 onShowLogin()
             }

             else -> {
                 // 🆕 First-time user
                 onShowOnboarding()
             }
         }
     }


     Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6C63FF), // purple
                        Color(0xFFFF6FD8), // pink
                        Color(0xFFFFA84B)  // orange
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main app title (Togetherly)
            Text(
                text = "Togetherly",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your social world",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // “Powered by Firebase” chip
            Surface(
                color = Color(0x33000000),
                shape = RoundedCornerShape(50),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = "Powered by Firebase",
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Developed by QTI Labs – with logo you shared
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // TODO: put your QTI Labs logo into res/drawable as ic_qti_labs
                Image(
                    painter = painterResource(id = R.drawable.ic_qti_labs),
                    contentDescription = "QTI Labs logo",
                    modifier = Modifier
                        .height(28.dp)
                        .padding(end = 8.dp),
                    contentScale = ContentScale.Fit
                )
                Column {
                    Text(
                        text = "Developed by",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "QTI Labs",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
