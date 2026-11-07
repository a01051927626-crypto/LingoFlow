package com.example.lingoflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "com.example.lingoflow.START_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "lingoflow_capture"
        private const val NOTIFICATION_ID = 41
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val processing = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private val seenLines = LinkedHashSet<String>()
    private val recentTranslations = ArrayDeque<String>()
    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private var translator: Translator? = null
    private var modelReady = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            startCapturePipeline()
        } catch (e: Exception) {
            updateOverlay("화면 캡처를 시작하지 못했습니다: ${e.localizedMessage}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapturePipeline() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        worker = HandlerThread("LingoFlowCapture").also { it.start() }
        workerHandler = Handler(worker!!.looper)

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < 900 || !processing.compareAndSet(false, true)) {
                source.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastFrameAt = now
            val image = source.acquireLatestImage()
            if (image == null) {
                processing.set(false)
                return@setOnImageAvailableListener
            }
            try {
                val bitmap = imageToBitmap(image)
                image.close()
                recognizeAndTranslate(bitmap)
            } catch (e: Exception) {
                image.close()
                processing.set(false)
            }
        }, workerHandler)

        projection?.registerCallback(projectionCallback, workerHandler)
        virtualDisplay = projection?.createVirtualDisplay(
            "LingoFlowDisplay", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, workerHandler
        )
        setupTranslator()
        updateOverlay("화면을 분석하고 있습니다…")
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }

    private fun setupTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.KOREAN)
            .build()
        translator = Translation.getClient(options)
        translator?.downloadModelIfNeeded()
            ?.addOnSuccessListener {
                modelReady = true
                updateOverlay("번역 준비 완료 · 화면을 스크롤해 보세요.")
            }
            ?.addOnFailureListener {
                updateOverlay("번역 모델 다운로드 실패 · 인터넷 연결을 확인해 주세요.")
            }
    }

    private fun recognizeAndTranslate(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val newLines = result.textBlocks
                    .flatMap { block -> block.lines }
                    .map { it.text.trim() }
                    .filter { it.isNotBlank() }
                    .filter { line ->
                        val key = line.replace(Regex("\\s+"), "")
                        synchronized(seenLines) {
                            if (key in seenLines) false
                            else {
                                seenLines.add(key)
                                while (seenLines.size > 500) {
                                    val iterator = seenLines.iterator()
                                    if (iterator.hasNext()) {
                                        iterator.next()
                                        iterator.remove()
                                    }
                                }
                                true
                            }
                        }
                    }

                if (newLines.isEmpty()) {
                    processing.set(false)
                    bitmap.recycle()
                } else if (!modelReady || translator == null) {
                    updateOverlay("번역 모델을 준비하고 있습니다…")
                    processing.set(false)
                    bitmap.recycle()
                } else {
                    translator?.translate(newLines.joinToString("\\n"))
                        ?.addOnSuccessListener { translated ->
                            synchronized(recentTranslations) {
                                recentTranslations.addLast(translated)
                                while (recentTranslations.size > 8) recentTranslations.removeFirst()
                                updateOverlay(recentTranslations.joinToString("\\n\\n"))
                            }
                            processing.set(false)
                            bitmap.recycle()
                        }
                        ?.addOnFailureListener {
                            updateOverlay("번역에 실패했습니다. 다시 시도해 주세요.")
                            processing.set(false)
                            bitmap.recycle()
                        } ?: run {
                        processing.set(false)
                        bitmap.recycle()
                    }
                }
            }
            .addOnFailureListener {
                processing.set(false)
                bitmap.recycle()
            }
    }

    private fun updateOverlay(text: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE
            putExtra(OverlayService.EXTRA_TEXT, text)
        }
        startService(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "LingoFlow 화면 번역", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LingoFlow 번역 실행 중")
            .setContentText("화면의 일본어 문장을 확인하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
            translator?.close()
            recognizer.close()
            worker?.quitSafely()
        } catch (_: Exception) { }
        projection = null
        virtualDisplay = null
        imageReader = null
        worker = null
        workerHandler = null
        super.onDestroy()
    }
}
