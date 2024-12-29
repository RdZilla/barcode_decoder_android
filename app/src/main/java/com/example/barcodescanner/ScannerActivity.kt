package com.example.barcodescanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

class ScannerActivity : AppCompatActivity() {

    private lateinit var cameraPreview: SurfaceView
    private lateinit var scanButton: Button
    private lateinit var exitButton: Button
    private lateinit var cameraSource: CameraSource
    private lateinit var barcodeDetector: BarcodeDetector
    private var isScanning = false
    private lateinit var roiView: RoiView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        cameraPreview = findViewById(R.id.cameraPreview)
        scanButton = findViewById(R.id.scanButton)
        exitButton = findViewById(R.id.exitButton)
        roiView = RoiView(this) // Создаем новый элемент для отображения ROI
        addContentView(
            roiView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val fileName = intent.getStringExtra("FILE_NAME") ?: "default.xls"

        // Инициализация детектора для сканирования штрих-кодов
        barcodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.ALL_FORMATS)
            .build()

        // Инициализация CameraSource с детектором
        cameraSource = CameraSource(this, cameraPreview, barcodeDetector)

        // Установка обработчика для BarcodeDetector
        barcodeDetector.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {
                // Освобождение ресурсов при завершении работы детектора (если необходимо)
            }

            override fun receiveDetections(detections: Detector.Detections<Barcode>) {
                if (!isScanning) return // Если не в режиме сканирования, игнорируем

                val items = detections.detectedItems
                if (items.size() > 0) {
                    val scannedValue =
                        items.valueAt(0).displayValue // Получаем значение первого обнаруженного штрих-кода
                    saveToExcel(fileName, scannedValue)
                    stopScanning() // Останавливаем сканирование после успешного считывания
                }
            }
        })
        scanButton.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }

        exitButton.setOnClickListener {
            stopScanning() // Останавливаем сканирование перед выходом.
            finish() // Закрываем текущее окно и возвращаемся на стартовый экран.
        }
        cameraPreview.setOnTouchListener { _, _ ->
            cameraSource.getCamera()?.autoFocus { success, _ ->
                if (success) {
                    Log.d("CameraSource", "Фокусировка выполнена успешно")
                } else {
                    Log.d("CameraSource", "Ошибка при фокусировке")
                }
            }
            true // Возвращаем true, чтобы указать, что событие обработано
        }

    }

    private fun startScanning() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            return
        }

        isScanning = true
        scanButton.text = "Стоп"

        // Запуск камеры и сканирования.
        try {
            cameraSource.start(cameraPreview.holder)
        } catch (e: Exception) {
            Log.e("CameraSource", "Ошибка при запуске камеры", e)
            stopScanning()
        }
    }

    private fun stopScanning() {
        if (!isScanning) return // Если уже остановлено, ничего не делаем.

        isScanning = false
        scanButton.text = "Сканировать"

        // Остановка обработки кадров.
        cameraSource.stopProcessing()

        // Вы можете оставить предварительный просмотр активным.
    }


    private fun saveToExcel(fileName: String, scannedValue: String) {
        Toast.makeText(this, scannedValue, Toast.LENGTH_LONG).show()
        val current_dates = SimpleDateFormat("yyyy-MM-dd").format(Date())
        // Путь к директории для сохранения файлов Excel
        val directoryPath = "/storage/emulated/0/BarcodeScanner/ExcelFiles"
        val directory = File(directoryPath)

        // Создание директории, если она не существует
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Полный путь к файлу Excel
        val filePath = "${directoryPath}/${fileName}_${current_dates}.xls"
        val file = File(filePath)

        // Проверка, существует ли файл Excel
        if (!file.exists()) {
            // Создание нового Excel файла, если он не существует
            val workbook = HSSFWorkbook()
            val sheet = workbook.createSheet("Scanned Data")

            val headerRow: Row = sheet.createRow(0)
            headerRow.createCell(0).setCellValue(fileName) // Имя файла
            headerRow.createCell(1)
                .setCellValue(current_dates) // Текущая дата
            val dataRow: Row = sheet.createRow(1)
            dataRow.createCell(0).setCellValue(scannedValue) // Значение сканирования

            // Запись данных в файл
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()
        } else {
            // Если файл существует, добавляем новое значение сканирования
            FileInputStream(file).use { inputStream ->
                val workbook = HSSFWorkbook(inputStream)
                val sheet = workbook.getSheetAt(0)

                // Находим следующую пустую строку
                val lastRowNum = sheet.lastRowNum + 1
                val newRow: Row = sheet.createRow(lastRowNum)
                newRow.createCell(0)
                    .setCellValue(scannedValue) // Значение сканирования в третьей колонке

                // Запись обновленных данных обратно в файл
                FileOutputStream(file).use { outputStream ->
                    workbook.write(outputStream)
                }
                workbook.close()
            }
        }
    }

    private class RoiView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }

            val roiLeft = width / 4
            val roiTop = height / 3
            val roiWidth = width / 2
            val roiHeight = height / 3

            canvas.drawRect(
                roiLeft.toFloat(),
                roiTop.toFloat(),
                (roiLeft + roiWidth).toFloat(),
                (roiTop + roiHeight).toFloat(),
                paint
            )
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 200
    }
}