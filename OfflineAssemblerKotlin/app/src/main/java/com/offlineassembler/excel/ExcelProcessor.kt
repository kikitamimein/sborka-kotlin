package com.offlineassembler.excel

import com.offlineassembler.model.AssemblyItem
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

class ExcelProcessor {
    
    data class ProcessResult(
        val items: List<AssemblyItem>,
        val shipmentInfo: String
    )
    
    fun processFile(inputStream: InputStream): ProcessResult {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        
        val items = mutableListOf<AssemblyItem>()
        var shipmentInfo = ""
        
        // Find header row and column indices
        var headerRowIndex = -1
        var articleCol = -1
        var nameCol = -1
        var quantityCol = -1
        var barcodeCol = -1
        var locationCol = -1
        
        // Search for header row (contains "Артикул" or "Наименование")
        for (rowIndex in 0 until minOf(20, sheet.lastRowNum + 1)) {
            val row = sheet.getRow(rowIndex) ?: continue
            
            for (cellIndex in 0 until row.lastCellNum) {
                val cell = row.getCell(cellIndex) ?: continue
                val value = cell.toString().trim().lowercase()
                
                when {
                    value.contains("артикул") -> {
                        articleCol = cellIndex
                        headerRowIndex = rowIndex
                    }
                    value.contains("наименование") || value.contains("название") -> {
                        nameCol = cellIndex
                        headerRowIndex = rowIndex
                    }
                    value.contains("количество") || value.contains("кол-во") || value.contains("кол.") -> {
                        quantityCol = cellIndex
                    }
                    value.contains("штрихкод") || value.contains("штрих-код") || value.contains("баркод") -> {
                        barcodeCol = cellIndex
                    }
                    value.contains("ячейка") || value.contains("место") || value.contains("локация") -> {
                        locationCol = cellIndex
                    }
                }
            }
            
            if (headerRowIndex >= 0) break
        }
        
        // Extract shipment info from rows before header
        if (headerRowIndex > 0) {
            val infoBuilder = StringBuilder()
            for (rowIndex in 0 until headerRowIndex) {
                val row = sheet.getRow(rowIndex) ?: continue
                val firstCell = row.getCell(0)
                if (firstCell != null) {
                    val text = firstCell.toString().trim()
                    if (text.isNotEmpty()) {
                        infoBuilder.append(text).append("\n")
                    }
                }
            }
            shipmentInfo = infoBuilder.toString().trim()
        }
        
        // If no header found, use defaults
        if (headerRowIndex < 0) {
            headerRowIndex = 0
            articleCol = 0
            nameCol = 1
            quantityCol = 2
        }
        
        // Read data rows
        for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            
            val article = if (articleCol >= 0) row.getCell(articleCol)?.toString()?.trim() ?: "" else ""
            val name = if (nameCol >= 0) row.getCell(nameCol)?.toString()?.trim() ?: "" else ""
            val quantityStr = if (quantityCol >= 0) row.getCell(quantityCol)?.toString()?.trim() ?: "0" else "0"
            val barcode = if (barcodeCol >= 0) row.getCell(barcodeCol)?.toString()?.trim() ?: "" else ""
            val location = if (locationCol >= 0) row.getCell(locationCol)?.toString()?.trim() ?: "" else ""
            
            // Skip empty rows
            if (article.isEmpty() && name.isEmpty()) continue
            
            // Parse quantity
            val quantity = try {
                quantityStr.toDouble().toInt()
            } catch (e: Exception) {
                0
            }
            
            if (quantity <= 0) continue
            
            items.add(
                AssemblyItem(
                    article = article,
                    name = name,
                    quantity = quantity,
                    barcode = barcode,
                    location = location
                )
            )
        }
        
        workbook.close()
        return ProcessResult(items, shipmentInfo)
    }
}
