package com.sidepanel.app.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sidepanel.app.data.pairing.PairingUriParser
import com.sidepanel.app.data.pairing.ParsedPairingInfo
import java.util.concurrent.Executors

private const val TAG = "QrScanner"

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (ParsedPairingInfo) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                CameraPreviewWithAnalyzer(
                    onQrScanned = onQrScanned
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Camera access is needed to scan the pairing QR code displayed on your PC.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                    ) {
                        Text("Grant Camera Permission", color = Color.White)
                    }
                }
            }

            // Top Bar with Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Point camera at SidePanel PC QR code",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Scanner",
                        tint = Color.White
                    )
                }
            }

            // Center Target Reticle Box
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.Center)
                    .border(2.dp, Color(0xFF58A6FF), RoundedCornerShape(16.dp))
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewWithAnalyzer(
    onQrScanned: (ParsedPairingInfo) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    var isProcessing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !isProcessing) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    if (barcode.valueType == Barcode.TYPE_TEXT ||
                                        barcode.valueType == Barcode.TYPE_URL
                                    ) {
                                        val rawValue = barcode.rawValue ?: ""
                                        val parsed = PairingUriParser.parse(rawValue)
                                        if (parsed != null && !isProcessing) {
                                            isProcessing = true
                                            Log.i(TAG, "QR Code scanned successfully: $parsed")
                                            onQrScanned(parsed)
                                            break
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera lifecycle: ${e.message}", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
