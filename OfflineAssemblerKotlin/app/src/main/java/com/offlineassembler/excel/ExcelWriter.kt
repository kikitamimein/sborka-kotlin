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
    private val outputDirUri: Uri
) {
    
    fun generateFinalFile(): String {
        val workbook = XSSFWorkbook()
        
        // Define styles
        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 12
            setFont(font)
        }
        
        val subHeaderStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        }
        
        val yellowStyle = workbook.createCellStyle().apply {
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.YELLOW.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        }
        
        // Group items by Source Name
        val itemsBySource = collectedItems.groupBy { it.sourceName.ifEmpty { "Сборка" } }
        
        itemsBySource.forEach { (sourceName, items) ->
            val safeSheetName = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(sourceName)
            var uniqueSheetName = safeSheetName
            var suffix = 1
            while (workbook.getSheet(uniqueSheetName) != null) {
                uniqueSheetName = "$safeSheetName ($suffix)"
                suffix++
            }
            val sheet = workbook.createSheet(uniqueSheetName)
            
            // 1. Shipment Info at the very top (Row 0)
            if (shipmentInfo.isNotEmpty()) {
                val infoRow = sheet.createRow(0)
                infoRow.createCell(0).apply {
                    setCellValue("ПОСТАВКА: $shipmentInfo")
                    setCellStyle(headerStyle)
                }
            }
            
            val dataStartRow = 2
            
            // 2. "Uncollected" block (Column 0) - now includes partial collections
            val uncollected = items.filter { 
                it.status == ItemStatus.SKIPPED || 
                it.status == ItemStatus.PENDING || 
                (it.status == ItemStatus.COLLECTED && it.collectedQuantity < it.quantity) ||
                (it.status == ItemStatus.QUANTITY_CHANGED && it.collectedQuantity < it.quantity)
            }
            
            var unRowNum = dataStartRow
            val uncollectedHeaderRow = sheet.getRow(unRowNum) ?: sheet.createRow(unRowNum)
            uncollectedHeaderRow.createCell(0).apply {
                setCellValue("не найдено")
                setCellStyle(headerStyle)
            }
            unRowNum++
            
            val uncollectedSubHeaderRow = sheet.getRow(unRowNum) ?: sheet.createRow(unRowNum)
            uncollectedSubHeaderRow.createCell(0).apply {
                setCellValue("штрихкод")
                setCellStyle(subHeaderStyle)
            }
            unRowNum++
            
            uncollected.forEach { item ->
                val row = sheet.getRow(unRowNum) ?: sheet.createRow(unRowNum)
                val missingQty = item.quantity - item.collectedQuantity
                val cellValue = if (item.collectedQuantity > 0) {
                    "${item.barcode.ifEmpty { item.article }} (не добор $missingQty шт.)"
                } else {
                    item.barcode.ifEmpty { item.article }
                }
                row.createCell(0).setCellValue(cellValue)
                unRowNum++
            }
            
            // 3. Boxes side-by-side
            val boxes = items.map { it.box }.filter { it > 0 }.distinct().sorted()
            
            boxes.forEachIndexed { boxIdx, boxNum ->
                val startCol = 2 + boxIdx * 4 // Gap of 1 column between boxes
                var boxRowNum = dataStartRow
                
                val boxItems = items.filter { it.box == boxNum && (it.status == ItemStatus.COLLECTED || it.status == ItemStatus.QUANTITY_CHANGED) }
                
                if (boxItems.isNotEmpty()) {
                    // Box Header
                    val headerRow = sheet.getRow(boxRowNum) ?: sheet.createRow(boxRowNum)
                    headerRow.createCell(startCol).apply {
                        setCellValue("коробка $boxNum")
                        setCellStyle(headerStyle)
                    }
                    boxRowNum++
                    
                    // Sub Headers
                    val subHeaderRow = sheet.getRow(boxRowNum) ?: sheet.createRow(boxRowNum)
                    subHeaderRow.createCell(startCol).apply { setCellValue("кол-во"); setCellStyle(subHeaderStyle) }
                    subHeaderRow.createCell(startCol + 1).apply { setCellValue("артикул"); setCellStyle(subHeaderStyle) }
                    subHeaderRow.createCell(startCol + 2).apply { setCellValue("штрихкод"); setCellStyle(subHeaderStyle) }
                    boxRowNum++
                    
                    // Box Items
                    boxItems.forEach { item ->
                        val row = sheet.getRow(boxRowNum) ?: sheet.createRow(boxRowNum)
                        
                        // Qty Cell
                        val qtyCell = row.createCell(startCol)
                        if (item.collectedQuantity != item.quantity) {
                            qtyCell.setCellValue("${item.collectedQuantity}/${item.quantity}")
                            qtyCell.setCellStyle(yellowStyle)
                        } else {
                            qtyCell.setCellValue(item.collectedQuantity.toDouble())
                        }
                        
                        row.createCell(startCol + 1).setCellValue(item.article)
                        row.createCell(startCol + 2).setCellValue(item.barcode)
                        
                        boxRowNum++
                    }
                }
                
                // Set column widths for this box
                sheet.setColumnWidth(startCol, 12 * 256)
                sheet.setColumnWidth(startCol + 1, 20 * 256)
                sheet.setColumnWidth(startCol + 2, 20 * 256)
                sheet.setColumnWidth(startCol + 3, 2 * 256) // Separator
            }
            
            sheet.setColumnWidth(0, 25 * 256) // Uncollected col
            sheet.setColumnWidth(1, 2 * 256)  // Separator
        }
        
        // Finalize and save
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val fileName = "Сборка_$timestamp.xlsx"
        
        val dir = DocumentFile.fromTreeUri(context, outputDirUri) ?: throw Exception("Папка не найдена")
        val file = dir.createFile("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileName)
            ?: throw Exception("Не удалось создать файл")
            
        context.contentResolver.openOutputStream(file.uri)?.use { os ->
            workbook.write(os)
        }
        workbook.close()
        
        return file.uri.toString()
    }
}
