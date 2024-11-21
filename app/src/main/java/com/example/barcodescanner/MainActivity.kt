package com.example.barcodescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var fileNameSpinner: Spinner
    private lateinit var startButton: Button

    // Переменная для обработки результатов запроса разрешений
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true) {
            // Все разрешения предоставлены, загружаем имена файлов
            loadFileNames()
        } else {
            // Разрешения не были предоставлены, показываем сообщение пользователю
            Toast.makeText(this, "Необходимы разрешения для работы приложения", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fileNameSpinner = findViewById(R.id.fileNameSpinner)
        startButton = findViewById(R.id.startButton)

        // Проверка и запрос разрешений при создании активности
        checkAndRequestPermissions()

        startButton.setOnClickListener {
            val selectedFileName = fileNameSpinner.selectedItem.toString()
            val intent = Intent(this, ScannerActivity::class.java)
            intent.putExtra("FILE_NAME", selectedFileName)
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writeStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (cameraPermission != PackageManager.PERMISSION_GRANTED ||
            readStoragePermission != PackageManager.PERMISSION_GRANTED ||
            writeStoragePermission != PackageManager.PERMISSION_GRANTED) {
            // Запрашиваем разрешения
            requestPermissionsLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        } else {
            // Разрешения уже предоставлены, загружаем имена файлов сразу
            loadFileNames()
        }
    }

    private fun loadFileNames() {
        val directoryPath = "/storage/emulated/0/BarcodeScanner/ProgramFiles"
        val filePath = "$directoryPath/names.txt"
        val names = mutableListOf<String>()

        try {
            val directory = File(directoryPath)
            if (!directory.exists()) {
                directory.mkdirs() // Создание директории, если она не существует
            }
            val file = File(filePath)
            if (file.exists()) {
                names.addAll(file.readLines())
            } else {
                // Уведомление о том, что файл не найден
                showNotification("Файл не найден, создан временный файл.")

                // Создание временного файла и заполнение его значениями
                file.writeText("file_1\nfile_2")

                // Чтение данных из временного файла
                names.addAll(file.readLines())
            }

            // Обновление адаптера для Spinner после загрузки имен файлов
            updateSpinner(names)

        } catch (e: Exception) {
            Log.e("FileError", "Ошибка чтения файла: ${e.message}")
            showNotification("Ошибка при загрузке имен файлов: ${e.message}")
        }
    }

    private fun updateSpinner(names: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fileNameSpinner.adapter = adapter
    }

    private fun showNotification(message: String) {
        // Отображение Toast-сообщения
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}