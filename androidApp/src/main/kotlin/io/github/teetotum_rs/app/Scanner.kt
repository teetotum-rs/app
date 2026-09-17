package io.github.teetotum_rs.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

/** The camera, reading QR codes until one is a Knob's; asks for the camera first. */
@Composable
fun Scanner(onCode: (JoinCode) -> Unit) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
    }
    if (allowed) {
        Camera(onCode)
    } else {
        LaunchedEffect(Unit) { ask.launch(Manifest.permission.CAMERA) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ActionButton("Allow the camera", onClick = { ask.launch(Manifest.permission.CAMERA) })
        }
    }
}

@Composable
private fun Camera(onCode: (JoinCode) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val preview = remember { PreviewView(context) }

    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var found = false
        future.addListener({
            val provider = future.get()
            val shown = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val reader = QRCodeReader()
            analysis.setAnalyzer(executor) { image ->
                val code = image.use { decode(reader, it) }
                if (code != null && !found) {
                    found = true
                    ContextCompat.getMainExecutor(context).execute { latest(code) }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, shown, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) future.get().unbindAll()
            executor.shutdown()
        }
    }
    // FILL_CENTER overflows the square; unclipped, it covers the text above.
    AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize().clipToBounds())
}

private val HINTS = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.ALSO_INVERTED to true,
)

private fun decode(reader: QRCodeReader, image: ImageProxy): JoinCode? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val luma = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rows = luma.size / plane.rowStride + if (luma.size % plane.rowStride == 0) 0 else 1
    val source = PlanarYUVLuminanceSource(
        luma,
        plane.rowStride,
        rows,
        0,
        0,
        image.width,
        image.height,
        false,
    )
    return try {
        JoinCode.parse(reader.decode(BinaryBitmap(HybridBinarizer(source)), HINTS).text)
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
