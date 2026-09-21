package com.illu.relevametal.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CameraCapture(
    onCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }

    var allowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        allowed = granted
    }

    DisposableEffect(Unit) {
        onDispose { provider?.unbindAll() }
    }

    if (!allowed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(Modifier.padding(24.dp)) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Permiso de cámara requerido", style = MaterialTheme.typography.titleMedium)
                    Text("La cámara se usa únicamente para registrar evidencias dentro de la obra.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Conceder permiso")
                    }
                    TextButton(onClick = onCancel) { Text("Volver") }
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(flashMode)
                            .build()
                        capture = imageCapture
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.24f)
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), strokeWidth = 1.5f)
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.5f)
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, size.height / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), strokeWidth = 1.5f)
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.5f)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp),
            shape = MaterialTheme.shapes.large,
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                "Encuadrá el vano completo y mantené el teléfono lo más frontal posible",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 42.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalButton(onClick = onCancel) { Text("Volver") }
            FilledTonalButton(
                onClick = {
                    val next = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                    flashMode = next
                    capture?.flashMode = next
                }
            ) {
                Text(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> "Flash ON"
                        ImageCapture.FLASH_MODE_OFF -> "Flash OFF"
                        else -> "Flash AUTO"
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            error?.let {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val cap = capture ?: return@Button
                    if (saving) return@Button
                    saving = true
                    error = null
                    val dir = File(context.filesDir, "evidence").apply { mkdirs() }
                    val name = "IMG_" + SimpleDateFormat(
                        "yyyyMMdd_HHmmss_SSS",
                        Locale.US
                    ).format(Date()) + ".jpg"
                    val file = File(dir, name)
                    cap.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                saving = false
                                onCaptured(file.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                saving = false
                                error = "No se pudo guardar la foto. Intentá nuevamente."
                            }
                        }
                    )
                },
                modifier = Modifier.size(86.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                enabled = capture != null && !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Text("●", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
