package com.phantom.mesh.calling

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object CameraVideoEngine {
    private const val TAG = "CameraVideoEngine"
    private const val TARGET_SAMPLE_WIDTH = 240
    private const val TARGET_SAMPLE_HEIGHT = 180
    private const val FRAME_INTERVAL_MS = 160L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraLock = Any()

    private var appContext: Context? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var activePreviewSurface: Surface? = null
    private var configuredSurfaceTexture: SurfaceTexture? = null
    private var currentSurfaceTexture: SurfaceTexture? = null
    private var activeTextureView: WeakReference<TextureView>? = null
    private var isFrontCamera: Boolean = true
    private var onFrameCapturedCallback: ((ByteArray) -> Unit)? = null

    private var previewSize: Size = Size(640, 480)

    private val isRunning = AtomicBoolean(false)
    private val isOpening = AtomicBoolean(false)
    private val isConfiguring = AtomicBoolean(false)
    private val isCapturing = AtomicBoolean(false)

    // Extracts Bitmap strictly on the Main UI thread (required by Android libhwui for TextureView)
    // and compresses JPEG on the background thread.
    private val frameSamplerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            val tv = activeTextureView?.get()
            if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0 && isCapturing.compareAndSet(false, true)) {
                mainHandler.post {
                    var bmp: Bitmap? = null
                    try {
                        if (isRunning.get() && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                            bmp = tv.getBitmap(TARGET_SAMPLE_WIDTH, TARGET_SAMPLE_HEIGHT)
                        }
                    } catch (e: Throwable) {
                        Log.d(TAG, "Main thread getBitmap skip: ${e.message}")
                    }

                    val capturedBmp = bmp
                    val bg = backgroundHandler
                    if (capturedBmp != null && bg != null && isRunning.get()) {
                        bg.post {
                            try {
                                val baos = ByteArrayOutputStream()
                                capturedBmp.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                                val bytes = baos.toByteArray()
                                if (bytes.isNotEmpty() && isRunning.get()) {
                                    onFrameCapturedCallback?.invoke(bytes)
                                }
                            } catch (_: Throwable) {
                            } finally {
                                try { capturedBmp.recycle() } catch (_: Throwable) {}
                                isCapturing.set(false)
                            }
                        }
                    } else {
                        try { capturedBmp?.recycle() } catch (_: Throwable) {}
                        isCapturing.set(false)
                    }
                }
            }

            if (isRunning.get()) {
                backgroundHandler?.postDelayed(this, FRAME_INTERVAL_MS)
            }
        }
    }

    fun start(
        context: Context,
        surfaceTexture: SurfaceTexture?,
        frontCamera: Boolean,
        onFrameCaptured: (ByteArray) -> Unit
    ) {
        synchronized(cameraLock) {
            appContext = context.applicationContext
            if (surfaceTexture != null) {
                currentSurfaceTexture = surfaceTexture
            }
            isFrontCamera = frontCamera
            onFrameCapturedCallback = onFrameCaptured

            startBackgroundThread()

            if (!isRunning.getAndSet(true)) {
                isOpening.set(false)
                isConfiguring.set(false)
                isCapturing.set(false)
            }

            // Only open camera once the live TextureView SurfaceTexture is attached
            if (currentSurfaceTexture != null) {
                if (cameraDevice == null && !isOpening.get()) {
                    openCamera(context.applicationContext)
                } else if (cameraDevice != null) {
                    startCaptureSession()
                }
            }
        }
    }

    fun attachPreview(textureView: TextureView, surfaceTexture: SurfaceTexture) {
        synchronized(cameraLock) {
            activeTextureView = WeakReference(textureView)
            currentSurfaceTexture = surfaceTexture
            val ctx = appContext ?: textureView.context.applicationContext
            appContext = ctx

            if (isRunning.get()) {
                startBackgroundThread()
                if (cameraDevice == null && !isOpening.get()) {
                    openCamera(ctx)
                } else if (cameraDevice != null && configuredSurfaceTexture !== surfaceTexture) {
                    startCaptureSession()
                }
            }
        }
    }

    fun detachPreview() {
        synchronized(cameraLock) {
            backgroundHandler?.removeCallbacks(frameSamplerRunnable)
            activeTextureView = null
            currentSurfaceTexture = null
            configuredSurfaceTexture = null
            closeCamera()
        }
    }

    fun updatePreviewSurface(context: Context, surfaceTexture: SurfaceTexture?) {
        synchronized(cameraLock) {
            appContext = context.applicationContext
            if (currentSurfaceTexture === surfaceTexture) return
            currentSurfaceTexture = surfaceTexture

            if (isRunning.get() && surfaceTexture != null) {
                if (cameraDevice != null) {
                    startCaptureSession()
                } else if (!isOpening.get()) {
                    openCamera(context.applicationContext)
                }
            }
        }
    }

    fun switchCamera(context: Context, frontCamera: Boolean) {
        synchronized(cameraLock) {
            appContext = context.applicationContext
            if (isFrontCamera == frontCamera) return
            isFrontCamera = frontCamera
            if (isRunning.get()) {
                closeCamera()
                if (currentSurfaceTexture != null) {
                    openCamera(context.applicationContext)
                }
            }
        }
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
        } catch (_: Throwable) {}
        backgroundThread = null
        backgroundHandler = null
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

        if (isOpening.getAndSet(true)) return

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            isOpening.set(false)
            return
        }

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
                isOpening.set(false)
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map != null) {
                previewSize = chooseOptimalPreviewSize(map)
            }

            cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    synchronized(cameraLock) {
                        isOpening.set(false)
                        if (!isRunning.get()) {
                            try { camera.close() } catch (_: Throwable) {}
                            return
                        }
                        cameraDevice = camera
                        startCaptureSession()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    synchronized(cameraLock) {
                        isOpening.set(false)
                        try { camera.close() } catch (_: Throwable) {}
                        if (cameraDevice === camera) {
                            cameraDevice = null
                        }
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    synchronized(cameraLock) {
                        isOpening.set(false)
                        Log.e(TAG, "Camera open error: $error")
                        try { camera.close() } catch (_: Throwable) {}
                        if (cameraDevice === camera) {
                            cameraDevice = null
                        }
                    }
                }
            }, backgroundHandler)
        } catch (e: Throwable) {
            isOpening.set(false)
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
        }
    }

    private fun chooseOptimalPreviewSize(map: StreamConfigurationMap): Size {
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return Size(640, 480)
        return sizes.firstOrNull { it.width == 640 && it.height == 480 }
            ?: sizes.filter { it.width <= 1280 && it.height <= 720 }
                .minByOrNull { Math.abs((it.width * it.height) - (640 * 480)) }
            ?: sizes.firstOrNull()
            ?: Size(640, 480)
    }

    private fun startCaptureSession() {
        synchronized(cameraLock) {
            val camera = cameraDevice ?: return
            if (!isRunning.get()) return

            val texture = currentSurfaceTexture ?: return
            if (isConfiguring.get()) return
            if (captureSession != null && configuredSurfaceTexture === texture) return

            try {
                isConfiguring.set(true)
                backgroundHandler?.removeCallbacks(frameSamplerRunnable)

                try {
                    captureSession?.stopRepeating()
                    captureSession?.close()
                } catch (_: Throwable) {}
                captureSession = null

                try {
                    activePreviewSurface?.release()
                } catch (_: Throwable) {}
                activePreviewSurface = null

                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val previewSurface = Surface(texture)
                if (!previewSurface.isValid) {
                    previewSurface.release()
                    isConfiguring.set(false)
                    return
                }
                activePreviewSurface = previewSurface
                configuredSurfaceTexture = texture

                val sessionCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        synchronized(cameraLock) {
                            isConfiguring.set(false)
                            if (!isRunning.get() || cameraDevice == null || !previewSurface.isValid) {
                                try { session.close() } catch (_: Throwable) {}
                                return
                            }
                            captureSession = session
                            try {
                                val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(previewSurface)
                                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                }
                                session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                                backgroundHandler?.removeCallbacks(frameSamplerRunnable)
                                backgroundHandler?.postDelayed(frameSamplerRunnable, FRAME_INTERVAL_MS)
                            } catch (e: Throwable) {
                                Log.e(TAG, "Failed setting repeating request: ${e.message}", e)
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        synchronized(cameraLock) {
                            isConfiguring.set(false)
                            Log.e(TAG, "Camera capture session configuration failed")
                        }
                    }
                }

                camera.createCaptureSession(listOf(previewSurface), sessionCallback, backgroundHandler)
            } catch (e: Throwable) {
                isConfiguring.set(false)
                Log.e(TAG, "Failed starting capture session: ${e.message}", e)
            }
        }
    }

    private fun closeCamera() {
        synchronized(cameraLock) {
            backgroundHandler?.removeCallbacks(frameSamplerRunnable)
            mainHandler.removeCallbacksAndMessages(null)
            isConfiguring.set(false)
            isCapturing.set(false)

            try {
                captureSession?.stopRepeating()
            } catch (_: Throwable) {}
            try {
                captureSession?.close()
            } catch (_: Throwable) {}
            captureSession = null

            try {
                cameraDevice?.close()
            } catch (_: Throwable) {}
            cameraDevice = null

            try {
                activePreviewSurface?.release()
            } catch (_: Throwable) {}
            activePreviewSurface = null
            configuredSurfaceTexture = null
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        isOpening.set(false)
        synchronized(cameraLock) {
            closeCamera()
            stopBackgroundThread()
            activeTextureView = null
            currentSurfaceTexture = null
            onFrameCapturedCallback = null
            Log.i(TAG, "CameraVideoEngine stopped")
        }
    }
}
