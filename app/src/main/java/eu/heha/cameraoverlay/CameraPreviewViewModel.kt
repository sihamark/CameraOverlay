package eu.heha.cameraoverlay

import android.Manifest.permission.CAMERA
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.io.FileDescriptor

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

    fun loadImageFromUri(context: Context, imageUri: Uri) {
        viewModelScope.launch(IO) {
            val image = context.contentResolver.openFileDescriptor(imageUri, "r")?.use {
                val fileDescriptor: FileDescriptor = it.fileDescriptor
                BitmapFactory.decodeFileDescriptor(fileDescriptor)
            } ?: return@launch

            launch {
                val imageFile = context.imageFile()
                val tempFile = context.filesDir.resolve("cachedImage.png")
                tempFile.delete()
                tempFile.outputStream().buffered().use { output ->
                    image.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                imageFile.delete()
                tempFile.copyTo(imageFile)
            }

            state = state.copy(
                overlayState = state.overlayState.copy(
                    image = BitmapPainter(image.asImageBitmap()),
                    transform = Transform()
                )
            )
        }
    }

    fun loadImage(context: Context) {
        viewModelScope.launch(IO) {
            try {
                val image = context.imageFile().inputStream().buffered().use { input ->
                    BitmapFactory.decodeStream(input)
                }
                state = state.copy(
                    overlayState = state.overlayState.copy(
                        image = BitmapPainter(image.asImageBitmap()),
                        transform = Transform()
                    )
                )
            } catch (e: Exception) {
                Log.e("model", "Unable to load image", e)
            }
        }
    }

    private fun Context.imageFile() = filesDir.resolve("image.png")
}