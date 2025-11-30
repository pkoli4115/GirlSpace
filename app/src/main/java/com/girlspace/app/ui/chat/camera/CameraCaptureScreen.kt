package com.girlspace.app.ui.chat.camera
import  androidx.compose.material3.TextButton
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.girlspace.app.ui.chat.ChatViewModel
import java.io.File

/**
 * Full-screen in-app camera for capturing and sending images in chat.
 * Flow:
 * 1) Open for a given threadId
 * 2) User captures photo
 * 3) Preview shown
 * 4) On "Send" -> vm.ensureThreadSelected(threadId) + vm.sendMedia(context, uri)
 */
@Composable
fun CameraCaptureScreen(
    threadId: String,
    onClose: () -> Unit,
    vm: ChatViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler { onClose() }

    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var flashEnabled by remember { mutableStateOf(false) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }

    // Make sure correct thread is selected so vm.sendMedia() works
    LaunchedEffect(threadId) {
        vm.ensureThreadSelected(threadId)
    }

    // Start / restart camera whenever selector or flash changes
    LaunchedEffect(cameraSelector, flashEnabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build()
        val capture = ImageCapture.Builder()
            .setFlashMode(
                if (flashEnabled) ImageCapture.FLASH_MODE_ON
                else ImageCapture.FLASH_MODE_OFF
            )
            .build()

        imageCapture = capture

        try {
            cameraProvider.unbindAll()

            val useCases = arrayListOf(preview, capture)

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            previewView?.let { pv ->
                preview.setSurfaceProvider(pv.surfaceProvider)
            }
        } catch (e: Exception) {
            Log.e("CameraCaptureScreen", "Failed to start camera", e)
        }
    }

    // If we already captured an image, show preview instead of live camera
    if (capturedUri != null) {
        CapturedImagePreview(
            uri = capturedUri!!,
            onSend = {
                // Use existing sendMedia() from your ChatViewModel
                vm.sendMedia(context, capturedUri!!)
                onClose()
            },
            onRetake = { capturedUri = null }
        )
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { flashEnabled = !flashEnabled }) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            cameraSelector =
                                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                else
                                    CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = pv
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Shutter button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
            ) {
                IconButton(
                    onClick = {
                        val capture = imageCapture ?: return@IconButton
                        val outputFile = File(
                            context.cacheDir,
                            "cam_${System.currentTimeMillis()}.jpg"
                        )
                        val outputOptions =
                            ImageCapture.OutputFileOptions.Builder(outputFile).build()

                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(exc: ImageCaptureException) {
                                    Log.e(
                                        "CameraCaptureScreen",
                                        "Image capture failed: ${exc.message}",
                                        exc
                                    )
                                }

                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    capturedUri = Uri.fromFile(outputFile)
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, CircleShape)
                ) {
                    // Empty inner to get white circle button
                }
            }
        }
    }
}

@Composable
private fun CapturedImagePreview(
    uri: Uri,
    onSend: () -> Unit,
    onRetake: () -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRetake) {
                        Text("Retake", color = Color.White)
                    }
                    Button(onClick = onSend) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
