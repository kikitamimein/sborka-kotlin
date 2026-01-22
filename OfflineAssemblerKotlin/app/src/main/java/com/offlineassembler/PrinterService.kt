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
    fun showPrintConfirmation(view: View, item: AssemblyItem) {
        val snackbar = Snackbar.make(view, "Распечатать штрихкод?\n${item.barcode}", Snackbar.LENGTH_LONG)
        
        // Custom styling for snackbar
        val snackbarView = snackbar.view
        val context = view.context
        
        snackbarView.setBackgroundColor(context.resources.getColor(R.color.surface, context.theme))
        val textView = snackbarView.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setTextColor(context.resources.getColor(R.color.text_primary, context.theme))
        textView.maxLines = 2
        textView.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER

        snackbar.setActionTextColor(context.resources.getColor(R.color.primary, context.theme))
        snackbar.setAction("ПЕЧАТЬ") {
            printBarcode(context, item)
        }
        
        // Center/Top positioning
        val params = snackbarView.layoutParams as android.widget.FrameLayout.LayoutParams
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        params.setMargins(48, 100, 48, 0)
        snackbarView.layoutParams = params
        
        snackbar.show()
    }

    private fun printBarcode(context: Context, item: AssemblyItem) {
        val prefs = PrefsManager(context)
        val ip = prefs.printerIp
        val port = prefs.printerPort

        thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 5000)
                val outputStream: OutputStream = socket.getOutputStream()

                val description = if (item.barcode.isNotEmpty() && item.name.contains(item.barcode)) {
                    item.name.replace(item.barcode, "").trim()
                } else {
                    item.name
                }.take(100)

                val article = item.article
                val barcode = item.barcode

                // Center alignment and settings
                tspl.append("SIZE 55 mm, 40 mm\r\n")
                tspl.append("GAP 3 mm, 0\r\n")
                tspl.append("DIRECTION 1\r\n")
                tspl.append("CLS\r\n")
                tspl.append("CODEPAGE UTF-8\r\n")
                tspl.append("SET ALIGNMENT CENTER\r\n")

                // Points for 55mm = 440 dots. Center = 220
                val centerX = 220

                // 1. Top description
                tspl.append("TEXT $centerX,10,\"1\",0,1,1,2,\"$description\"\r\n")

                if (barcode.isNotEmpty()) {
                    // 2. Middle Barcode (narrow 3, width ~360 dots)
                    // BARCODE X, Y, type, height, readable, rotation, narrow, wide, content
                    tspl.append("BARCODE $centerX,30,\"128\",200,0,0,3,6,2,\"$barcode\"\r\n")
                    
                    // 3. Barcode digits 2x larger
                    tspl.append("TEXT $centerX,240,\"2\",0,2,2,2,\"$barcode\"\r\n")
                }

                // 4. Bottom Article
                tspl.append("TEXT $centerX,300,\"1\",0,1,1,2,\"Арт: $article\"\r\n")

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
