package com.offlineassembler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.offlineassembler.databinding.ActivityCompletionBinding
import java.io.File

class CompletionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCompletionBinding
    
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_DISCREPANCIES = "discrepancies"
    }
    
    private var filePath: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        val discrepancies = intent.getStringArrayListExtra(EXTRA_DISCREPANCIES) ?: arrayListOf()
        
        setupUI(discrepancies)
    }
    
    private fun setupUI(discrepancies: List<String>) {
        binding.filePathText.text = "Файл сохранен:\n$filePath"
        
        binding.shareButton.setOnClickListener { shareFile() }
        binding.showLocationButton.setOnClickListener { showLocation() }
        binding.homeButton.setOnClickListener { goHome() }
        
        // Show discrepancies
        if (discrepancies.isNotEmpty()) {
            val headerText = android.widget.TextView(this).apply {
                text = "Расхождения:"
                textSize = 16f
                setTextColor(resources.getColor(R.color.error, theme))
                setPadding(0, 0, 0, 16)
            }
            binding.discrepanciesContainer.addView(headerText)
            
            discrepancies.forEach { discrepancy ->
                val textView = android.widget.TextView(this).apply {
                    text = "• $discrepancy"
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.text_secondary, theme))
                    setPadding(0, 4, 0, 4)
                }
                binding.discrepanciesContainer.addView(textView)
            }
        }
    }
    
    private fun shareFile() {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Поделиться файлом"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showLocation() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("path", filePath))
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Расположение файла")
            .setMessage(filePath)
            .setPositiveButton("Скопировано!") { d, _ -> d.dismiss() }
            .show()
    }
    
    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onBackPressed() {
        goHome()
    }
}
