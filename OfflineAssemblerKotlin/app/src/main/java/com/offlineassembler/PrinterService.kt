package com.offlineassembler

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.offlineassembler.data.PrefsManager
import com.offlineassembler.model.AssemblyItem
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

object PrinterService {
    
    fun showPrintConfirmation(view: View, item: AssemblyItem) {
        val context = view.context
        val dialog = BottomSheetDialog(context, R.style.TransparentBottomSheetDialog)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.layout_print_confirmation, null)
        
        var count = 1
        val countText = dialogView.findViewById<TextView>(R.id.printCount)
        
        dialogView.findViewById<TextView>(R.id.itemName).text = item.name
        dialogView.findViewById<TextView>(R.id.itemBarcode).text = "ШК: ${item.barcode}"
        
        dialogView.findViewById<View>(R.id.minusButton).setOnClickListener {
            if (count > 1) {
                count--
                countText.text = count.toString()
            }
        }

        dialogView.findViewById<View>(R.id.plusButton).setOnClickListener {
            count++
            countText.text = count.toString()
        }

        dialogView.findViewById<View>(R.id.printConfirmButton).setOnClickListener {
            printBarcode(context, item, count)
            dialog.dismiss()
        }
        
        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun printBarcode(context: Context, item: AssemblyItem, quantity: Int = 1) {
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

                val tspl = StringBuilder()
                tspl.append("SIZE 55 mm, 40 mm\r\n")
                tspl.append("GAP 3 mm, 0\r\n")
                tspl.append("DIRECTION 1\r\n")
                tspl.append("CLS\r\n")
                tspl.append("CODEPAGE UTF-8\r\n")
                tspl.append("SET ALIGNMENT CENTER\r\n")

                val centerX = 220

                // 1. Top description - exactly 30 dots
                // X=10, Y=5, Width=420, Height=30, Font="1", Rotation=0, X-scale=1, Y-scale=1, Space=1, Alignment=1 (Center)
                tspl.append("BLOCK 10,5,420,30,\"1\",0,1,1,1,1,\"$description\"\r\n")

                if (barcode.isNotEmpty()) {
                    // 2. Middle Barcode - maximized height
                    // Y from 40 to 250 (Height 210)
                    tspl.append("BARCODE 220,40,\"128\",210,0,0,3,6,2,\"$barcode\"\r\n")
                    
                    // 3. Barcode digits - 2x larger (Scale 2,2)
                    tspl.append("TEXT 220,255,\"2\",0,2,2,2,\"$barcode\"\r\n")
                }

                // 4. Bottom Article - bottom area (dots 300+)
                tspl.append("TEXT 220,305,\"1\",0,1,1,2,\"Арт: $article\"\r\n")

                tspl.append("PRINT $quantity,1\r\n")

                outputStream.write(tspl.toString().toByteArray(Charsets.UTF_8))
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                Log.e("PrinterService", "Error printing barcode", e)
            }
        }
    }
}
