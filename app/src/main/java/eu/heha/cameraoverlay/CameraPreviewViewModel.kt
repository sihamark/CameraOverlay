package eu.heha.cameraoverlay

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CameraPreviewViewModel : ViewModel() {

    var overlayParameters by mutableStateOf(OverlayParameters())
        private set

    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private var camera: Camera? = null

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        val camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner, DEFAULT_BACK_CAMERA, cameraPreviewUseCase
        )

        this.camera = camera

        camera.cameraInfo.zoomState.value?.let {
            camera.cameraControl.setZoomRatio(it.minZoomRatio)
            overlayParameters = overlayParameters.copy(
                zoom = it.minZoomRatio,
                zoomRange = it.minZoomRatio..it.maxZoomRatio
            )
        }

        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }

    fun onChangeTransform(transform: Transform) {
        overlayParameters = overlayParameters.copy(transform = transform)
    }

    fun onChangeAlpha(newAlpha: Float) {
        overlayParameters = overlayParameters.copy(alpha = newAlpha)
    }

    fun onChangeZoom(newZoom: Float) {
        camera?.cameraControl?.setZoomRatio(newZoom)
        overlayParameters = overlayParameters.copy(zoom = newZoom)
    }
}