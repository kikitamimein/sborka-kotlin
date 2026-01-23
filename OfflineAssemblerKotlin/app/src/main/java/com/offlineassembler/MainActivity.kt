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
        // Old settings button hidden
        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }
        
        // New main settings button calls same dialog
        binding.mainSettingsButton.setOnClickListener {
            showSettingsDialog()
        }
        
        binding.continueSessionButton.setOnClickListener {
            startAssembly()
        }

        binding.allItemsButton.setOnClickListener {
            val intent = Intent(this, AllItemsActivity::class.java)
            startActivity(intent)
        }
        
        // Hide fileList related stuff as we want a cleaner main screen
        binding.fileList.visibility = View.GONE
        binding.subtitleText.visibility = View.GONE
        
        // Check for saved session
        if (sessionManager.hasSession()) {
            binding.continueSessionButton.visibility = View.VISIBLE
            showResumeDialog()
        }
    }
    
    private fun refreshFileList() {
        // selectFolderButton removed, nothing to toggle
        val inputUriString = prefsManager.inputFolderUri
        
        // Update subtitle to show selected folder info if any
        if (inputUriString != null) {
            val inputUri = Uri.parse(inputUriString)
            val dir = DocumentFile.fromTreeUri(this, inputUri)
            binding.titleText.text = "Сборщик"
            binding.subtitleText.visibility = View.VISIBLE
            binding.subtitleText.text = "Папка: ${dir?.name ?: "..."}"
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
    
    private fun getFolderName(uriString: String?): String {
        if (uriString.isNullOrEmpty()) return "нет"
        return try {
            val uri = Uri.parse(uriString)
            DocumentFile.fromTreeUri(this, uri)?.name ?: "нет"
        } catch (e: Exception) {
            "ошибка"
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            "Выбрать файл для сборки",
            "Выбрать папку Вход (${getFolderName(prefsManager.inputFolderUri)})",
            "Выбрать папку Выход (${getFolderName(prefsManager.outputFolderUri)})",
            "Настройки принтера (${prefsManager.printerIp}:${prefsManager.printerPort})"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFileSelectionDialog()
                    1 -> inputFolderLauncher.launch(null)
                    2 -> outputFolderLauncher.launch(null)
                    3 -> showPrinterSettingsDialog()
                }
            }
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showPrinterSettingsDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val ipInput = android.widget.EditText(this).apply {
            hint = "IP Адрес"
            setText(prefsManager.printerIp)
        }
        val portInput = android.widget.EditText(this).apply {
            hint = "Порт"
            setText(prefsManager.printerPort.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(ipInput)
        layout.addView(portInput)

        AlertDialog.Builder(this)
            .setTitle("Принтер")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                prefsManager.printerIp = ipInput.text.toString()
                prefsManager.printerPort = portInput.text.toString().toIntOrNull() ?: 9100
                Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFileSelectionDialog() {
        val inputUriString = prefsManager.inputFolderUri
        if (inputUriString == null) {
            Toast.makeText(this, "Сначала выберите папку Вход", Toast.LENGTH_SHORT).show()
            return
        }

        val inputUri = Uri.parse(inputUriString)
        val dir = DocumentFile.fromTreeUri(this, inputUri)
        val files = dir?.listFiles()
            ?.filter { it.name?.endsWith(".xlsx", ignoreCase = true) == true || it.name?.endsWith(".xls", ignoreCase = true) == true }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            Toast.makeText(this, "В папке нет Excel файлов", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name ?: "Unknown" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите файл")
            .setItems(fileNames) { _, which ->
                processExcelFile(files[which].uri)
            }
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
            
            // Natural sort for locations: splits strings into numeric and text parts
            fun splitLocation(loc: String): List<Any> {
                val result = mutableListOf<Any>()
                var i = 0
                while (i < loc.length) {
                    val start = i
                    if (loc[i].isDigit()) {
                        while (i < loc.length && loc[i].isDigit()) i++
                        result.add(loc.substring(start, i).toLong())
                    } else {
                        while (i < loc.length && !loc[i].isDigit()) i++
                        result.add(loc.substring(start, i))
                    }
                }
                return result
            }

            val sortedItems = result.items.sortedWith { item1, item2 ->
                val parts1 = splitLocation(item1.location.trim())
                val parts2 = splitLocation(item2.location.trim())
                
                var cmp = 0
                val size = minOf(parts1.size, parts2.size)
                for (i in 0 until size) {
                    val p1 = parts1[i]
                    val p2 = parts2[i]
                    
                    if (p1 is Long && p2 is Long) {
                        cmp = p1.compareTo(p2)
                    } else {
                        cmp = p1.toString().compareTo(p2.toString())
                    }
                    if (cmp != 0) break
                }
                
                if (cmp == 0) cmp = parts1.size.compareTo(parts2.size)
                
                if (cmp != 0) cmp else item1.barcode.compareTo(item2.barcode).let {
                    if (it != 0) it else item1.sourceName.compareTo(item2.sourceName)
                }
            }

            // Create session
            val session = AssemblySession(
                items = sortedItems.toMutableList(),
                shipmentInfo = result.shipmentInfo,
                inputFilePath = uri.toString(),
                isSingleSheet = result.isSingleSheet
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
