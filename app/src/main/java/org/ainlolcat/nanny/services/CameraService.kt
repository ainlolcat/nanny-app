package org.ainlolcat.nanny.services

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture
import org.ainlolcat.nanny.services.control.TelegramBotService
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.Supplier

class CameraService(private val activity: AppCompatActivity, private val uiThreadRunner: Consumer<Runnable>, private val executor: ExecutorService, private val botHolder: Supplier<TelegramBotService?>) : PermissionHungryService {
    private val TAG = "CameraService"

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraInitializationFailed = false;

    override fun getRequiredPermissions(): Map<String, Int> {
        val permissions: MutableMap<String, Int> = HashMap()
        permissions[Manifest.permission.CAMERA] =
            PermissionHungryService.PERMISSION_COUNTER.getAndIncrement()
        return permissions
    }

    fun takePicture(chatId: String) {
        if (checkIfAllGranted(activity)) {
            uiThreadRunner.accept {
                takePictureXUiRunnable(chatId)
            }
        } else {
            botHolder.get()?.sendBroadcastMessage("Need permissions to take photo")
        }
    }

    private fun takePictureXUiRunnable(chatId: String) {
        // this init will work because only one thread access cameraProviderFuture/imageCapture
        // pay attention to any changes here
        if (cameraProviderFuture == null && !cameraInitializationFailed) {
            try {
                cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Could not init ProcessCameraProvider", e)
                cameraInitializationFailed = true
                botHolder.get()?.sendBroadcastMessage("Could not init camera")
            }
        }
        cameraProviderFuture!!.addListener({
            uiThreadRunner.accept {
                val provider = try {
                    cameraProviderFuture!!.get()

                } catch (e: java.lang.Exception) {
                    Log.e(TAG, "Could not get ProcessCameraProvider from future", e)
                    cameraInitializationFailed = true
                    botHolder.get()?.sendBroadcastMessage("Could not init camera")
                    null
                }
                if (provider != null) {
                    takePictureXCameraProviderListenerUiRunnable(provider, chatId)
                }
            }
        }, executor)
    }

    private fun takePictureXCameraProviderListenerUiRunnable(
        cameraProvider: ProcessCameraProvider, chatId: String
    ) {
        val hasCamera = try {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        } catch (e: CameraInfoUnavailableException) {
            Log.i(TAG, "Cannot check camera availability", e)
            false
        }
        if (!hasCamera) {
            cameraInitializationFailed = true
            executor.execute {
                botHolder.get()?.sendBroadcastMessage("Sorry, this phone does not have front camera")
            }
            return
        }
        if (imageCapture == null) {
            imageCapture = ImageCapture.Builder().setTargetResolution(Size(1920, 1080)).build()
        }
        if (camera == null) {
            camera = cameraProvider.bindToLifecycle(
                activity, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture
            )
        }
        imageCapture!!.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {

            override fun onError(error: ImageCaptureException) {
                Log.i(TAG, "Got error", error)
            }

            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                Log.i(
                    "MainActivity",
                    "Got image $imageProxy of size ${imageProxy.height}x${imageProxy.width}"
                )
                val planeProxy = imageProxy.planes[0]
                val buffer: ByteBuffer = planeProxy.buffer
                val bytes = ByteArray(buffer.remaining())
                val stream = ByteArrayOutputStream()
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    .compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val data = stream.toByteArray()
                Log.i(TAG, "Schedule sending image of size ${data.size}")
                executor.execute {
                    Log.i(TAG, "Start sending image of size ${data.size}")
                    botHolder.get()?.sendImageToChat(chatId, data)
                }
                imageProxy.close()
            }
        })
    }
}