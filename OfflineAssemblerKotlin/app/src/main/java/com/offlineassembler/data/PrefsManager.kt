package com.offlineassembler.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    var inputFolderUri: String?
        get() = prefs.getString(KEY_INPUT_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_INPUT_FOLDER, value).apply()
        
    var outputFolderUri: String?
        get() = prefs.getString(KEY_OUTPUT_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_OUTPUT_FOLDER, value).apply()
        
    companion object {
        private const val PREFS_NAME = "offline_assembler_settings"
        private const val KEY_INPUT_FOLDER = "input_folder_uri"
        private const val KEY_OUTPUT_FOLDER = "output_folder_uri"
    }
}
