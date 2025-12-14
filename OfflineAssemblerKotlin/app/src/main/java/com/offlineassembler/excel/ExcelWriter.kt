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
        val sheet = workbook.createSheet("Сборка")
        var rowNum = 0
        
        // 1. Discrepancies at the top
        if (discrepancies.isNotEmpty()) {
            val headerRow = sheet.createRow(rowNum++)
            headerRow.createCell(0).setCellValue("Расхождения:")
            
            discrepancies.forEach { discrepancy ->
                val row = sheet.createRow(rowNum++)
                row.createCell(0).setCellValue(discrepancy)
            }
            rowNum++ // Spacing
        }
        
        // 2. Shipment Info
        if (shipmentInfo.isNotEmpty()) {
            val headerRow = sheet.createRow(rowNum++)
            headerRow.createCell(0).setCellValue("Информация о поставке:")
            
            val infoRow = sheet.createRow(rowNum++)
            infoRow.createCell(0).setCellValue(shipmentInfo)
            rowNum++ // Spacing
        }
        
        // 3. Boxes
        // Find all unique box numbers
        val boxes = collectedItems.map { it.box }.filter { it > 0 }.distinct().sorted()
        
        boxes.forEach { boxNum ->
            val boxItems = collectedItems.filter { it.box == boxNum && it.status in listOf(ItemStatus.COLLECTED, ItemStatus.QUANTITY_CHANGED) }
            
            if (boxItems.isNotEmpty()) {
                // Box Header
                val boxHeaderRow = sheet.createRow(rowNum++)
                val cell = boxHeaderRow.createCell(0)
                cell.setCellValue("Коробка № $boxNum")
                // Merge cells for box header (across 3 columns)
                sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(rowNum - 1, rowNum - 1, 0, 2))
                
                // Columns Header
                val colHeaderRow = sheet.createRow(rowNum++)
                colHeaderRow.createCell(0).setCellValue("Кол-во")
                colHeaderRow.createCell(1).setCellValue("Артикул")
                colHeaderRow.createCell(2).setCellValue("Штрихкод")
                
                // Items
                boxItems.forEach { item ->
                    val row = sheet.createRow(rowNum++)
                    row.createCell(0).setCellValue(item.collectedQuantity.toDouble())
                    row.createCell(1).setCellValue(item.article)
                    row.createCell(2).setCellValue(item.barcode)
                }
                
                rowNum++ // Spacing between boxes
            }
        }
        
        // Set column widths (fixed, no autoSizeColumn)
        sheet.setColumnWidth(0, 15 * 256) // Quantity
        sheet.setColumnWidth(1, 25 * 256) // Article
        sheet.setColumnWidth(2, 25 * 256) // Barcode
        
        // Generate filename
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val fileName = "Сборка_$timestamp.xlsx"
        
        val dir = DocumentFile.fromTreeUri(context, outputDirUri)
        if (dir == null) {
            throw Exception("Не удалось получить доступ к папке. Попробуйте выбрать её заново.")
        }
        if (!dir.canWrite()) {
            throw Exception("Нет прав на запись в папку. Попробуйте выбрать другую.")
        }
        
        val file = dir.createFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileName)
            ?: throw Exception("Не удалось создать файл в выбранной папке.")
            
        context.contentResolver.openOutputStream(file.uri)?.use { os ->
            workbook.write(os)
        }
        workbook.close()
        
        return file.uri.toString()
    }
}
