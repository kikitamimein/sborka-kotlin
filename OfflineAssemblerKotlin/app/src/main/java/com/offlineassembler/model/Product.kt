package com.offlineassembler.model

import com.google.gson.annotations.SerializedName

data class Product(
    @SerializedName("article")
    val article: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("barcode")
    val barcode: String = ""
)
