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
        binding.scannerButton.setOnClickListener { launchScanner() }
    }
    
    private fun launchScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        startActivity(intent)
    }
    
    private fun updateDisplay() {
        val s = session ?: return
        
        // Find next item that needs collection (PENDING or partially COLLECTED)
        while (s.currentIndex < s.items.size) {
            val item = s.items[s.currentIndex]
            val isDone = item.status == ItemStatus.COLLECTED || 
                         (item.status == ItemStatus.QUANTITY_CHANGED && item.collectedQuantity >= item.quantity) ||
                         item.status == ItemStatus.SKIPPED
            if (isDone) {
                s.currentIndex++
            } else {
                break
            }
        }
        
        if (s.currentIndex >= s.items.size) {
            finishAssembly()
            return
        }
        
        // Identify the group of items with the same barcode
        val currentItem = s.items[s.currentIndex]
        val currentBarcode = currentItem.barcode
        val group = s.items.drop(s.currentIndex).takeWhile { it.barcode == currentBarcode }
        
        binding.nameText.text = currentItem.name
        binding.locationText.text = currentItem.location.ifEmpty { "---" }
        
        // Show last 4 digits of barcode
        val barcodeLast4 = if (currentItem.barcode.length >= 4) 
            currentItem.barcode.takeLast(4) 
        else 
            currentItem.barcode
        binding.barcodeText.text = barcodeLast4.ifEmpty { "----" }

        val totalUnits = s.items.sumOf { it.quantity }
        val collectedUnits = s.items.sumOf { it.collectedQuantity }
        binding.progressText.text = "$collectedUnits / $totalUnits шт."
        
        binding.itemsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        
        // If multi-sheet, add a header row for the group
        if (!s.isSingleSheet) {
            val headerView = inflater.inflate(R.layout.item_parallel_header, binding.itemsContainer, false)
            binding.itemsContainer.addView(headerView)
        }

        group.forEachIndexed { index, item ->
            val layoutRes = if (s.isSingleSheet) android.R.layout.simple_list_item_1 else R.layout.item_parallel_row
            val itemView = inflater.inflate(layoutRes, binding.itemsContainer, false)
            
            val remaining = item.quantity - item.collectedQuantity

            if (s.isSingleSheet) {
                val title = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
                title.text = "$remaining шт."
                title.textSize = 48f
                title.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                title.setTypeface(null, android.graphics.Typeface.BOLD)
                title.setTextColor(resources.getColor(R.color.success, theme))
            } else {
                val sourceText = itemView.findViewById<android.widget.TextView>(R.id.sourceText)
                val boxText = itemView.findViewById<android.widget.TextView>(R.id.boxText)
                val qtyText = itemView.findViewById<android.widget.TextView>(R.id.qtyText)
                
                sourceText.text = if (item.status != ItemStatus.PENDING && remaining == 0) "✓ ${item.sourceName}" else item.sourceName
                boxText.text = s.sheetBoxCounters.getOrPut(item.sourceName) { 1 }.toString()
                qtyText.text = "$remaining шт."
                
                if (remaining == 0) {
                    sourceText.setTextColor(resources.getColor(R.color.success, theme))
                    qtyText.setTextColor(resources.getColor(R.color.success, theme))
                }
            }
            
            itemView.setOnClickListener {
                showItemActions(s.currentIndex + index)
            }
            
            binding.itemsContainer.addView(itemView)
        }
        
        // Update main box text
        binding.boxText.text = if (s.isSingleSheet) {
            "Коробка №${s.sheetBoxCounters.getOrPut(currentItem.sourceName) { 1 }}"
        } else {
            if (group.size > 1) "Параллельная сборка" else "Коробка №${s.sheetBoxCounters.getOrPut(currentItem.sourceName) { 1 }}"
        }
        
        sessionManager.saveSession(s)
    }
    
    private fun onCollect() {
        val s = session ?: return
        if (s.currentIndex >= s.items.size) return
        
        val currentBarcode = s.items[s.currentIndex].barcode
        
        // Find FIRST pending item in this barcode group
        val itemToCollect = s.items.getOrNull(s.currentIndex)
        
        if (itemToCollect != null && itemToCollect.status == ItemStatus.PENDING) {
            itemToCollect.status = ItemStatus.COLLECTED
            itemToCollect.collectedQuantity = itemToCollect.quantity
            itemToCollect.box = s.sheetBoxCounters.getOrPut(itemToCollect.sourceName) { 1 }
            
            // Check if all items in this group are now done. 
            // If yes, currentIndex will move forward automatically in updateDisplay() 
            // when it skips non-pending items.
        }
        
        sessionManager.saveSession(s)
        updateDisplay()
    }
    
    private fun showItemActions(itemIndex: Int) {
         val s = session ?: return
         if (itemIndex >= s.items.size) return
         
         val item = s.items[itemIndex]
         
         showBottomSheet("Действия: ${item.sourceName}", listOf(
            MenuItem("Изменить количество", android.R.drawable.ic_menu_edit, R.color.accent) { editItemQuantity(itemIndex) },
            MenuItem("Пропустить позицию", android.R.drawable.ic_delete, R.color.error) { skipItem(itemIndex) },
            MenuItem("След. коробка для ${item.sourceName}", android.R.drawable.ic_input_add, R.color.primary) { incrementBoxForSheet(item.sourceName) }
        ))
    }
    
    private fun skipItem(index: Int) {
        val s = session ?: return
        val item = s.items[index]
        
        item.status = ItemStatus.SKIPPED
        item.collectedQuantity = 0
        item.box = 0
        
        sessionManager.saveSession(s)
        updateDisplay()
    }

    private fun editItemQuantity(index: Int) {
        val s = session ?: return
        val item = s.items[index]
        val currentBox = s.sheetBoxCounters.getOrPut(item.sourceName) { 1 }
        
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val quantityInput = EditText(this).apply {
            hint = "Количество"
            setText(item.quantity.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val boxInput = EditText(this).apply {
            hint = "Номер коробки"
            setText(currentBox.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(quantityInput)
            addView(boxInput)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Изменить: ${item.sourceName}")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                try {
                    val newQty = quantityInput.text.toString().toInt()
                    val newBox = boxInput.text.toString().toInt()
                    
                    if (newQty >= 0 && newBox >= 1) {
                        item.status = ItemStatus.QUANTITY_CHANGED
                        item.collectedQuantity = newQty
                        item.box = newBox
                        s.sheetBoxCounters[item.sourceName] = newBox // Update global counter for this sheet
                        
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
    
    private fun incrementBoxForSheet(sheetName: String) {
        val s = session ?: return
        val current = s.sheetBoxCounters.getOrPut(sheetName) { 1 }
        s.sheetBoxCounters[sheetName] = current + 1
        sessionManager.saveSession(s)
        updateDisplay()
        Toast.makeText(this, "Коробка для $sheetName: ${current + 1}", Toast.LENGTH_SHORT).show()
    }
    
    private fun onNextBox() {
        val s = session ?: return
        val currentItem = s.items.getOrNull(s.currentIndex) ?: return
        
        // Use the source of the current item
        incrementBoxForSheet(currentItem.sourceName)
    }
    
    private fun saveSession() {
        session?.let {
            sessionManager.saveSession(it)
            Toast.makeText(this, "Сессия сохранена", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showReviewList() {
        val intent = Intent(this, ReviewActivity::class.java)
        startActivity(intent)
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
            if (s.outputDirUri.isEmpty()) {
                Toast.makeText(this, "Папка сохранения не выбрана", Toast.LENGTH_LONG).show()
                return
            }
            
            val outputUri = Uri.parse(s.outputDirUri)
            
            val writer = ExcelWriter(
                context = this,
                collectedItems = s.items,
                shipmentInfo = s.shipmentInfo,
                outputDirUri = outputUri
            )
            
            val outputFileUriString = writer.generateFinalFile()
            
            if (finish) {
                sessionManager.clearSession()
                showCompletionDialog(outputFileUriString, discrepancies)
            } else {
                Toast.makeText(this, "Файл сохранен", Toast.LENGTH_LONG).show()
                // Reset pending items if we were just generating intermediate
                if (markUncollected) {
                    s.items.filter { it.status == ItemStatus.SKIPPED && it.collectedQuantity == 0 }.forEach {
                        it.status = ItemStatus.PENDING
                    }
                    sessionManager.saveSession(s)
                }
            }
            
        } catch (e: Throwable) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload session to get changes from ReviewActivity
        val loadedSession = sessionManager.loadSession()
        if (loadedSession != null) {
            session = loadedSession
            // Update display but don't save immediately to avoid overwriting if something is wrong
            // updateDisplay() calls saveSession(), which is fine as we just loaded the latest state
            updateDisplay()
        } else if (!isFinishing) {
            // If session is missing and we are not finishing, something is wrong
            Toast.makeText(this, "Ошибка: сессия не найдена", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCompletionDialog(fileUriString: String, discrepancies: List<String>) {
        val intent = Intent(this, CompletionActivity::class.java).apply {
            putExtra(CompletionActivity.EXTRA_FILE_URI, fileUriString)
            putStringArrayListExtra(CompletionActivity.EXTRA_DISCREPANCIES, ArrayList(discrepancies))
        }
        startActivity(intent)
        finish()
    }

    private fun showTopMenu() {
        val items = listOf(
            MenuItem("Главное меню", android.R.drawable.ic_menu_revert, R.color.text_primary) { 
               finish()
            },
            MenuItem("Список позиций", android.R.drawable.ic_menu_view, R.color.primary) { showReviewList() }
        )
        showBottomSheet("Меню", items)
    }

    private fun showActionsMenu() {
        val items = listOf(
            MenuItem("Промежуточный файл", android.R.drawable.ic_menu_save, R.color.primary) { confirmGenerateIntermediate() },
            MenuItem("Завершить досрочно", android.R.drawable.ic_menu_close_clear_cancel, R.color.error) { confirmFinishEarly() }
        )
        showBottomSheet("Действия с файлом", items)
    }

    private fun showBottomSheet(title: String, items: List<MenuItem>) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.layout_bottom_sheet_list, null)
        
        val titleView = view.findViewById<android.widget.TextView>(R.id.sheetTitle)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.itemsContainer)
        
        titleView.text = title
        
        items.forEach { item ->
            val itemView = android.widget.TextView(this).apply {
                text = item.text
                textSize = 18f
                setPadding(48, 32, 48, 32)
                setTextColor(resources.getColor(R.color.text_primary, theme))
                
                val icon = resources.getDrawable(item.iconRes, theme).mutate()
                icon.setTint(resources.getColor(item.colorRes, theme))
                icon.setBounds(0, 0, 72, 72)
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = 32
                
                setOnClickListener {
                    dialog.dismiss()
                    item.action()
                }
            }
            container.addView(itemView)
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private data class MenuItem(
        val text: String,
        val iconRes: Int,
        val colorRes: Int,
        val action: () -> Unit
    )
}
