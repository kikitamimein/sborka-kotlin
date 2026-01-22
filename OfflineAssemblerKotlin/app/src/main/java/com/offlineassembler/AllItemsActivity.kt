package com.offlineassembler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.offlineassembler.data.ProductDatabase
import com.offlineassembler.databinding.ActivityAllItemsBinding
import com.offlineassembler.excel.ExcelProcessor
import com.offlineassembler.model.AssemblyItem
import com.offlineassembler.model.Product
import kotlin.concurrent.thread

class AllItemsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllItemsBinding
    private lateinit var db: ProductDatabase
    private lateinit var adapter: ProductAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importProducts(uri)
        }
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCAN_RESULT") ?: ""
            if (barcode.isNotEmpty()) {
                binding.searchEditText.setText(barcode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllItemsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = ProductDatabase(this)
        setupUI()
        loadAll()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.importButton.setOnClickListener { importLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
        
        adapter = ProductAdapter { product ->
            // Convert Product to AssemblyItem for PrinterService
            val item = AssemblyItem(
                article = product.article,
                name = product.name,
                barcode = product.barcode,
                quantity = 1
            )
            PrinterService.showPrintConfirmation(binding.root, item)
        }

        binding.productsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.productsRecyclerView.adapter = adapter

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.updateList(db.search(s.toString()))
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.scanSearchButton.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java).apply {
                putExtra("SEARCH_MODE", true)
            }
            scanLauncher.launch(intent)
        }
    }

    private fun loadAll() {
        adapter.updateList(db.loadProducts())
    }

    private fun importProducts(uri: Uri) {
        thread {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@thread
                val processor = ExcelProcessor()
                val result = processor.processFile(inputStream)
                inputStream.close()

                val products = result.items.map { 
                    Product(
                        article = it.article, 
                        name = it.name, 
                        barcode = it.barcode,
                        location = it.location
                    )
                }.distinctBy { it.barcode.ifEmpty { it.article } }

                db.saveProducts(products)
                
                runOnUiThread {
                    loadAll()
                    Toast.makeText(this, "Загружено ${products.size} товаров", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class ProductAdapter(private val onPrint: (Product) -> Unit) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {
        private var items = listOf<Product>()

        fun updateList(newList: List<Product>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.productName)
            val article: TextView = view.findViewById(R.id.productArticle)
            val barcode: TextView = view.findViewById(R.id.productBarcode)
            val location: TextView = view.findViewById(R.id.productLocation)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.article.text = "Арт: ${item.article}"
            holder.barcode.text = "ШК: ${item.barcode}"
            holder.location.text = "Место: ${item.location.ifEmpty { "---" }}"
            holder.name.setOnClickListener { onPrint(item) }
        }

        override fun getItemCount() = items.size
    }
}
