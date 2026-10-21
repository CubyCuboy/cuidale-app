package com.example.cuidaleapp.data

import android.content.Context
import android.content.SharedPreferences
import com.example.cuidaleapp.models.AccountHistoryEntry
import com.example.cuidaleapp.models.LinkedAccountDetail
import com.example.cuidaleapp.models.UserData
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject

class UserSession(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    fun saveUser(user: UserData) {
        val historyArray = JSONArray()
        user.accountHistory.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("action", entry.action)
                put("title", entry.title)
                put("description", entry.description)
                put("timestamp", entry.timestamp)
            }
            historyArray.put(obj)
        }

        val linkedArray = JSONArray()
        user.linkedAccounts.forEach { acc ->
            val obj = JSONObject().apply {
                put("providerId", acc.providerId)
                put("providerName", acc.providerName)
                put("accountName", acc.accountName)
                put("linkedAt", acc.linkedAt)
                put("verificado", acc.verificado)
            }
            linkedArray.put(obj)
        }

        prefs.edit().apply {
            putString("user_uid", user.uid)
            putString("user_nombre", user.nombre)
            putString("user_nombre_s", user.nombre_s)
            putString("user_apellido_pa", user.apellido_pa)
            putString("user_apellido_ma", user.apellido_ma)
            putString("user_correo", user.correo)
            putString("user_rut", user.rut)
            putString("user_rol", user.rol)
            putString("user_telefono", user.telefono)
            putString("user_fotoUrl", user.fotoUrl)
            putBoolean("user_telefonoVerificado", user.telefonoVerificado)
            putBoolean("user_verificado", user.verificado)
            putBoolean("user_completado", user.completado)
            putString("user_descripcion", user.descripcion)
            putString("user_id_region", user.id_region)
            putString("user_region", user.region)
            putString("user_comuna", user.comuna)
            putString("user_accountHistory", historyArray.toString())
            putString("user_linkedAccounts", linkedArray.toString())

            user.createdAt?.let {
                if (it is Timestamp) putLong("user_createdAt", it.seconds)
            }
            user.updatedAt?.let {
                if (it is Timestamp) putLong("user_updatedAt", it.seconds)
            }
            apply()
        }
    }

    fun getUser(): UserData {
        val createdAtSec = prefs.getLong("user_createdAt", -1L)
        val updatedAtSec = prefs.getLong("user_updatedAt", -1L)

        val historyStr = prefs.getString("user_accountHistory", "[]") ?: "[]"
        val historyList = mutableListOf<AccountHistoryEntry>()
        try {
            val arr = JSONArray(historyStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                historyList.add(
                    AccountHistoryEntry(
                        id = obj.optString("id", ""),
                        action = obj.optString("action", ""),
                        title = obj.optString("title", ""),
                        description = obj.optString("description", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {}

        val linkedStr = prefs.getString("user_linkedAccounts", "[]") ?: "[]"
        val linkedList = mutableListOf<LinkedAccountDetail>()
        try {
            val arr = JSONArray(linkedStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                linkedList.add(
                    LinkedAccountDetail(
                        providerId = obj.optString("providerId", ""),
                        providerName = obj.optString("providerName", ""),
                        accountName = obj.optString("accountName", ""),
                        linkedAt = obj.optLong("linkedAt", 0L),
                        verificado = obj.optBoolean("verificado", false)
                    )
                )
            }
        } catch (e: Exception) {}

        return UserData(
            uid = prefs.getString("user_uid", "") ?: "",
            nombre = prefs.getString("user_nombre", "") ?: "",
            nombre_s = prefs.getString("user_nombre_s", "") ?: "",
            apellido_pa = prefs.getString("user_apellido_pa", "") ?: "",
            apellido_ma = prefs.getString("user_apellido_ma", "") ?: "",
            correo = prefs.getString("user_correo", "") ?: "",
            rut = prefs.getString("user_rut", "") ?: "",
            rol = prefs.getString("user_rol", "") ?: "",
            telefono = prefs.getString("user_telefono", "") ?: "",
            fotoUrl = prefs.getString("user_fotoUrl", "") ?: "",
            telefonoVerificado = prefs.getBoolean("user_telefonoVerificado", false),
            verificado = prefs.getBoolean("user_verificado", false),
            completado = prefs.getBoolean("user_completado", false),
            descripcion = prefs.getString("user_descripcion", "") ?: "",
            id_region = prefs.getString("user_id_region", "") ?: "",
            region = prefs.getString("user_region", "") ?: "",
            comuna = prefs.getString("user_comuna", "") ?: "",
            createdAt = if (createdAtSec != -1L) Timestamp(createdAtSec, 0) else null,
            updatedAt = if (updatedAtSec != -1L) Timestamp(updatedAtSec, 0) else null,
            accountHistory = historyList,
            linkedAccounts = linkedList
        )
    }

    fun clear() {
        val isDark = prefs.getBoolean("dark_mode", false)
        val keepSession = prefs.getBoolean("keep_session", true)
        prefs.edit().clear().apply()
        prefs.edit().putBoolean("dark_mode", isDark).apply()
        prefs.edit().putBoolean("keep_session", keepSession).apply()
    }
}
