package com.phantom.mesh.calling

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight Camera2 Video Capture and Streaming Engine for PhantomMesh.
 *
 * Provides:
 * - Live local camera preview rendering on a TextureView surface (for PiP).
 * - Real-time downsampled JPEG frame extraction (320x240) at ~2.8 fps.
 * - Hardware camera switching (Front vs Back camera).
 * - Safe resource lifecycle management and permission verification.
 */
object CameraVideoEngine {
    private const val TAG = "CameraVideoEngine"
    private const val FRAME_WIDTH = 320
    private const val FRAME_HEIGHT = 240
    private const val FRAME_INTERVAL_MS = 350L // ~2.8 fps for low-bandwidth mesh & cloud streaming

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentSurfaceTexture: SurfaceTexture? = null
    private var isFrontCamera: Boolean = true
    private var onFrameCapturedCallback: ((ByteArray) -> Unit)? = null
    private var lastFrameTime = 0L

    private val isRunning = AtomicBoolean(false)

    fun start(
        context: Context,
        surfaceTexture: SurfaceTexture?,
        frontCamera: Boolean,
        onFrameCaptured: (ByteArray) -> Unit
    ) {
        currentSurfaceTexture = surfaceTexture
        isFrontCamera = frontCamera
        onFrameCapturedCallback = onFrameCaptured

        if (isRunning.getAndSet(true)) {
            restartCamera(context)
            return
        }

        startBackgroundThread()
        openCamera(context)
    }

    fun switchCamera(context: Context, frontCamera: Boolean) {
        if (isFrontCamera == frontCamera) return
        isFrontCamera = frontCamera
        if (isRunning.get()) {
            restartCamera(context)
        }
    }

    fun updatePreviewSurface(context: Context, surfaceTexture: SurfaceTexture?) {
        currentSurfaceTexture = surfaceTexture
        if (isRunning.get()) {
            restartCamera(context)
        }
    }

    private fun restartCamera(context: Context) {
        closeCamera()
        openCamera(context)
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
            backgroundThread = null
            backgroundHandler = null
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(context: Context) {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCam) {
            Log.w(TAG, "CAMERA permission not granted, skipping video capture")
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

        try {
            val targetFacing = if (isFrontCamera) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }

            var selectedCameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    selectedCameraId = id
                    break
                }
            }
            if (selectedCameraId == null) {
                selectedCameraId = cameraManager.cameraIdList.firstOrNull()
            }
            if (selectedCameraId == null) {
                Log.w(TAG, "No camera found on device")
                return
            }

            // Create ImageReader for compressed JPEG frame capture
            imageReader?.close()
            imageReader = ImageReader.newInstance(FRAME_WIDTH, FRAME_HEIGHT, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val now = System.currentTimeMillis()
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        if (now - lastFrameTime >= FRAME_INTERVAL_MS) {
                            lastFrameTime = now
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            onFrameCapturedCallback?.invoke(bytes)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing camera frame: ${e.message}")
                    } finally {
                        image.close()
                    }
                }, backgroundHandler)
            }

            cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera open error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
        }
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return

        try {
            val surfaces = mutableListOf<Surface>()
            val texture = currentSurfaceTexture
            var previewSurface: Surface? = null

            if (texture != null) {
                texture.setDefaultBufferSize(FRAME_WIDTH, FRAME_HEIGHT)
                previewSurface = Surface(texture)
                surfaces.add(previewSurface)
            }
            surfaces.add(reader.surface)

            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            if (previewSurface != null) {
                                addTarget(previewSurface)
                            }
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        Log.i(TAG, "Camera repeating capture session configured")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set repeating capture request: ${e.message}", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera configuration failed")
                }
            }

            camera.createCaptureSession(surfaces, sessionCallback, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting capture session: ${e.message}", e)
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        closeCamera()
        stopBackgroundThread()
        currentSurfaceTexture = null
        onFrameCapturedCallback = null
        Log.i(TAG, "CameraVideoEngine stopped")
    }
}
