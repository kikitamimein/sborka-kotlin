package com.offlineassembler

import android.util.Log
import com.offlineassembler.model.AssemblyItem
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

object PrinterService {
    private const val PRINTER_IP = "10.0.0.167"
    private const val PRINTER_PORT = 9100

    fun printBarcode(item: AssemblyItem) {
        thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(PRINTER_IP, PRINTER_PORT), 5000)
                val outputStream: OutputStream = socket.getOutputStream()

                val description = if (item.barcode.isNotEmpty() && item.name.contains(item.barcode)) {
                    item.name.replace(item.barcode, "").trim()
                } else {
                    item.name
                }.take(100) // Limit length just in case

                val article = item.article
                val barcode = item.barcode

                // TSPL Commands for 55x40mm
                // Width 55mm ~ 440 dots
                // Height 40mm ~ 320 dots
                val tspl = StringBuilder()
                tspl.append("SIZE 55 mm, 40 mm\r\n")
                tspl.append("GAP 3 mm, 0\r\n")
                tspl.append("DIRECTION 1\r\n")
                tspl.append("CLS\r\n")
                tspl.append("CODEPAGE UTF-8\r\n")

                // Top description: small font
                tspl.append("TEXT 10,10,\"1\",0,1,1,\"$description\"\r\n")

                // Middle Barcode: height modified to ~240 to fit readable text and spacing
                if (barcode.isNotEmpty()) {
                    tspl.append("BARCODE 10,30,\"128\",240,1,0,2,4,\"$barcode\"\r\n")
                }

                // Bottom Article
                tspl.append("TEXT 10,295,\"1\",0,1,1,\"Арт: $article\"\r\n")

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
