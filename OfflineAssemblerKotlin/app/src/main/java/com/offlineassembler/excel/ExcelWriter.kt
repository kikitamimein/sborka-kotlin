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
        
        // Group items by Source Name
        val itemsBySource = collectedItems.groupBy { it.sourceName.ifEmpty { "Сборка" } }
        
        // Create a sheet for each source (Order)
        itemsBySource.forEach { (sourceName, items) ->
            val safeSheetName = try {
                 org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(sourceName)
            } catch (e: Exception) {
                "Order_${sourceName.hashCode()}"
            }
            
            var uniqueSheetName = safeSheetName
            var suffix = 1
            while (workbook.getSheet(uniqueSheetName) != null) {
                uniqueSheetName = "$safeSheetName ($suffix)"
                suffix++
            }
            
            val sheet = workbook.createSheet(uniqueSheetName)
            var rowNum = 0
            
            // 1. Shipment Info section
            val infoHeaderRow = sheet.createRow(rowNum++)
            infoHeaderRow.createCell(0).setCellValue("ИНФОРМАЦИЯ О ПОСТАВКЕ:")
            
            if (shipmentInfo.isNotEmpty()) {
                val infoRow = sheet.createRow(rowNum++)
                infoRow.createCell(0).setCellValue(shipmentInfo)
                rowNum++
            }
            
            // 2. Summary Table (1 assembly - 1 line per box)
            val summaryHeaderRow = sheet.createRow(rowNum++)
            summaryHeaderRow.createCell(0).setCellValue("ОТЧЕТ ПО СБОРКЕ:")
            
            val summaryTableHead = sheet.createRow(rowNum++)
            summaryTableHead.createCell(0).setCellValue("Отпр. №")
            summaryTableHead.createCell(1).setCellValue("Кол-во")
            summaryTableHead.createCell(2).setCellValue("Коробка")
            
            val boxes = items.filter { it.box > 0 }.map { it.box }.distinct().sorted()
            
            boxes.forEach { boxNum ->
                val boxTotalQty = items.filter { it.box == boxNum }.sumOf { it.collectedQuantity }
                if (boxTotalQty > 0) {
                    val row = sheet.createRow(rowNum++)
                    row.createCell(0).setCellValue(sourceName)
                    row.createCell(1).setCellValue(boxTotalQty.toDouble())
                    row.createCell(2).setCellValue(boxNum.toDouble())
                }
            }
            rowNum++ // Spacing
            
            // 3. Detailed Box Breakdown
            boxes.forEach { boxNum ->
                val boxItems = items.filter { it.box == boxNum && (it.status == ItemStatus.COLLECTED || it.status == ItemStatus.QUANTITY_CHANGED) }
                
                if (boxItems.isNotEmpty()) {
                    // Box Header
                    val boxHeaderRow = sheet.createRow(rowNum++)
                    boxHeaderRow.createCell(0).setCellValue("КОРОБКА № $boxNum")
                    
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
                    
                    // Box Discrepancies (those that were collected but quantity changed)
                    val boxDiscrepancies = boxItems.filter { it.status == ItemStatus.QUANTITY_CHANGED && it.collectedQuantity != it.quantity }
                    if (boxDiscrepancies.isNotEmpty()) {
                        val discHeader = sheet.createRow(rowNum++)
                        discHeader.createCell(1).setCellValue("Изменения в коробке $boxNum:")
                        boxDiscrepancies.forEach { item ->
                            val row = sheet.createRow(rowNum++)
                            val identifier = item.article
                            row.createCell(1).setCellValue("Арт: $identifier (было ${item.quantity}, стало ${item.collectedQuantity})")
                        }
                    }
                    
                    rowNum++ // Spacing between boxes
                }
            }
            
            // 4. Uncollected / Skipped section
            val uncollected = items.filter { it.status == ItemStatus.SKIPPED || it.status == ItemStatus.PENDING || (it.status == ItemStatus.QUANTITY_CHANGED && it.collectedQuantity == 0) }
            if (uncollected.isNotEmpty()) {
                val skipHeaderRow = sheet.createRow(rowNum++)
                skipHeaderRow.createCell(0).setCellValue("НЕ НАЙДЕНО / ПРОПУЩЕНО:")
                
                uncollected.forEach { item ->
                    val row = sheet.createRow(rowNum++)
                    row.createCell(0).setCellValue(item.quantity.toDouble())
                    row.createCell(1).setCellValue(item.article)
                    row.createCell(2).setCellValue(item.barcode)
                }
            }
            
            // Set column widths
            sheet.setColumnWidth(0, 20 * 256)
            sheet.setColumnWidth(1, 35 * 256)
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
