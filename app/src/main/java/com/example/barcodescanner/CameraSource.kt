package com.example.barcodescanner

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import java.nio.ByteBuffer

class CameraSource(private val context: Context, private val surfaceView: SurfaceView, private val detector: Detector<*>) {

    private var camera: Camera? = null
    private var isProcessing = false
    private val cameraLock = Any()

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                start(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // Этот метод можно оставить пустым или добавить логику для обработки изменений поверхности.
                setCameraDisplayOrientation()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stop()
            }
        })
    }

    fun getCamera(): Camera? {
        return camera
    }
    @Throws(Exception::class)
    fun start(holder: SurfaceHolder) {
        synchronized(cameraLock) {
            if (camera == null) {
                camera = Camera.open()
                camera?.setPreviewDisplay(holder)

                // Установка правильной ориентации камеры
                setCameraDisplayOrientation()

                // Установка режима фокусировки
                val parameters = camera?.parameters
                if (parameters != null) {
                    if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    }
                    camera?.parameters = parameters
                }

                camera?.setPreviewCallback { data, camera ->
                    if (isProcessing) return@setPreviewCallback

                    isProcessing = true

                    val parameters = camera.parameters
                    val previewSize = parameters.previewSize

                    val frame = Frame.Builder()
                        .setImageData(ByteBuffer.wrap(data), previewSize.width, previewSize.height, ImageFormat.NV21)
                        .build()

                    detector.receiveFrame(frame)

                    isProcessing = false
                }
                camera?.startPreview() // Запускаем предварительный просмотр.
            }
        }
    }

    private fun setCameraDisplayOrientation() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayRotation = windowManager.defaultDisplay.rotation

        val cameraInfo = Camera.CameraInfo()
        Camera.getCameraInfo(0, cameraInfo)

        var degrees = 0
        when (displayRotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = -90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = -270
        }

        val result: Int = (cameraInfo.orientation + degrees) % 360

        camera?.setDisplayOrientation(result)
    }

    // Метод для остановки обработки кадров без остановки предварительного просмотра.
    fun stopProcessing() {
        isProcessing = true // Останавливаем обработку кадров.
    }

    // Метод для возобновления обработки кадров.
    fun resumeProcessing() {
        isProcessing = false // Возвращаем возможность обработки кадров.
    }

    fun stop() {
        synchronized(cameraLock) {
            if (camera != null) {
                camera?.stopPreview() // Останавливаем предварительный просмотр камеры.
                camera?.setPreviewCallback(null) // Отменяем обратный вызов перед освобождением камеры.
                camera?.release()
                camera = null
            }
        }
    }

    fun release() {
        stop() // Остановка и освобождение ресурсов камеры и детектора.
    }
}