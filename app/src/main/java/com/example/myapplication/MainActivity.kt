package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection

import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val pointsFlow = MutableSharedFlow<List<Pair<Float, Float>>>()
    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {


            MyApplicationTheme {



                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val metrics = DisplayMetrics()
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.defaultDisplay.getMetrics(metrics)
                var previewWidth by remember { mutableStateOf(0) }
                var previewHeight by remember { mutableStateOf(0) }

                // Stany dla wymiarów obrazu z kamery (ImageAnalysis)
                var imageWidth by remember { mutableStateOf(0) }
                var imageHeight by remember { mutableStateOf(0) }

                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { ctx ->

                        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
                    }, modifier = Modifier.fillMaxSize().onGloballyPositioned { coords ->
                        previewWidth = coords.size.width
                        previewHeight = coords.size.height
                    }, update = { x ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().setTargetResolution(Size(metrics.widthPixels,metrics.heightPixels)).build().also {
                                it.setSurfaceProvider(x.surfaceProvider)
                            }
                            val options = PoseDetectorOptions.Builder()
                                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)  // Tryb strumieniowy
                                .build()
                            val analyzer = Analyzer { proxy :ImageProxy->
                                imageWidth = proxy.width
                                imageHeight = proxy.height
                                if (proxy.image != null) {
                                    val poseidentifier = PoseDetection.getClient(options);
                                    val imageForMlKit = InputImage.fromMediaImage(
                                        proxy.image!!,
                                        proxy.imageInfo.rotationDegrees
                                    )
                                    poseidentifier.process(imageForMlKit)
                                        .addOnSuccessListener { pose ->
                                            Log.e("Tag", "To jest komunikat o tym ze jest sucess ")
                                            // Zajmujemy się wynikiem detekcji
                                            lifecycleScope.launch {
                                                pointsFlow.emit(processPose(pose))}
                                        }
                                        .addOnFailureListener { e ->
                                            // Obsługa błędów
                                            Log.e("Tag", "To jest komunikat o tym ze nie dziala  ")
                                            e.printStackTrace()
                                        }
                                        .addOnCompleteListener {
                                            // Zamknięcie ImageProxy po przetworzeniu
                                            Log.e("Tag", "To jest komunikat o tym ze jest complete ")
                                            proxy.close()
                                        }

                                    // tomek tutaj mam dostep do image proxy wystarczylo podpiac analyzera do lifecycle
                                    //Log.e("Tag", "To jest komunikat o tym ze dziala ")

                                }else{proxy.close()}

                            }
                            val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) .build();
                            imageAnalysis.setAnalyzer(mainExecutor,analyzer)


                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview,imageAnalysis)
                            Log.e("Tag", "To jest komunikat o tym ze dziala ")


                            // Możesz teraz uzyskać rozdzielczość kamery


                        }, ContextCompat.getMainExecutor(context))
                    })
                    var points by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
                    // Nasłuchiwanie punktów emitowanych przez flow
                    LaunchedEffect(Unit) {
                        pointsFlow.collectLatest { newPoints ->
                            points = newPoints
                            // Można zaimplementować tutaj logikę rysowania, np. wyświetlanie punktów na Canvas
                        }
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (imageWidth > 0 && imageHeight > 0 && previewWidth > 0 && previewHeight > 0) {
                            // Obliczamy współczynnik skalowania. Przyjmujemy, że obraz jest skalowany w trybie FIT_CENTER.
                            val scale = minOf(
                                previewWidth.toFloat() / imageWidth,
                                previewHeight.toFloat() / imageHeight
                            )


                            // Dla każdego punktu przeliczamy współrzędne
                            points.forEach { (x, y) ->
                                // x i y są w układzie obrazu (np. 1280x720)
                                val canvasX =  x * scale
                                val canvasY =  y * scale
                                drawCircle(
                                    color = Color.Red,
                                    radius = 10f,
                                    center = Offset(canvasX, canvasY)
                                )
                            }
                        }
                    }
                }


            }
        }

    }


    fun processPose(pose: Pose) : List<Pair<Float,Float>> {
        // Przechodzimy przez wszystkie punkty ciała (landmarks)
        Log.e("Tag", "To jest komunikat o tym ze jestem w pose ")
        val list = mutableListOf<Pair<Float,Float>>();
        for (landmark in pose.allPoseLandmarks) {
            val position = landmark.position
            // Możemy np. wyświetlić pozycję ciała lub przetwarzać dane w inny sposób
            list.add(Pair(position.x,position.y))
            Log.e("Tag", "Landmark: ${landmark.landmarkType} -> Position: ${position.x}, ${position.y}")
        }
        return list;
    }
}















