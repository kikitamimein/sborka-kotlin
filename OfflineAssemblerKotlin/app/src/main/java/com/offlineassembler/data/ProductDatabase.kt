package com.offlineassembler.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.offlineassembler.model.Product
import java.io.File

class ProductDatabase(context: Context) {
    private val file = File(context.filesDir, "products.json")
    private val gson = Gson()

    fun saveProducts(products: List<Product>) {
        file.writeText(gson.toJson(products))
    }

    fun loadProducts(): List<Product> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Product>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun search(query: String): List<Product> {
        val products = loadProducts()
        if (query.isEmpty()) return products
        val q = query.lowercase()
        return products.filter { 
            it.article.lowercase().contains(q) || it.barcode.contains(q) || it.name.lowercase().contains(q)
        }
    }
}
