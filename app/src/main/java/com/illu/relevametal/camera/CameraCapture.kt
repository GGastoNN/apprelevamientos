package com.illu.relevametal.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CameraCapture(onCaptured:(String)->Unit, onCancel:()->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    val allowed=ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
    if(!allowed) {
        Column(Modifier.padding(24.dp)) {
            Text("Se necesita permiso de cámara. Volvé atrás y concedelo desde Android.")
            Button(onClick=onCancel){ Text("Volver") }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        AndroidView(factory={ ctx ->
            PreviewView(ctx).also { pv ->
                val future=ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider=future.get()
                    val preview=Preview.Builder().build().also{ it.setSurfaceProvider(pv.surfaceProvider) }
                    capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, modifier=Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.SpaceBetween) {
            OutlinedButton(onClick=onCancel){ Text("Cancelar") }
            Button(onClick={
                val cap=capture ?: return@Button
                val dir=File(context.filesDir,"evidence").apply{mkdirs()}
                val name="IMG_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())+".jpg"
                val file=File(dir,name)
                cap.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object:ImageCapture.OnImageSavedCallback{
                    override fun onImageSaved(output: ImageCapture.OutputFileResults){ onCaptured(file.absolutePath) }
                    override fun onError(exception: ImageCaptureException){}
                })
            }){ Text("Tomar foto") }
        }
    }
}
