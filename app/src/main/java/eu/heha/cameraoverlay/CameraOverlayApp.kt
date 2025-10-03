package eu.heha.cameraoverlay

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.heha.cameraoverlay.ui.theme.CameraOverlayTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraOverlayApp() {
    CameraOverlayTheme {
        Scaffold(
            contentWindowInsets = WindowInsets()
        ) { innerPadding ->
            val context = LocalContext.current
            val sheetState = rememberModalBottomSheetState()
            var isSettingsSheetVisible by remember { mutableStateOf(false) }
            val model: CameraPreviewViewModel = viewModel()
            LaunchedEffect(model) {
                model.loadImage(context)
            }
            val mediaRequest = rememberLauncherForActivityResult(PickVisualMedia()) { imageUri ->
                if (imageUri != null) model.loadImageFromUri(context, imageUri)
            }
            Box(
                contentAlignment = Alignment.Companion.Center,
                modifier = Modifier.Companion.padding(innerPadding)
            ) {
                LifecycleResumeEffect(Unit) {
                    model.checkCameraPermission(context)
                    onPauseOrDispose { }
                }
                if (!model.state.isCameraPermissionGranted) {
                    val activity = LocalActivity.current
                    PermissionPrompt(
                        onClickRequest = {
                            model.requestCameraPermission(activity)
                        }
                    )
                } else {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    CameraPreviewContent(
                        surfaceRequest = model.state.surfaceRequest,
                        onStart = { model.bindToCamera(context, lifecycleOwner) },
                        lifecycleOwner = lifecycleOwner,
                        modifier = Modifier.Companion.fillMaxSize()
                    )
                    ImageOverlay(
                        model.state.overlayState,
                        onChangeTransform = model::onChangeTransform
                    )
                    AnimatedVisibility(
                        !isSettingsSheetVisible,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .safeContentPadding()
                    ) {
                        IconButton({ isSettingsSheetVisible = true }) {
                            Icon(Icons.Default.Settings, "Open Settings")
                        }
                    }
                }
            }

            if (isSettingsSheetVisible) {
                ModalBottomSheet(
                    onDismissRequest = { isSettingsSheetVisible = false },
                    sheetState = sheetState
                ) {
                    SettingsContent(
                        alpha = model.state.overlayState.alpha,
                        onAlphaChanged = model::onChangeAlpha,
                        zoom = model.state.overlayState.zoom,
                        zoomRange = model.state.overlayState.zoomRange,
                        onZoomChanged = model::onChangeZoom,
                        onClickResetTransform = model::resetTransform,
                        onClickSelectImage = {
                            mediaRequest.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onClickRequest: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            "Grant the Camera Permission!!!",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.Companion.height(16.dp))
        Button(onClickRequest) {
            Text("Request Permission", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ImageOverlay(
    overlayState: OverlayState,
    onChangeTransform: (Transform) -> Unit
) {
    fun Offset.rotateBy(angle: Float): Offset {
        val angleInRadians = angle * (PI / 180)
        val cos = cos(angleInRadians)
        val sin = sin(angleInRadians)
        return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
    }

    if (overlayState.image == null) {
        return
    }

    val transform = overlayState.transform

    var offset by remember { mutableStateOf(transform.offset) }
    var zoom by remember { mutableFloatStateOf(transform.zoom) }
    var angle by remember { mutableFloatStateOf(transform.angle) }

    LaunchedEffect(transform) {
        if (!transform.sameAs(offset, zoom, angle)) {
            offset = transform.offset
            zoom = transform.zoom
            angle = transform.angle
        }
    }

    LaunchedEffect(offset, zoom, angle) {
        if (!transform.sameAs(offset, zoom, angle)) {
            delay(0.3.seconds)
            onChangeTransform(Transform(offset, zoom, angle))
        }
    }

    Box(
        Modifier
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { centroid, pan, gestureZoom, gestureRotate ->
                        val oldScale = zoom
                        val newScale = zoom * gestureZoom
                        // For natural zooming and rotating, the centroid of the gesture should
                        // be the fixed point where zooming and rotating occurs.
                        // We compute where the centroid was (in the pre-transformed coordinate
                        // space), and then compute where it will be after this delta.
                        // We then compute what the new offset should be to keep the centroid
                        // visually stationary for rotating and zooming, and also apply the pan.
                        offset =
                            (offset + centroid / oldScale).rotateBy(gestureRotate) -
                                    (centroid / newScale + pan / oldScale)
                        zoom = newScale
                        angle += gestureRotate
                    }
                )
            }
            .graphicsLayer {
                translationX = -offset.x * zoom
                translationY = -offset.y * zoom
                scaleX = zoom
                scaleY = zoom
                rotationZ = angle
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .fillMaxSize()
    ) {

        Image(
            overlayState.image,
            contentDescription = null,
            contentScale = ContentScale.Inside,
            modifier = Modifier
                .alpha(overlayState.alpha)
                .fillMaxSize()
        )
    }
}

@Composable
private fun CameraPreviewContent(
    surfaceRequest: SurfaceRequest?,
    onStart: suspend () -> Unit,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(lifecycleOwner) {
        onStart()
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    alpha: Float,
    onAlphaChanged: (Float) -> Unit,
    zoom: Float,
    zoomRange: ClosedFloatingPointRange<Float>,
    onZoomChanged: (Float) -> Unit,
    onClickResetTransform: () -> Unit,
    onClickSelectImage: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 16.dp, horizontal = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        Text("Alpha", style = MaterialTheme.typography.labelLarge)
        val alphaState = rememberSliderState(
            value = alpha, steps = 20, valueRange = 0f..1f
        )
        LaunchedEffect(alphaState.value) {
            onAlphaChanged(alphaState.value)
        }
        Slider(alphaState, Modifier.fillMaxWidth())
        Text("%.2f".format(alphaState.value), style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        Text("Zoom", style = MaterialTheme.typography.labelLarge)
        val zoomState = rememberSliderState(
            value = zoom, steps = 100, valueRange = zoomRange
        )
        LaunchedEffect(zoomState.value) {
            onZoomChanged(zoomState.value)
        }
        Slider(zoomState, Modifier.fillMaxWidth())
        Text("%.2f".format(zoomState.value), style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClickResetTransform) {
            Icon(Icons.Default.Refresh, "Reset Transform")
            Spacer(Modifier.width(8.dp))
            Text("Reset Transform")
        }

        OutlinedButton(onClickSelectImage) {
            Icon(Icons.Outlined.Image, "Select Image")
            Spacer(Modifier.width(8.dp))
            Text("Select Image")
        }
    }
}

data class State(
    val isCameraPermissionGranted: Boolean = false,
    val surfaceRequest: SurfaceRequest? = null,
    val overlayState: OverlayState = OverlayState()
)

data class OverlayState(
    val image: Painter? = null,
    val alpha: Float = 0.5f,
    val zoom: Float = 1f,
    val zoomRange: ClosedFloatingPointRange<Float> = 1f..2f,
    val transform: Transform = Transform()
)

data class Transform(
    val offset: Offset = Offset.Zero,
    val zoom: Float = 1f,
    val angle: Float = 0f
) {
    fun sameAs(
        offset: Offset, zoom: Float, angle: Float
    ) = this.offset == offset && this.zoom == zoom && this.angle == angle
}