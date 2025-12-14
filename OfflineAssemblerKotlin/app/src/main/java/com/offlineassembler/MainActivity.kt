package com.offlineassembler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.offlineassembler.data.PrefsManager
import com.offlineassembler.data.SessionManager
import com.offlineassembler.databinding.ActivityMainBinding
import com.offlineassembler.excel.ExcelProcessor
import com.offlineassembler.model.AssemblySession

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var adapter: FileListAdapter
    
    private val inputFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        handleFolderSelection(uri, isInput = true)
    }

    private val outputFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        handleFolderSelection(uri, isInput = false)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sessionManager = SessionManager(this)
        prefsManager = PrefsManager(this)
        
        setupUI()
        handleIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        refreshFileList()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun setupUI() {
        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }
        
        binding.selectFolderButton.setOnClickListener {
            inputFolderLauncher.launch(null)
        }
        
        binding.continueSessionButton.setOnClickListener {
            startAssembly()
        }
        
        adapter = FileListAdapter { uri ->
            processExcelFile(uri)
        }
        
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter
        
        // Check for saved session
        if (sessionManager.hasSession()) {
            binding.continueSessionButton.visibility = View.VISIBLE
            showResumeDialog()
        }
    }
    
    private fun refreshFileList() {
        val inputUriString = prefsManager.inputFolderUri
        
        if (inputUriString == null) {
            binding.fileList.visibility = View.GONE
            binding.emptyView.visibility = View.GONE
            binding.selectFolderButton.visibility = View.VISIBLE
            binding.subtitleText.text = "Выберите рабочую папку"
            return
        }
        
        binding.selectFolderButton.visibility = View.GONE
        binding.fileList.visibility = View.VISIBLE
        binding.subtitleText.text = "Выберите файл для начала"
        
        try {
            val inputUri = Uri.parse(inputUriString)
            val dir = DocumentFile.fromTreeUri(this, inputUri)
            
            if (dir == null || !dir.canRead()) {
                // Permission lost or folder deleted
                prefsManager.inputFolderUri = null
                refreshFileList()
                return
            }
            
            val files = dir.listFiles()
                .filter { it.name?.endsWith(".xlsx", ignoreCase = true) == true || it.name?.endsWith(".xls", ignoreCase = true) == true }
                .sortedByDescending { it.lastModified() }
                
            if (files.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.fileList.visibility = View.GONE
                binding.emptyView.text = "В папке нет Excel файлов"
            } else {
                binding.emptyView.visibility = View.GONE
                binding.fileList.visibility = View.VISIBLE
                adapter.submitList(files)
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка доступа к папке: ${e.message}", Toast.LENGTH_SHORT).show()
            prefsManager.inputFolderUri = null
            refreshFileList()
        }
    }
    
    private fun handleFolderSelection(uri: Uri?, isInput: Boolean) {
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                
                if (isInput) {
                    prefsManager.inputFolderUri = uri.toString()
                    refreshFileList()
                } else {
                    prefsManager.outputFolderUri = uri.toString()
                    Toast.makeText(this, "Папка для сохранения выбрана", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка доступа: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showSettingsDialog() {
        val options = arrayOf("Выбрать папку с файлами (Вход)", "Выбрать папку для сохранения (Выход)")
        
        AlertDialog.Builder(this)
            .setTitle("Настройки папок")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> inputFolderLauncher.launch(null)
                    1 -> outputFolderLauncher.launch(null)
                }
            }
            .setPositiveButton("Закрыть", null)
            .show()
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
            
            // Set output directory from prefs if available
            prefsManager.outputFolderUri?.let {
                session.outputDirUri = it
            }
            
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
        if (session != null) {
            if (session.outputDirUri.isEmpty()) {
                // Try to get from prefs first
                if (prefsManager.outputFolderUri != null) {
                    session.outputDirUri = prefsManager.outputFolderUri!!
                    sessionManager.saveSession(session)
                    launchAssemblyActivity()
                } else {
                    showOutputFolderSelectionDialog()
                }
            } else {
                launchAssemblyActivity()
            }
        } else {
            sessionManager.clearSession()
            binding.continueSessionButton.visibility = View.GONE
            Toast.makeText(this, "Ошибка: сессия не найдена", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showOutputFolderSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выберите папку для сохранения")
            .setMessage("Необходимо выбрать папку, куда будет сохранен итоговый файл сборки.")
            .setPositiveButton("Выбрать") { _, _ ->
                outputFolderLauncher.launch(null)
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(false)
            .show()
    }
    
    private fun launchAssemblyActivity() {
        val intent = Intent(this, AssemblyActivity::class.java)
        startActivity(intent)
    }
    
    // Adapter
    inner class FileListAdapter(private val onItemClick: (Uri) -> Unit) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {
        
        private var files: List<DocumentFile> = emptyList()
        
        fun submitList(newFiles: List<DocumentFile>) {
            files = newFiles
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileName: TextView = view.findViewById(R.id.fileName)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.fileName.text = file.name
            holder.itemView.setOnClickListener {
                onItemClick(file.uri)
            }
        }
        
        override fun getItemCount() = files.size
    }
}
