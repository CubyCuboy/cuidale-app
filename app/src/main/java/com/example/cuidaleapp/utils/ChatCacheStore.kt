package com.example.cuidaleapp.utils

import android.content.Context
import com.example.cuidaleapp.models.ChatMessage
import com.example.cuidaleapp.models.UserData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Caché local y separada de los chats (mensajes + resumen de conversaciones).
 *
 * Se guarda en filesDir/chat_cache/<uid>/ para que:
 *  - sobreviva al borrado de caché de la app (multimedia),
 *  - permita ver los chats sin conexión,
 *  - se elimine únicamente al cerrar la sesión por completo.
 */
object ChatCacheStore {

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun rootDir(): File = File(appContext!!.filesDir, "chat_cache").apply {
        if (!exists()) mkdirs()
    }

    fun userDir(uid: String): File = File(rootDir(), uid).apply {
        if (!exists()) mkdirs()
    }

    private fun messagesFile(uid: String, chatId: String): File =
        File(userDir(uid), "messages_${chatId.hashCode() and 0x7fffffff}.json")

    private fun summaryFile(uid: String, chatId: String): File =
        File(userDir(uid), "summary_${chatId.hashCode() and 0x7fffffff}.json")

    // ── ChatMessage JSON ──
    private fun messageToJson(m: ChatMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("senderId", m.senderId)
        put("text", m.text)
        put("timestamp", m.timestamp)
        put("edited", m.edited)
        put("deleted", m.deleted)
        put("pinned", m.pinned)
        put("imageUrl", m.imageUrl ?: "")
        put("videoUrl", m.videoUrl ?: "")
        put("fileUrl", m.fileUrl ?: "")
        put("fileName", m.fileName ?: "")
        put("replyToId", m.replyToId ?: "")
        put("replyToText", m.replyToText ?: "")
        put("replyToName", m.replyToName ?: "")
        put("type", m.type)
        put("metadata", JSONObject(m.metadata))
        put("readBy", JSONObject(m.readBy.mapValues { it.value.toString() }))
    }

    private fun messageFromJson(o: JSONObject): ChatMessage = ChatMessage(
        id = o.optString("id"),
        senderId = o.optString("senderId"),
        text = o.optString("text"),
        timestamp = o.optLong("timestamp"),
        edited = o.optBoolean("edited"),
        deleted = o.optBoolean("deleted"),
        pinned = o.optBoolean("pinned"),
        imageUrl = o.optString("imageUrl").ifEmpty { null },
        videoUrl = o.optString("videoUrl").ifEmpty { null },
        fileUrl = o.optString("fileUrl").ifEmpty { null },
        fileName = o.optString("fileName").ifEmpty { null },
        replyToId = o.optString("replyToId").ifEmpty { null },
        replyToText = o.optString("replyToText").ifEmpty { null },
        replyToName = o.optString("replyToName").ifEmpty { null },
        type = o.optString("type", "text"),
        metadata = stringMap(o.optJSONObject("metadata")),
        readBy = o.optJSONObject("readBy")?.let { obj ->
            obj.keys().asSequence().associate { key -> key to obj.optBoolean(key) }
        } ?: emptyMap()
    )

    private fun stringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associate { key -> key to (obj.optString(key) ?: "") }
    }

    // ── Resumen de chat (para la lista sin conexión) ──
    private fun summaryToJson(chatId: String, otherUid: String, unread: Int, lastMessage: ChatMessage?, otherUser: UserData?): JSONObject =
        JSONObject().apply {
            put("chatId", chatId)
            put("otherUid", otherUid)
            put("unread", unread)
            put("lastMessage", lastMessage?.let { messageToJson(it) } ?: JSONObject.NULL)
            put("otherUser", otherUser?.let { user ->
                JSONObject().apply {
                    put("uid", user.uid)
                    put("nombre", user.nombre)
                    put("apellido_pa", user.apellido_pa)
                    put("fotoUrl", user.fotoUrl)
                }
            } ?: JSONObject.NULL)
        }

    private fun summaryFromJson(o: JSONObject): CachedChatSummary {
        val otherUserObj = o.optJSONObject("otherUser")
        val otherUser = otherUserObj?.let { u ->
            UserData(
                uid = u.optString("uid"),
                nombre = u.optString("nombre"),
                apellido_pa = u.optString("apellido_pa"),
                fotoUrl = u.optString("fotoUrl")
            )
        }
        val lastObj = o.optJSONObject("lastMessage")
        return CachedChatSummary(
            chatId = o.optString("chatId"),
            otherUid = o.optString("otherUid"),
            unread = o.optInt("unread"),
            lastMessage = lastObj?.let { messageFromJson(it) },
            otherUser = otherUser
        )
    }

    fun saveMessages(uid: String, chatId: String, messages: List<ChatMessage>) {
        runCatching {
            val arr = JSONArray()
            messages.forEach { arr.put(messageToJson(it)) }
            messagesFile(uid, chatId).writeText(arr.toString())
        }
    }

    fun loadMessages(uid: String, chatId: String): List<ChatMessage> {
        pruneExpired(uid)
        return runCatching {
            val file = messagesFile(uid, chatId)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            val cut = System.currentTimeMillis() - retentionMillis()
            (0 until arr.length()).map { messageFromJson(arr.getJSONObject(it)) }
                .filter { it.timestamp >= cut }
        }.getOrDefault(emptyList())
    }

    fun saveSummary(uid: String, summary: CachedChatSummary) {
        runCatching {
            summaryFile(uid, summary.chatId).writeText(summaryToJson(summary.chatId, summary.otherUid, summary.unread, summary.lastMessage, summary.otherUser).toString())
        }
    }

    fun loadSummaries(uid: String): List<CachedChatSummary> {
        pruneExpired(uid)
        return runCatching {
            val dir = userDir(uid)
            dir.listFiles { f -> f.name.startsWith("summary_") && f.name.endsWith(".json") }
                ?.mapNotNull { f ->
                    runCatching { summaryFromJson(JSONObject(f.readText())) }.getOrNull()
                } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    // ── Retención de caché para modo offline ──
    private const val PREFS_NAME = "cuidale_prefs"
    private const val KEY_RETENTION_DAYS = "offline_cache_days"

    @Volatile
    private var retentionDaysInMemory: Int? = null

    fun getRetentionDays(): Int {
        retentionDaysInMemory?.let { return it }
        val ctx = appContext ?: return 3
        val value = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_RETENTION_DAYS, 3)
        retentionDaysInMemory = value
        return value
    }

    fun setRetentionDays(days: Int) {
        val clamped = days.coerceIn(1, 30)
        retentionDaysInMemory = clamped
        val ctx = appContext ?: return
        // commit() escribe de forma síncrona y garantiza la persistencia inmediata,
        // evitando que la configuración se pierda si el proceso termina.
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_RETENTION_DAYS, clamped).commit()
    }

    private fun retentionMillis(): Long {
        return getRetentionDays().coerceAtLeast(1) * 24L * 60 * 60 * 1000
    }

    /**
     * Elimina archivos de caché de chats cuyo ÚLTIMO MENSAJE sea más antiguo
     * que la retención configurada. No borra chats con mensajes recientes,
     * aunque el archivo lleve tiempo sin escribirse (p.ej. chats no abiertos).
     */
    fun pruneExpired(uid: String) {
        runCatching {
            val dir = userDir(uid)
            val cut = System.currentTimeMillis() - retentionMillis()
            dir.listFiles { f -> f.name.startsWith("messages_") && f.name.endsWith(".json") }
                ?.forEach { f ->
                    val lastTs = runCatching {
                        val arr = JSONArray(f.readText())
                        if (arr.length() == 0) 0L else arr.getJSONObject(arr.length() - 1).optLong("timestamp")
                    }.getOrDefault(f.lastModified())
                    if (lastTs < cut) {
                        val hash = f.name.removePrefix("messages_").removeSuffix(".json")
                        File(dir, "summary_$hash.json").delete()
                        File(dir, "outbox_$hash.json").delete()
                        f.delete()
                    }
                }
        }
    }

    fun sizeForUser(uid: String): Long {
        if (uid.isBlank()) return 0L
        return runCatching { folderSize(userDir(uid)) }.getOrDefault(0L)
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += folderSize(it) }
        return total
    }

    fun clearChat(uid: String, chatId: String) {
        if (uid.isBlank() || chatId.isBlank()) return
        runCatching {
            val dir = userDir(uid)
            val hash = chatId.hashCode() and 0x7fffffff
            listOf("messages_$hash.json", "summary_$hash.json", "outbox_$hash.json")
                .forEach { File(dir, it).delete() }
        }
    }

    // ── Outbox: mensajes pendientes de envío (sin conexión) ──
    private fun outboxFile(uid: String, chatId: String): File =
        File(userDir(uid), "outbox_${chatId.hashCode() and 0x7fffffff}.json")

    private fun outboxToJson(m: OutboxMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("chatId", m.chatId)
        put("text", m.text)
        put("imageUrl", m.imageUrl ?: "")
        put("videoUrl", m.videoUrl ?: "")
        put("fileUrl", m.fileUrl ?: "")
        put("fileName", m.fileName ?: "")
        put("replyToId", m.replyToId ?: "")
        put("replyToText", m.replyToText ?: "")
        put("replyToName", m.replyToName ?: "")
        put("type", m.type)
        put("timestamp", m.timestamp)
        put("metadata", JSONObject(m.metadata))
    }

    private fun outboxFromJson(o: JSONObject): OutboxMessage = OutboxMessage(
        id = o.optString("id"),
        chatId = o.optString("chatId"),
        text = o.optString("text"),
        imageUrl = o.optString("imageUrl").ifEmpty { null },
        videoUrl = o.optString("videoUrl").ifEmpty { null },
        fileUrl = o.optString("fileUrl").ifEmpty { null },
        fileName = o.optString("fileName").ifEmpty { null },
        replyToId = o.optString("replyToId").ifEmpty { null },
        replyToText = o.optString("replyToText").ifEmpty { null },
        replyToName = o.optString("replyToName").ifEmpty { null },
        type = o.optString("type", "text"),
        timestamp = o.optLong("timestamp"),
        metadata = stringMap(o.optJSONObject("metadata"))
    )

    fun saveOutbox(uid: String, chatId: String, items: List<OutboxMessage>) {
        runCatching {
            val arr = JSONArray()
            items.forEach { arr.put(outboxToJson(it)) }
            outboxFile(uid, chatId).writeText(arr.toString())
        }
    }

    fun loadOutbox(uid: String, chatId: String): List<OutboxMessage> {
        return runCatching {
            val file = outboxFile(uid, chatId)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { outboxFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun clearOutbox(uid: String, chatId: String) {
        runCatching { outboxFile(uid, chatId).delete() }
    }

    fun clearForUser(uid: String) {
        runCatching { userDir(uid).deleteRecursively() }
    }

    fun clearAll() {
        runCatching { rootDir().deleteRecursively() }
    }
}

data class CachedChatSummary(
    val chatId: String,
    val otherUid: String,
    val unread: Int,
    val lastMessage: ChatMessage?,
    val otherUser: UserData? = null
)

data class OutboxMessage(
    val id: String,
    val chatId: String,
    val text: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToName: String? = null,
    val type: String = "text",
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
