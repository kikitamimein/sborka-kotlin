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
        
        // Group items by Source Name
        val itemsBySource = collectedItems.groupBy { it.sourceName.ifEmpty { "Сборка" } }
        
        // Create a summary/discrepancies sheet if there are discrepancies
        if (discrepancies.isNotEmpty()) {
            val summarySheet = workbook.createSheet("Отчет")
            var rowNum = 0
            
            val headerRow = summarySheet.createRow(rowNum++)
            headerRow.createCell(0).setCellValue("Расхождения:")
            
            discrepancies.forEach { discrepancy ->
                val row = summarySheet.createRow(rowNum++)
                row.createCell(0).setCellValue(discrepancy)
            }
            summarySheet.setColumnWidth(0, 50 * 256)
        }
        
        // Create a sheet for each source (Order)
        itemsBySource.forEach { (sourceName, items) ->
            // Excel sheet names must be valid and unique. 
            // sourceName comes from input Excel, so usually valid, but safe to sanitize?
            // POI handles some, but let's just use it.
            val safeSheetName = try {
                 org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(sourceName)
            } catch (e: Exception) {
                "Order_${sourceName.hashCode()}"
            }
            
            // If sheet with this name exists (e.g. from Summary), append suffix
            var uniqueSheetName = safeSheetName
            var suffix = 1
            while (workbook.getSheet(uniqueSheetName) != null) {
                uniqueSheetName = "$safeSheetName ($suffix)"
                suffix++
            }
            
            val sheet = workbook.createSheet(uniqueSheetName)
            var rowNum = 0
            
            // Shipment Info (only on first sheet or all? Let's put on all if relevant, or just first)
            // The original logic bad shipment info. Let's just put it at top of every sheet if existing
            if (shipmentInfo.isNotEmpty()) {
                val headerRow = sheet.createRow(rowNum++)
                headerRow.createCell(0).setCellValue("Информация о поставке:")
                
                val infoRow = sheet.createRow(rowNum++)
                infoRow.createCell(0).setCellValue(shipmentInfo)
                rowNum++ 
            }
            
            // Find all unique box numbers for THIS source
            val boxes = items.map { it.box }.filter { it > 0 }.distinct().sorted()
            
            boxes.forEach { boxNum ->
                val boxItems = items.filter { it.box == boxNum && it.status in listOf(ItemStatus.COLLECTED, ItemStatus.QUANTITY_CHANGED) }
                
                if (boxItems.isNotEmpty()) {
                    // Box Header
                    val boxHeaderRow = sheet.createRow(rowNum++)
                    val cell = boxHeaderRow.createCell(0)
                    cell.setCellValue("Коробка № $boxNum")
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
            
            // Set column widths
            sheet.setColumnWidth(0, 15 * 256)
            sheet.setColumnWidth(1, 25 * 256)
            sheet.setColumnWidth(2, 25 * 256)
        }
        
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
