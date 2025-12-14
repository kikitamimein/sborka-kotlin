package com.offlineassembler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.offlineassembler.data.SessionManager
import com.offlineassembler.databinding.ActivityMainBinding
import com.offlineassembler.excel.ExcelProcessor
import com.offlineassembler.model.AssemblySession

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processExcelFile(it) }
    }
    
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Persist permissions
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                
                // Save to session and start assembly
                val session = sessionManager.loadSession()
                if (session != null) {
                    session.outputDirUri = uri.toString()
                    sessionManager.saveSession(session)
                    launchAssemblyActivity()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка доступа к папке: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Папка не выбрана", Toast.LENGTH_SHORT).show()
            // If it was a new session, maybe we should clear it? 
            // But user might want to try again.
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sessionManager = SessionManager(this)
        
        setupUI()
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun setupUI() {
        binding.openFileButton.setOnClickListener {
            // Support both xlsx and xls
            filePickerLauncher.launch("application/*")
        }
        
        binding.continueSessionButton.setOnClickListener {
            startAssembly()
        }
        
        // Check for saved session
        if (sessionManager.hasSession()) {
            binding.continueSessionButton.visibility = View.VISIBLE
            showResumeDialog()
        }
    }
    
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri != null) {
            processExcelFile(uri)
        }
    }
    
    private fun processExcelFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw Exception("Не удалось открыть файл")
            
            val processor = ExcelProcessor()
            val result = processor.processFile(inputStream)
            inputStream.close()
            
            if (result.items.isEmpty()) {
                Toast.makeText(this, "Файл пуст или имеет неверный формат", Toast.LENGTH_LONG).show()
                return
            }
            
            // Create session
            val session = AssemblySession(
                items = result.items.toMutableList(),
                shipmentInfo = result.shipmentInfo,
                inputFilePath = uri.toString()
            )
            
            sessionManager.saveSession(session)
            
            // Show confirmation with items count
            AlertDialog.Builder(this)
                .setTitle("Файл загружен")
                .setMessage("Найдено позиций: ${result.items.size}\n\nНачать сборку?")
                .setPositiveButton("Начать") { _, _ ->
                    startAssembly()
                }
                .setNegativeButton("Отмена") { _, _ ->
                    sessionManager.clearSession()
                }
                .show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showResumeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Восстановить сборку?")
            .setMessage("Найдена незавершенная сборка. Продолжить?")
            .setPositiveButton("Продолжить") { _, _ ->
                startAssembly()
            }
            .setNegativeButton("Начать новую") { _, _ ->
                sessionManager.clearSession()
                binding.continueSessionButton.visibility = View.GONE
            }
            .show()
    }
    
    private fun startAssembly() {
        val session = sessionManager.loadSession()
        if (session != null && session.outputDirUri.isEmpty()) {
            showFolderSelectionDialog()
        } else {
            launchAssemblyActivity()
        }
    }
    
    private fun showFolderSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выберите папку")
            .setMessage("Необходимо выбрать папку, куда будет сохранен итоговый файл сборки.")
            .setPositiveButton("Выбрать") { _, _ ->
                folderPickerLauncher.launch(null)
            }
            .setNegativeButton("Отмена") { _, _ ->
                // Do nothing, user stays on main screen
            }
            .setCancelable(false)
            .show()
    }
    
    private fun launchAssemblyActivity() {
        val intent = Intent(this, AssemblyActivity::class.java)
        startActivity(intent)
    }
}
