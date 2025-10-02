package eu.heha.cameraoverlay

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.heha.cameraoverlay.ui.theme.CameraOverlayTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CameraOverlayApp() {
    CameraOverlayTheme {
        Scaffold(
            contentWindowInsets = WindowInsets()
        ) { innerPadding ->
            Box(
                contentAlignment = Alignment.Companion.Center,
                modifier = Modifier.Companion.padding(innerPadding)
            ) {
                val context = LocalContext.current
                val activity = LocalActivity.current
                var isPermissionGranted by remember { mutableStateOf(false) }
                LifecycleResumeEffect(Unit) {
                    isPermissionGranted = ContextCompat
                        .checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    onPauseOrDispose { }
                }
                if (!isPermissionGranted) {
                    Column {
                        Text(
                            "Grant the Camera Permission!!!",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(Modifier.Companion.height(16.dp))
                        Button(onClick = {
                            if (activity != null) {
                                ActivityCompat.requestPermissions(
                                    activity, arrayOf(Manifest.permission.CAMERA), 0
                                )
                            }
                        }) {
                            Text("Request Permission")
                        }
                    }
                } else {
                    val viewModel: CameraPreviewViewModel = viewModel()
                    CameraPreviewContent(
                        viewModel = viewModel,
                        modifier = Modifier.Companion.fillMaxSize()
                    )
                    ImageOverlay(
                        viewModel.overlayParameters,
                        viewModel::onChangeTransform
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageOverlay(
    overlayParameters: OverlayParameters,
    onChangeTransform: (Transform) -> Unit
) {
    fun Offset.rotateBy(angle: Float): Offset {
        val angleInRadians = angle * (PI / 180)
        val cos = cos(angleInRadians)
        val sin = sin(angleInRadians)
        return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
    }

    val transform = overlayParameters.transform
    Box(
        Modifier
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { centroid, pan, gestureZoom, gestureRotate ->
                        val oldScale = transform.zoom
                        val newScale = transform.zoom * gestureZoom

                        // For natural zooming and rotating, the centroid of the gesture should
                        // be the fixed point where zooming and rotating occurs.
                        // We compute where the centroid was (in the pre-transformed coordinate
                        // space), and then compute where it will be after this delta.
                        // We then compute what the new offset should be to keep the centroid
                        // visually stationary for rotating and zooming, and also apply the pan.
                        onChangeTransform(
                            Transform(
                                offset = (transform.offset + centroid / oldScale)
                                    .rotateBy(gestureRotate) -
                                        (centroid / newScale + pan / oldScale),
                                zoom = newScale,
                                angle = transform.angle + gestureRotate
                            )
                        )
                    }
                )
            }
            .graphicsLayer {
                translationX = -transform.offset.x * transform.zoom
                translationY = -transform.offset.y * transform.zoom
                scaleX = transform.zoom
                scaleY = transform.zoom
                rotationZ = transform.angle
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .fillMaxSize()
    ) {
        Image(
            painterResource(R.drawable.overlay),
            contentDescription = null,
            contentScale = ContentScale.Inside,
            modifier = Modifier
                .alpha(overlayParameters.alpha)
                .fillMaxSize()
        )
    }
}

@Composable
private fun CameraPreviewContent(
    viewModel: CameraPreviewViewModel,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            modifier = modifier
        )
    }
}

data class OverlayParameters(
    val alpha: Float = 0.5f,
    val transform: Transform = Transform()
)

data class Transform(
    val offset: Offset = Offset.Zero,
    val zoom: Float = 1f,
    val angle: Float = 0f
)