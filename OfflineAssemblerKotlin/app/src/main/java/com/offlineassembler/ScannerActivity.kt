package com.offlineassembler

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.offlineassembler.data.SessionManager
import com.offlineassembler.databinding.ActivityScannerBinding
import com.offlineassembler.model.AssemblyItem
import com.offlineassembler.model.AssemblySession
import com.offlineassembler.model.ItemStatus
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sessionManager: SessionManager
    private var session: AssemblySession? = null
    private var currentItem: AssemblyItem? = null
    private var currentItemIndex: Int = -1
    
    private val scanner: BarcodeScanner = BarcodeScanning.getClient()
    
    // Add ProductDatabase for search mode
    private lateinit var db: com.offlineassembler.data.ProductDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        session = sessionManager.loadSession()
        db = com.offlineassembler.data.ProductDatabase(this) // Init DB

        if (session == null && !intent.getBooleanExtra("SEARCH_MODE", false)) {
            Toast.makeText(this, "Сессия не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val isSearchMode = intent.getBooleanExtra("SEARCH_MODE", false)
        if (isSearchMode) {
            binding.totalProgress.visibility = View.GONE
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupUI()
        updateGlobalProgress()
    }

    private fun updateGlobalProgress() {
        val s = session ?: return
        val total = s.items.sumOf { it.quantity }
        val collected = s.items.sumOf { it.collectedQuantity }
        binding.totalProgress.text = "Собрано: $collected / $total"
    }

    private fun setupUI() {
        binding.closeButton.setOnClickListener { finish() }
        
        binding.addButton.setOnClickListener {
            onAddOne()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Ошибка камеры", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcodeValue = barcodes[0].rawValue ?: ""
                        if (barcodeValue.isNotEmpty()) {
                            runOnUiThread {
                                onBarcodeScanned(barcodeValue)
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private var lastScannedBarcode: String = ""

    private fun onBarcodeScanned(barcode: String) {
        if (intent.getBooleanExtra("SEARCH_MODE", false)) {
            val product = db.search(barcode).firstOrNull { it.barcode == barcode }
            if (product != null) {
                // Determine color - simpler logic for search mode
                binding.scanFrame.setBackgroundResource(R.drawable.bg_scan_frame_white) // Or custom color?
                
                // Show info
                binding.scanInfoCard.visibility = View.VISIBLE
                binding.itemName.text = product.name
                binding.itemBarcode.text = product.barcode
                binding.itemQuantity.text = "Место: ${product.location}"
                
                // Allow printing
                val item = AssemblyItem(
                    article = product.article,
                    name = product.name,
                    barcode = product.barcode,
                    quantity = 0 // Not relevant
                )
                
                binding.itemName.setOnClickListener {
                    PrinterService.showPrintConfirmation(binding.root, item)
                }
                binding.itemBarcode.setOnClickListener {
                    PrinterService.showPrintConfirmation(binding.root, item)
                }
                binding.scanInfoCard.setOnClickListener {
                     PrinterService.showPrintConfirmation(binding.root, item)
                }
                
            } else {
                Toast.makeText(this, "Товар не найден", Toast.LENGTH_SHORT).show()
                binding.scanInfoCard.visibility = View.GONE
            }
            return
        }

        // Extend the "colored" state
        binding.scanOverlay.removeCallbacks(resetFrameRunnable)
        binding.scanOverlay.postDelayed(resetFrameRunnable, 800)
        
        val s = session ?: return
        
        var foundToCollect: AssemblyItem? = null
        var foundIndex: Int = -1

        for (i in s.items.indices) {
            val item = s.items[i]
            if (item.barcode == barcode && item.collectedQuantity < item.quantity) {
                foundToCollect = item
                foundIndex = i
                break
            }
        }

        if (foundToCollect != null) {
            // Case 1: Matching found and needs collection -> GREEN
            binding.scanOverlay.setBackgroundResource(R.drawable.bg_scan_frame)
            if (barcode != lastScannedBarcode || binding.itemCard.visibility != View.VISIBLE) {
                lastScannedBarcode = barcode
                currentItem = foundToCollect
                currentItemIndex = foundIndex
                showItemInfo(foundToCollect)
            }
        } else {
            // Case 2: Either not in session or already collected
            val existsAtAll = s.items.any { it.barcode == barcode }
            if (existsAtAll) {
                // Case 2.1: Exists but fully collected -> WHITE
                binding.scanOverlay.setBackgroundResource(R.drawable.bg_scan_frame_white)
            } else {
                // Case 2.2: Not in assembly -> RED
                binding.scanOverlay.setBackgroundResource(R.drawable.bg_scan_frame_error)
            }
        }
    }

    private val resetFrameRunnable = Runnable {
        binding.scanOverlay.setBackgroundResource(R.drawable.bg_scan_frame_white)
    }

    private fun showItemInfo(item: AssemblyItem) {
        binding.itemCard.visibility = View.VISIBLE
        binding.itemName.text = item.name
        binding.itemName.setOnClickListener { 
            PrinterService.showPrintConfirmation(it, item)
        }
        
        binding.itemLocation.text = "Место: ${item.location.ifEmpty { "---" }}"
        
        val barcodeLast4 = if (item.barcode.length >= 4) item.barcode.takeLast(4) else item.barcode
        binding.itemBarcode.text = "ШК: $barcodeLast4"
        binding.itemBarcode.setOnClickListener { 
            PrinterService.showPrintConfirmation(it, item)
        }
        
        binding.itemQuantity.text = "${item.collectedQuantity} / ${item.quantity}"
        
        if (item.collectedQuantity >= item.quantity) {
             binding.addButton.isEnabled = false
             binding.addButton.text = "Собрано"
        } else {
             binding.addButton.isEnabled = true
             binding.addButton.text = "+1 Добавить"
        }
    }

    private fun onAddOne() {
        val s = session ?: return
        val item = currentItem ?: return
        val index = currentItemIndex
        
        if (item.collectedQuantity < item.quantity) {
            item.collectedQuantity += 1
            
            if (item.collectedQuantity >= item.quantity) {
                item.status = ItemStatus.COLLECTED
            } else {
                item.status = ItemStatus.QUANTITY_CHANGED
            }
            
            // Set box number for the item
            item.box = s.sheetBoxCounters.getOrPut(item.sourceName) { 1 }
            
            sessionManager.saveSession(s)
            
            // Update UI
            showItemInfo(item)
            updateGlobalProgress()
            
            Toast.makeText(this, "Добавлено: ${item.name} (+1)", Toast.LENGTH_SHORT).show()
            
            // If finished, maybe hide the card after a delay or just leave it
            if (item.collectedQuantity >= item.quantity) {
                // Reset current item so the user can scan next one
                // currentItem = null 
                // Using runOnUiThread delay if we want to auto-hide
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Разрешение на камеру не получено", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
