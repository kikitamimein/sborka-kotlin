package com.offlineassembler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.offlineassembler.data.SessionManager
import com.offlineassembler.databinding.ActivityAssemblyBinding
import com.offlineassembler.excel.ExcelWriter
import com.offlineassembler.model.AssemblyItem
import com.offlineassembler.model.AssemblySession
import com.offlineassembler.model.ItemStatus
import java.io.File

class AssemblyActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAssemblyBinding
    private lateinit var sessionManager: SessionManager
    private var session: AssemblySession? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssemblyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sessionManager = SessionManager(this)
        session = sessionManager.loadSession()
        
        if (session == null || session!!.items.isEmpty()) {
            Toast.makeText(this, "Нет данных для сборки", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupUI()
        updateDisplay()
    }
    
    private fun setupUI() {
        binding.collectButton.setOnClickListener { onCollect() }
        binding.menuButton.setOnClickListener { showTopMenu() }
        binding.actionsButton.setOnClickListener { showActionsMenu() }
        binding.saveButton.setOnClickListener { saveSession() }
        binding.listButton.setOnClickListener { showReviewList() }
    }
    
    private fun updateDisplay() {
        val s = session ?: return
        
        // Find next pending item
        while (s.currentIndex < s.items.size && s.items[s.currentIndex].status != ItemStatus.PENDING) {
            s.currentIndex++
        }
        
        if (s.currentIndex >= s.items.size) {
            finishAssembly()
            return
        }
        
        val item = s.items[s.currentIndex]
        
        binding.nameText.text = item.name
        binding.locationText.text = item.location.ifEmpty { "---" }
        
        // Show last 4 digits of barcode
        val barcodeLast4 = if (item.barcode.length >= 4) 
            item.barcode.takeLast(4) 
        else 
            item.barcode
        binding.barcodeText.text = barcodeLast4.ifEmpty { "----" }
        
        binding.quantityText.text = item.quantity.toString()
        binding.progressText.text = "${s.currentIndex + 1} / ${s.items.size}"
        binding.boxText.text = "Коробка №${s.currentBox}"
        
        sessionManager.saveSession(s)
    }
    
    private fun onCollect() {
        val s = session ?: return
        if (s.currentIndex >= s.items.size) return
        
        val item = s.items[s.currentIndex]
        item.status = ItemStatus.COLLECTED
        item.collectedQuantity = item.quantity
        item.box = s.currentBox
        
        s.currentIndex++
        sessionManager.saveSession(s)
        updateDisplay()
    }
    
    private fun showTopMenu() {
        AlertDialog.Builder(this)
            .setTitle("Дополнительные опции")
            .setItems(arrayOf("Сгенерировать промежуточный файл", "Завершить сборку досрочно")) { _, which ->
                when (which) {
                    0 -> confirmGenerateIntermediate()
                    1 -> confirmFinishEarly()
                }
            }
            .show()
    }
    
    private fun showActionsMenu() {
        AlertDialog.Builder(this)
            .setTitle("Действия")
            .setItems(arrayOf("Нет товара (Пропустить)", "Изменить количество", "Следующая коробка")) { _, which ->
                when (which) {
                    0 -> onSkip()
                    1 -> onChangeQuantity()
                    2 -> onNextBox()
                }
            }
            .show()
    }
    
    private fun onSkip() {
        val s = session ?: return
        if (s.currentIndex >= s.items.size) return
        
        val item = s.items[s.currentIndex]
        item.status = ItemStatus.SKIPPED
        item.collectedQuantity = 0
        item.box = 0
        
        s.currentIndex++
        sessionManager.saveSession(s)
        updateDisplay()
    }

    private fun onChangeQuantity() {
        val s = session ?: return
        if (s.currentIndex >= s.items.size) return
        
        val item = s.items[s.currentIndex]
        
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val quantityInput = EditText(this).apply {
            hint = "Количество"
            setText(item.quantity.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val boxInput = EditText(this).apply {
            hint = "Номер коробки"
            setText(s.currentBox.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(quantityInput)
            addView(boxInput)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Изменить количество")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                try {
                    val newQty = quantityInput.text.toString().toInt()
                    val newBox = boxInput.text.toString().toInt()
                    
                    if (newQty >= 0 && newBox >= 1) {
                        item.status = ItemStatus.QUANTITY_CHANGED
                        item.collectedQuantity = newQty
                        item.box = newBox
                        s.currentBox = newBox
                        s.currentIndex++
                        sessionManager.saveSession(s)
                        updateDisplay()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Введите корректные числа", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun onNextBox() {
        val s = session ?: return
        s.currentBox++
        sessionManager.saveSession(s)
        updateDisplay()
        Toast.makeText(this, "Начата коробка №${s.currentBox}", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveSession() {
        session?.let {
            sessionManager.saveSession(it)
            Toast.makeText(this, "Сессия сохранена", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showReviewList() {
        val s = session ?: return
        
        val items = s.items.mapIndexed { index, item ->
            val status = when (item.status) {
                ItemStatus.COLLECTED -> "✓"
                ItemStatus.SKIPPED -> "✗"
                ItemStatus.QUANTITY_CHANGED -> "~"
                else -> "○"
            }
            "$status ${item.location} | ${item.barcode.takeLast(4)} | ${item.collectedQuantity}/${item.quantity}"
        }
        
        AlertDialog.Builder(this)
            .setTitle("Обзор сборки")
            .setItems(items.toTypedArray()) { _, which ->
                // Could allow editing here
            }
            .setPositiveButton("Закрыть", null)
            .show()
    }
    
    private fun confirmGenerateIntermediate() {
        AlertDialog.Builder(this)
            .setTitle("Сгенерировать промежуточный файл?")
            .setMessage("Все необработанные позиции будут помечены как не собранные. Сборка продолжится.")
            .setPositiveButton("Да, сгенерировать") { _, _ ->
                generateExcelFile(markUncollected = true, finish = false)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun confirmFinishEarly() {
        AlertDialog.Builder(this)
            .setTitle("Завершить сборку досрочно?")
            .setMessage("Все необработанные позиции будут помечены как не собранные. Сборка завершится.")
            .setPositiveButton("Да, завершить") { _, _ ->
                generateExcelFile(markUncollected = true, finish = true)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun finishAssembly() {
        generateExcelFile(markUncollected = false, finish = true)
    }
    
    private fun generateExcelFile(markUncollected: Boolean, finish: Boolean) {
        val s = session ?: return
        
        // Mark pending items as skipped if requested
        if (markUncollected) {
            s.items.filter { it.status == ItemStatus.PENDING }.forEach {
                it.status = ItemStatus.SKIPPED
                it.collectedQuantity = 0
                it.box = 0
            }
        }
        
        // Generate discrepancies list
        val discrepancies = s.items.mapNotNull { item ->
            val identifier = item.barcode.ifEmpty { "Арт: ${item.article}" }
            when {
                item.status == ItemStatus.SKIPPED -> "Пропущено: $identifier - ${item.quantity} шт."
                item.status == ItemStatus.QUANTITY_CHANGED && item.collectedQuantity != item.quantity ->
                    "Изменено: $identifier было ${item.quantity}, стало ${item.collectedQuantity}"
                else -> null
            }
        }
        
        try {
            val outputDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
                ?: filesDir
            
            val writer = ExcelWriter(
                collectedItems = s.items,
                shipmentInfo = s.shipmentInfo,
                discrepancies = discrepancies,
                outputDirectory = outputDir
            )
            
            val outputFile = writer.generateFinalFile()
            
            if (finish) {
                sessionManager.clearSession()
                showCompletionDialog(outputFile, discrepancies)
            } else {
                Toast.makeText(this, "Файл сохранен: ${outputFile.name}", Toast.LENGTH_LONG).show()
                // Reset pending items if we were just generating intermediate
                if (markUncollected) {
                    s.items.filter { it.status == ItemStatus.SKIPPED && it.collectedQuantity == 0 }.forEach {
                        it.status = ItemStatus.PENDING
                    }
                    sessionManager.saveSession(s)
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showCompletionDialog(file: File, discrepancies: List<String>) {
        val message = buildString {
            append("Файл сохранен:\n${file.absolutePath}")
            if (discrepancies.isNotEmpty()) {
                append("\n\nРасхождения:\n")
                discrepancies.forEach { append("• $it\n") }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Сборка завершена!")
            .setMessage(message)
            .setPositiveButton("Поделиться") { _, _ ->
                shareFile(file)
            }
            .setNeutralButton("В главное меню") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun shareFile(file: File) {
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
            Toast.makeText(this, "Ошибка при попытке поделиться: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
