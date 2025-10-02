package eu.heha.cameraoverlay

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.awaitCancellation

class CameraPreviewViewModel : ViewModel() {

    var state by mutableStateOf(State())
        private set

    private var camera: Camera? = null

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            state = state.copy(surfaceRequest = newSurfaceRequest)
        }
    }

    suspend fun bindToCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        val camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner, DEFAULT_BACK_CAMERA, cameraPreviewUseCase
        )

        this.camera = camera

        camera.cameraInfo.zoomState.value?.let {
            camera.cameraControl.setZoomRatio(it.minZoomRatio)
            state = state.copy(
                overlayState = state.overlayState.copy(
                    zoom = it.minZoomRatio,
                    zoomRange = it.minZoomRatio..it.maxZoomRatio
                )
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
        state = state.copy(
            overlayState = state.overlayState.copy(transform = transform)
        )
    }

    fun onChangeAlpha(newAlpha: Float) {
        state = state.copy(
            overlayState = state.overlayState.copy(alpha = newAlpha)
        )
    }

    fun onChangeZoom(newZoom: Float) {
        camera?.cameraControl?.setZoomRatio(newZoom)
        state = state.copy(
            overlayState = state.overlayState.copy(zoom = newZoom)
        )
    }

    fun resetTransform() {
        state = state.copy(
            overlayState = state.overlayState.copy(transform = Transform())
        )
    }

    fun requestCameraPermission(activity: Activity?) {
        if (activity == null) return
        ActivityCompat.requestPermissions(
            activity, arrayOf(CAMERA), 0
        )
    }

    fun checkCameraPermission(context: Context) {
        state = state.copy(
            isCameraPermissionGranted =
                ContextCompat.checkSelfPermission(context, CAMERA) == PERMISSION_GRANTED
        )
    }
}