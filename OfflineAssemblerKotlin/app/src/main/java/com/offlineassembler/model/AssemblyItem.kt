package com.offlineassembler.model

import com.google.gson.annotations.SerializedName

data class AssemblyItem(
    @SerializedName("article")
    val article: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("quantity")
    val quantity: Int,
    
    @SerializedName("barcode")
    val barcode: String = "",
    
    @SerializedName("location")
    val location: String = "",
    
    @SerializedName("status")
    var status: ItemStatus = ItemStatus.PENDING,
    
    @SerializedName("collected_quantity")
    var collectedQuantity: Int = 0,
    
    @SerializedName("box")
    var box: Int = 0
)

enum class ItemStatus {
    PENDING,
    COLLECTED,
    SKIPPED,
    QUANTITY_CHANGED
}

data class AssemblySession(
    @SerializedName("items")
    val items: MutableList<AssemblyItem>,
    
    @SerializedName("current_index")
    var currentIndex: Int = 0,
    
    @SerializedName("current_box")
    var currentBox: Int = 1,
    
    @SerializedName("shipment_info")
    val shipmentInfo: String = "",
    
    @SerializedName("input_file_path")
    val inputFilePath: String = "",
    
    @SerializedName("output_directory")
    var outputDirectory: String = "",
    
    @SerializedName("output_dir_uri")
    var outputDirUri: String = ""
)
