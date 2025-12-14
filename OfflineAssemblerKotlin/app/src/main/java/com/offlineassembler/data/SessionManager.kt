package com.offlineassembler.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.offlineassembler.model.AssemblySession

class SessionManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveSession(session: AssemblySession) {
        val json = gson.toJson(session)
        prefs.edit().putString(KEY_SESSION, json).apply()
    }
    
    fun loadSession(): AssemblySession? {
        val json = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            gson.fromJson(json, AssemblySession::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun hasSession(): Boolean {
        return prefs.contains(KEY_SESSION)
    }
    
    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "offline_assembler_prefs"
        private const val KEY_SESSION = "assembly_session"
    }
}
