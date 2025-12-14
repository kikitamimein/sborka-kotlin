package com.offlineassembler

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.offlineassembler.data.SessionManager
import com.offlineassembler.databinding.ActivityReviewBinding
import com.offlineassembler.databinding.ItemReviewBinding
import com.offlineassembler.model.AssemblyItem
import com.offlineassembler.model.AssemblySession
import com.offlineassembler.model.ItemStatus

class ReviewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityReviewBinding
    private lateinit var sessionManager: SessionManager
    private var session: AssemblySession? = null
    private lateinit var adapter: ReviewAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        sessionManager = SessionManager(this)
        session = sessionManager.loadSession()
        
        if (session == null) {
            finish()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        
        adapter = ReviewAdapter(
            items = session!!.items,
            onQuantityClick = { index -> showEditQuantityDialog(index) },
            onBoxClick = { index -> showEditBoxDialog(index) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun showEditQuantityDialog(index: Int) {
        val item = session!!.items[index]
        
        val input = EditText(this).apply {
            hint = "Количество"
            setText(item.collectedQuantity.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Изменить количество")
            .setMessage(item.name)
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                try {
                    val newQty = input.text.toString().toInt()
                    if (newQty >= 0) {
                        item.collectedQuantity = newQty
                        item.status = if (newQty > 0) ItemStatus.QUANTITY_CHANGED else ItemStatus.SKIPPED
                        sessionManager.saveSession(session!!)
                        adapter.notifyItemChanged(index)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showEditBoxDialog(index: Int) {
        val item = session!!.items[index]
        
        val input = EditText(this).apply {
            hint = "Номер коробки"
            setText(if (item.box > 0) item.box.toString() else "1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Изменить коробку")
            .setMessage(item.name)
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                try {
                    val newBox = input.text.toString().toInt()
                    if (newBox >= 1) {
                        item.box = newBox
                        sessionManager.saveSession(session!!)
                        adapter.notifyItemChanged(index)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    // Adapter
    inner class ReviewAdapter(
        private val items: List<AssemblyItem>,
        private val onQuantityClick: (Int) -> Unit,
        private val onBoxClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {
        
        inner class ViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val b = holder.binding
            
            // Status icon and color
            val (iconRes, tintColor) = when (item.status) {
                ItemStatus.COLLECTED -> android.R.drawable.presence_online to R.color.success
                ItemStatus.SKIPPED -> android.R.drawable.presence_busy to R.color.error
                ItemStatus.QUANTITY_CHANGED -> android.R.drawable.presence_away to R.color.accent
                else -> android.R.drawable.presence_invisible to R.color.text_secondary
            }
            b.statusIcon.setImageResource(iconRes)
            b.statusIcon.setColorFilter(resources.getColor(tintColor, theme))
            
            b.locationText.text = item.location.ifEmpty { "-" }
            
            val barcodeLast4 = if (item.barcode.length >= 4) item.barcode.takeLast(4) else item.barcode
            b.barcodeText.text = barcodeLast4.ifEmpty { "-" }
            
            b.planText.text = item.quantity.toString()
            
            b.factText.text = item.collectedQuantity.toString()
            b.factText.setTextColor(resources.getColor(
                when (item.status) {
                    ItemStatus.COLLECTED -> R.color.success
                    ItemStatus.SKIPPED -> R.color.error
                    ItemStatus.QUANTITY_CHANGED -> R.color.accent
                    else -> R.color.text_secondary
                }, theme))
            b.factText.setOnClickListener { onQuantityClick(position) }
            
            b.boxText.text = if (item.box > 0) item.box.toString() else "-"
            b.boxText.setOnClickListener { onBoxClick(position) }
        }
        
        override fun getItemCount() = items.size
    }
}
