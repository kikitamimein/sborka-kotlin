package com.offlineassembler

import android.util.Log
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.offlineassembler.model.AssemblyItem
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

object PrinterService {
    private const val PRINTER_IP = "10.0.0.167"
    private const val PRINTER_PORT = 9100

    fun showPrintConfirmation(view: View, item: AssemblyItem) {
        Snackbar.make(view, "Распечатать штрихкод?", Snackbar.LENGTH_LONG)
            .setAction("ПЕЧАТЬ") {
                printBarcode(item)
            }
            .show()
    }

    private fun printBarcode(item: AssemblyItem) {
        thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(PRINTER_IP, PRINTER_PORT), 5000)
                val outputStream: OutputStream = socket.getOutputStream()

                val description = if (item.barcode.isNotEmpty() && item.name.contains(item.barcode)) {
                    item.name.replace(item.barcode, "").trim()
                } else {
                    item.name
                }.take(100)

                val article = item.article
                val barcode = item.barcode

                // TSPL Commands for 55x40mm (440x320 dots at 203 DPI)
                val tspl = StringBuilder()
                tspl.append("SIZE 55 mm, 40 mm\r\n")
                tspl.append("GAP 3 mm, 0\r\n")
                tspl.append("DIRECTION 1\r\n")
                tspl.append("CLS\r\n")
                tspl.append("CODEPAGE UTF-8\r\n")

                // 1. Top description: small font (Font 1, 8x12 dots)
                tspl.append("TEXT 10,10,\"1\",0,1,1,\"$description\"\r\n")

                // 2. Middle Barcode: narrow=3 to fill width better
                // Using human_readable=0 to manually print larger text below
                if (barcode.isNotEmpty()) {
                    tspl.append("BARCODE 10,30,\"128\",200,0,0,3,6,\"$barcode\"\r\n")
                    
                    // 3. Barcode digits 2x larger (Font 2 is 12x20, x2 becomes 24x40)
                    tspl.append("TEXT 10,240,\"2\",0,2,2,\"$barcode\"\r\n")
                }

                // 4. Bottom Article
                tspl.append("TEXT 10,300,\"1\",0,1,1,\"Арт: $article\"\r\n")

                tspl.append("PRINT 1,1\r\n")

                outputStream.write(tspl.toString().toByteArray(Charsets.UTF_8))
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                Log.e("PrinterService", "Error printing barcode", e)
            }
        }
    }
}
