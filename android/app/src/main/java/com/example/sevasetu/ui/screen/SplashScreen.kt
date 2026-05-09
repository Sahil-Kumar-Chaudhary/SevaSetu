package com.example.sevasetu.ui.screen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sevasetu.Login
import com.example.sevasetu.R
import com.example.sevasetu.notifications.FcmTokenRegistrar
import com.example.sevasetu.notifications.NotificationPermissionHelper
import com.example.sevasetu.notifications.NotificationSupport
import com.example.sevasetu.ui.theme.SevaSetuTheme
import com.example.sevasetu.utils.ThemePreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class SplashScreen : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize notification channels only (no backend call before auth)
        try {
            FcmTokenRegistrar.initializeNotificationChannels(this)
            Log.d("SplashScreen", "Notification system initialized")
        } catch (e: Exception) {
            Log.e("SplashScreen", "Error initializing notification system", e)
        }

        val themePreferenceManager = ThemePreferenceManager(this)
        enableEdgeToEdge()
        setContent {
            val themePreference = remember { themePreferenceManager.getTheme() }
            SevaSetuTheme(themePreference = themePreference) {
                SplashScreenContent(onNavigate = {
                    startActivity(Intent(this, Login::class.java))
                    finish()
                })
            }
        }
    }
}

@Composable
fun SplashScreenContent(onNavigate: () -> Unit) {
    val logoScale = remember { Animatable(0.8f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            try {
                val url = URL("https://sevasetu-zqa6.onrender.com/")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()

                connection.inputStream.close()
                connection.disconnect()
            } catch (_: Exception) {
                // Ignore errors (server might still be waking up)
            }
        }

        logoAlpha.animateTo(1f, animationSpec = tween(1200))
        logoScale.animateTo(1f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
        delay(3000)
        onNavigate()
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // 🔹 Background Image
        Image(
            painter = painterResource(id = R.drawable.backgroundscreen),
            contentDescription = "background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 🔹 Gradient Overlay for better contrast and depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // 🔹 Center Content
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Logo container - Refined Professional Design with Animation
            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
                shape = CircleShape,
                color = Color.White,
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color(0xFFEEEEEE))
            ) {
                Box(
                    modifier = Modifier.padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.appicon),
                        contentDescription = "logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Name (SevaSetu)
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFF333333))) {
                        append("Seva")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF0B6B4F))) {
                        append("Setu")
                    }
                },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Connecting Citizens to Better Cities",
                color = Color.Gray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )
        }

        // 🔹 Bottom Section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(4.dp)
                    .clip(CircleShape),
                color = Color(0xFF0B6B4F),
                trackColor = Color(0xFF0B6B4F).copy(alpha = 0.1f),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "✔ VERIFIED CIVIC INFRASTRUCTURE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashScreenContent {
    }
}