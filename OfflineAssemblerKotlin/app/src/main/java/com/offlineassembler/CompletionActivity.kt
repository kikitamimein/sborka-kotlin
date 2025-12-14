package com.offlineassembler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.offlineassembler.databinding.ActivityCompletionBinding

class CompletionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCompletionBinding
    
    companion object {
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_DISCREPANCIES = "discrepancies"
    }
    
    private var fileUriString: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
        val discrepancies = intent.getStringArrayListExtra(EXTRA_DISCREPANCIES) ?: arrayListOf()
        
        setupUI(discrepancies)
    }
    
    private fun setupUI(discrepancies: List<String>) {
        binding.filePathText.text = "Файл сохранен"
        
        binding.shareButton.setOnClickListener { shareFile() }
        binding.showLocationButton.setOnClickListener { openFile() }
        binding.showLocationButton.text = "Открыть файл"
        binding.showLocationButton.setIconResource(android.R.drawable.ic_menu_view)
        
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
        if (fileUriString.isEmpty()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val uri = Uri.parse(fileUriString)
            
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
    
    private fun openFile() {
        if (fileUriString.isEmpty()) return
        
        try {
            val uri = Uri.parse(fileUriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть файл: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
