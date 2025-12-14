package com.offlineassembler.excel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.offlineassembler.model.AssemblyItem
import com.offlineassembler.model.ItemStatus
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.*

class ExcelWriter(
    private val context: Context,
    private val collectedItems: List<AssemblyItem>,
    private val shipmentInfo: String,
    private val discrepancies: List<String>,
    private val outputDirUri: Uri
) {
    
    fun generateFinalFile(): String {
        val workbook = XSSFWorkbook()
        
        // Create main sheet
        val sheet = workbook.createSheet("Сборка")
        
        // Header row
        val headerRow = sheet.createRow(0)
        val headers = listOf("Коробка", "Артикул", "Наименование", "Количество", "Штрихкод")
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }
        
        // Data rows
        var rowNum = 1
        collectedItems.filter { it.status in listOf(ItemStatus.COLLECTED, ItemStatus.QUANTITY_CHANGED) && it.collectedQuantity > 0 }
            .forEach { item ->
                val row = sheet.createRow(rowNum++)
                row.createCell(0).setCellValue(item.box.toDouble())
                row.createCell(1).setCellValue(item.article)
                row.createCell(2).setCellValue(item.name)
                row.createCell(3).setCellValue(item.collectedQuantity.toDouble())
                row.createCell(4).setCellValue(item.barcode)
            }
        
        // Add shipment info
        if (shipmentInfo.isNotEmpty()) {
            rowNum++
            val infoRow = sheet.createRow(rowNum++)
            infoRow.createCell(0).setCellValue("Информация о поставке:")
            val infoDataRow = sheet.createRow(rowNum++)
            infoDataRow.createCell(0).setCellValue(shipmentInfo)
        }
        
        // Add discrepancies
        if (discrepancies.isNotEmpty()) {
            rowNum++
            val discrepancyHeaderRow = sheet.createRow(rowNum++)
            discrepancyHeaderRow.createCell(0).setCellValue("Расхождения:")
            
            discrepancies.forEach { discrepancy ->
                val discrepancyRow = sheet.createRow(rowNum++)
                discrepancyRow.createCell(0).setCellValue(discrepancy)
            }
        }
        
        // Auto-size columns
        for (i in 0 until 5) {
            sheet.autoSizeColumn(i)
        }
        
        // Generate filename
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val fileName = "Сборка_$timestamp.xlsx"
        
        val dir = DocumentFile.fromTreeUri(context, outputDirUri)
        if (dir == null || !dir.canWrite()) {
            throw Exception("Невозможно записать в выбранную папку")
        }
        
        val file = dir.createFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileName)
            ?: throw Exception("Не удалось создать файл")
            
        context.contentResolver.openOutputStream(file.uri)?.use { os ->
            workbook.write(os)
        }
        workbook.close()
        
        return file.uri.toString()
    }
}
