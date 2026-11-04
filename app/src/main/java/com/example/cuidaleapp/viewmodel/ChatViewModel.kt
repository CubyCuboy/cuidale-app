package com.example.cuidaleapp.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.ChatMessage
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.cuidaleapp.utils.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference
    private val db = FirebaseFirestore.getInstance()
    private val chatCache = com.example.cuidaleapp.utils.ChatCacheStore
    
    val isOnline: StateFlow<Boolean> = ConnectivityObserver.isOnline

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _chatList = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chatList: StateFlow<List<ChatPreview>> = _chatList

    private val _usersMap = MutableStateFlow<Map<String, UserData>>(emptyMap())
    val usersMap: StateFlow<Map<String, UserData>> = _usersMap.asStateFlow()
    private val _lastMessagesMap = MutableStateFlow<Map<String, ChatMessage?>>(emptyMap())
    private val _unreadCountsMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _chatIdsMap = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount: StateFlow<Int> = _totalUnreadCount

    private val _typingUsers = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingUsers: StateFlow<Map<String, Boolean>> = _typingUsers

    private val _otherUserStatus = MutableStateFlow("desconectado")
    val otherUserStatus: StateFlow<String> = _otherUserStatus

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _pinnedMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val pinnedMessages: StateFlow<List<ChatMessage>> = _pinnedMessages

    private val _outbox = MutableStateFlow<List<com.example.cuidaleapp.utils.OutboxMessage>>(emptyList())
    val outbox: StateFlow<List<com.example.cuidaleapp.utils.OutboxMessage>> = _outbox

    private val _isOtherTyping = MutableStateFlow(false)
    val isOtherTyping: StateFlow<Boolean> = _isOtherTyping

    private val _myManualStatus = MutableStateFlow<String?>(null)
    val myManualStatus: StateFlow<String?> = _myManualStatus

    private val _lastActivityTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTimestamp: StateFlow<Long> = _lastActivityTimestamp

    data class ChatPreview(
        val otherUser: UserData,
        val lastMessage: ChatMessage?,
        val unreadCount: Int,
        val chatId: String
    )

    private var messagesListener: ValueEventListener? = null
    private var statusListener: ValueEventListener? = null
    private var typingListener: ValueEventListener? = null
    private var chatsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var blockingListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val chatMessageListeners = mutableMapOf<String, ValueEventListener>()
    private val chatTypingListeners = mutableMapOf<String, ValueEventListener>()
    private val userListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private var currentChatId: String? = null

    /**
     * Limpia TODO el estado en memoria (mensajes, lista de chats, outbox,
     * usuarios cacheados) tras borrar la caché local. Los listeners de Firestore
     * siguen vivos y volverán a poblar la UI al reconectar/reentrar.
     */
    fun clearAllCachedState() {
        _messages.value = emptyList()
        _pinnedMessages.value = emptyList()
        _outbox.value = emptyList()
        _chatList.value = emptyList()
        _totalUnreadCount.value = 0
        _lastMessagesMap.value = emptyMap()
        _unreadCountsMap.value = emptyMap()
        _chatIdsMap.value = emptyMap()
        _usersMap.value = emptyMap()
    }

    init {
        // Cuando se pierde la conexión, poblar la UI con la caché local del chat.
        // Cuando se recupera, reenviar automáticamente los mensajes pendientes (outbox).
        viewModelScope.launch {
            ConnectivityObserver.isOnline.collect { online ->
                val uid = auth.currentUser?.uid ?: return@collect
                if (!online) {
                    val cachedChats = chatCache.loadSummaries(uid)
                    if (cachedChats.isNotEmpty()) {
                        _chatList.value = cachedChats.map { s ->
                            val user = s.otherUser ?: UserData(uid = s.otherUid, nombre = "Cargando...")
                            ChatPreview(user, s.lastMessage, s.unread, s.chatId)
                        }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
                        _totalUnreadCount.value = _chatList.value.sumOf { it.unreadCount }
                    }
                    val activeChat = currentChatId
                    if (activeChat != null) {
                        val msgs = chatCache.loadMessages(uid, activeChat)
                        if (msgs.isNotEmpty()) {
                            _messages.value = msgs
                            _pinnedMessages.value = msgs.filter { it.pinned && !it.deleted }
                        }
                    }
                } else {
                    // Al volver la conexión, reenviar los mensajes pendientes.
                    val pending = _outbox.value.toList()
                    if (pending.isNotEmpty()) {
                        pending.forEach { sendOutboxMessage(it) }
                    }
                }
            }
        }
    }

    fun setManualStatus(status: String?) {
        _myManualStatus.value = status
        val uid = auth.currentUser?.uid ?: return
        if (status == null) {
            database.child("status").child(uid).child("manual").removeValue()
        } else {
            database.child("status").child(uid).child("manual").setValue(status)
        }
    }

    fun setUserStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val statusRef = database.child("status").child(uid)

        when (_myManualStatus.value) {
            "no_molestar" -> {
                statusRef.onDisconnect().cancel()
                statusRef.setValue(mapOf(
                    "estado" to "no molestar",
                    "lastSeen" to ServerValue.TIMESTAMP
                ))
            }
            "desconectado" -> {
                statusRef.onDisconnect().cancel()
                statusRef.setValue(mapOf(
                    "estado" to "desconectado",
                    "lastSeen" to ServerValue.TIMESTAMP
                ))
            }
            else -> {
                if (isOnline) {
                    val status = mapOf(
                        "estado" to "en línea",
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    statusRef.setValue(status)
                    statusRef.onDisconnect().setValue(mapOf(
                        "estado" to "desconectado",
                        "lastSeen" to ServerValue.TIMESTAMP
                    ))
                } else {
                    statusRef.setValue(mapOf(
                        "estado" to "desconectado",
                        "lastSeen" to ServerValue.TIMESTAMP
                    ))
                }
            }
        }
    }

    private var lastReportedAt = 0L

    fun reportActivity() {
        val uid = auth.currentUser?.uid ?: return
        _lastActivityTimestamp.value = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        if (now - lastReportedAt >= 30_000L) {
            lastReportedAt = now
            database.child("status").child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        val myUid = auth.currentUser?.uid ?: return
        val typingRef = database.child("typing").child(chatId).child(myUid)
        if (isTyping) {
            typingRef.setValue(true)
            typingRef.onDisconnect().removeValue()
        } else {
            typingRef.removeValue()
        }
    }

    fun listenTyping(chatId: String, otherUid: String) {
        typingListener?.let { database.child("typing").child(chatId).child(otherUid).removeEventListener(it) }
        
        typingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _isOtherTyping.value = snapshot.exists() && snapshot.getValue(Boolean::class.java) == true
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("typing").child(chatId).child(otherUid).addValueEventListener(typingListener!!)
    }

    fun listenUserStatus(otherUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        statusListener?.let { database.child("status").child(otherUid).removeEventListener(it) }
        
        db.collection("vinculos")
            .whereArrayContains("uids", myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val hasVinculo = snapshot.documents.any { doc ->
                    val uids = doc.get("uids") as? List<String> ?: emptyList()
                    doc.getString("estado") == "aceptado" && uids.contains(otherUid)
                }
                
                if (hasVinculo) {
                    statusListener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            _otherUserStatus.value = snapshot.child("estado").getValue(String::class.java) ?: "desconectado"
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    }
                    database.child("status").child(otherUid).addValueEventListener(statusListener!!)
                } else {
                    _otherUserStatus.value = "estado oculto"
                }
            }
        listenToBlocking(otherUid)
    }

    private fun listenToBlocking(otherUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        blockingListener?.remove()
        
        blockingListener = db.collection("usuarios").whereEqualTo("uid", otherUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                val bloqueados = snapshot.documents[0].get("bloqueados") as? List<String> ?: emptyList()
                _isBlocked.value = bloqueados.contains(myUid)
            }
    }

    fun listenMessages(chatId: String) {
        val myUid = auth.currentUser?.uid ?: return

        // Si ya estamos escuchando este chat, igual refrescamos la caché local
        // cuando no hay conexión (evita que aparezca vacío al reentrar offline).
        if (currentChatId == chatId && messagesListener != null) {
            if (!ConnectivityObserver.isOnline.value) {
                val cached = chatCache.loadMessages(myUid, chatId)
                if (cached.isNotEmpty()) {
                    _messages.value = cached
                    _pinnedMessages.value = cached.filter { it.pinned && !it.deleted }
                }
            }
            return
        }

        removeActiveListener()
        currentChatId = chatId

        _outbox.value = chatCache.loadOutbox(myUid, chatId)
        if (!ConnectivityObserver.isOnline.value) {
            val cached = chatCache.loadMessages(myUid, chatId)
            if (cached.isNotEmpty()) {
                _messages.value = cached
                _pinnedMessages.value = cached.filter { it.pinned && !it.deleted }
            }
        }

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { child ->
                    child.getValue(ChatMessage::class.java)?.let { list.add(it) }
                }
                val sortedList = list.sortedBy { it.timestamp }
                _messages.value = sortedList
                _pinnedMessages.value = sortedList.filter { it.pinned && !it.deleted }
                
                val uidForCache = auth.currentUser?.uid ?: return
                chatCache.saveMessages(uidForCache, chatId, sortedList)

                val myUid = uidForCache
                list.forEach { msg ->
                    if (msg.senderId != myUid && !msg.readBy.containsKey(myUid)) {
                        markAsRead(chatId, msg.id)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("messages").child(chatId).addValueEventListener(messagesListener!!)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun removeActiveListener() {
        currentChatId?.let { chatId ->
            messagesListener?.let { listener ->
                database.child("messages").child(chatId).removeEventListener(listener)
            }
            typingListener?.let { listener ->
                database.child("typing").child(chatId).removeEventListener(listener)
            }
        }
        messagesListener = null
        typingListener = null
    }

    /**
     * Desconecta los listeners del chat activo. Se usa al salir de la pantalla
     * de chat para que los mensajes nuevos no se marquen como leídos
     * automáticamente mientras el usuario ya no está viendo ese chat.
     */
    fun clearActiveChatListeners() {
        removeActiveListener()
    }

    fun listenChats() {
        val myUid = auth.currentUser?.uid ?: return
        if (chatsListener != null) return

        // Precargar la caché local (si existe) como estado inicial inmediato,
        // tanto online como offline. Se sobreescribirá al llegar datos frescos.
        val cachedInit = chatCache.loadSummaries(myUid)
        if (cachedInit.isNotEmpty()) {
            _chatList.value = cachedInit.map { s ->
                val user = s.otherUser ?: UserData(uid = s.otherUid, nombre = "Cargando...")
                ChatPreview(user, s.lastMessage, s.unread, s.chatId)
            }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
            _totalUnreadCount.value = _chatList.value.sumOf { it.unreadCount }
        }

        chatsListener = db.collection("vinculos")
            .whereArrayContains("uids", myUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val aceptados = snapshot?.documents?.filter {
                    it.getString("estado") == "aceptado"
                } ?: emptyList()

                val userIds = mutableListOf<String>()
                val chatIds = mutableListOf<String>()

                aceptados.forEach { doc ->
                    val uids = doc.get("uids") as? List<String> ?: emptyList()
                    val otherUid = uids.firstOrNull { it != myUid } ?: ""
                    if (otherUid.isNotEmpty()) {
                        userIds.add(otherUid)
                        chatIds.add(doc.id)
                    }
                }

                if (userIds.isEmpty()) {
                    // Si Firestore no devuelve vínculos (red o filtro) pero tenemos
                    // caché local, mantenerla visible en lugar de vaciar la lista.
                    val cached = chatCache.loadSummaries(myUid)
                    if (cached.isNotEmpty()) {
                        _chatList.value = cached.map { s ->
                            val user = s.otherUser ?: UserData(uid = s.otherUid, nombre = "Cargando...")
                            ChatPreview(user, s.lastMessage, s.unread, s.chatId)
                        }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
                        _totalUnreadCount.value = _chatList.value.sumOf { it.unreadCount }
                    } else {
                        _chatList.value = emptyList()
                        _totalUnreadCount.value = 0
                    }
                    return@addSnapshotListener
                }

                loadChatPreviews(userIds, chatIds, myUid)
            }
    }

    private fun loadChatPreviews(userIds: List<String>, chatIds: List<String>, myUid: String) {
        userIds.forEachIndexed { index, otherUid ->
            val chatId = chatIds[index]
            _chatIdsMap.value = _chatIdsMap.value + (otherUid to chatId)

            if (!userListeners.containsKey(otherUid)) {
                val uListener = db.collection("usuarios").whereEqualTo("uid", otherUid)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                        val doc = snapshot.documents[0]
                        val otherUser = doc.toObject(UserData::class.java)?.copy(uid = otherUid) ?: return@addSnapshotListener
                        
                        _usersMap.value = _usersMap.value + (otherUid to otherUser)
                        updateChatList()

                        if (otherUid == currentChatId?.split("_")?.find { it != myUid }) {
                            val bloqueados = doc.get("bloqueados") as? List<String> ?: emptyList()
                            _isBlocked.value = bloqueados.contains(myUid)
                        }
                    }
                userListeners[otherUid] = uListener
            }

            if (!chatMessageListeners.containsKey(chatId)) {
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val lastMsg = snapshot.children.lastOrNull()?.getValue(ChatMessage::class.java)
                        var unread = 0
                        snapshot.children.forEach { m ->
                            val msg = m.getValue(ChatMessage::class.java)
                            if (msg != null && msg.senderId != myUid && !msg.readBy.containsKey(myUid)) {
                                unread++
                            }
                        }
                        
                        _lastMessagesMap.value = _lastMessagesMap.value + (chatId to lastMsg)
                        _unreadCountsMap.value = _unreadCountsMap.value + (chatId to unread)
                        updateChatList()

                        val otherUser = _usersMap.value[otherUid]
                        chatCache.saveSummary(myUid, com.example.cuidaleapp.utils.CachedChatSummary(chatId, otherUid, unread, lastMsg, otherUser))
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                chatMessageListeners[chatId] = listener
                database.child("messages").child(chatId).addValueEventListener(listener)
            }
            
            if (!chatTypingListeners.containsKey(chatId)) {
                val tListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isTyping = snapshot.exists() && snapshot.getValue(Boolean::class.java) == true
                        _typingUsers.value = _typingUsers.value + (chatId to isTyping)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                chatTypingListeners[chatId] = tListener
                database.child("typing").child(chatId).child(otherUid).addValueEventListener(tListener)
            }
        }
    }

    private fun updateChatList() {
        val newList = _chatIdsMap.value.map { (otherUid, chatId) ->
            val user = _usersMap.value[otherUid] ?: UserData(uid = otherUid, nombre = "Cargando...")
            val lastMsg = _lastMessagesMap.value[chatId]
            val unread = _unreadCountsMap.value[chatId] ?: 0
            ChatPreview(user, lastMsg, unread, chatId)
        }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }

        _chatList.value = newList
        _totalUnreadCount.value = newList.sumOf { it.unreadCount }
    }

    fun sendMessage(
        chatId: String,
        text: String,
        imageUrl: String? = null,
        videoUrl: String? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToName: String? = null,
        type: String = "text",
        metadata: Map<String, String> = emptyMap(),
        isSystem: Boolean = false
    ) {
        if (_isBlocked.value && !isSystem) return

        // Sin conexión: encolar el mensaje de texto localmente para enviarlo después.
        if (!isSystem && type == "text" && !ConnectivityObserver.isOnline.value) {
            queueOfflineMessage(chatId, text, imageUrl, videoUrl, fileUrl, fileName, replyToId, replyToText, replyToName, type, metadata)
            return
        }

        performSend(chatId, text, imageUrl, videoUrl, fileUrl, fileName, replyToId, replyToText, replyToName, type, metadata, isSystem)
    }

    private fun performSend(
        chatId: String,
        text: String,
        imageUrl: String? = null,
        videoUrl: String? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToName: String? = null,
        type: String = "text",
        metadata: Map<String, String> = emptyMap(),
        isSystem: Boolean = false,
        timestampOverride: Long? = null
    ) {
        val myUid = auth.currentUser?.uid ?: return
        val msgRef = database.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return
        
        val message = ChatMessage(
            id = msgId,
            senderId = if (isSystem) "system" else myUid,
            text = text,
            timestamp = timestampOverride ?: System.currentTimeMillis(),
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            fileUrl = fileUrl,
            fileName = fileName,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToName = replyToName,
            type = type,
            metadata = metadata,
            readBy = if (isSystem) emptyMap() else mapOf(myUid to true)
        )
        msgRef.setValue(message)

        if (!isSystem) {
            val otherUid = chatId.split("_").firstOrNull { it != myUid } ?:
                         chatId.split("_").lastOrNull { it != myUid } ?: ""

            if (otherUid.isNotEmpty()) {
                db.collection("usuarios").whereEqualTo("uid", otherUid).get().addOnSuccessListener { query ->
                    if (query.isEmpty) return@addOnSuccessListener
                    val recipientDoc = query.documents.firstOrNull { it.getBoolean("completado") == true || !it.getString("rut").isNullOrBlank() }
                        ?: query.documents.first()
                    val notifRef = recipientDoc.reference.collection("notificaciones").document()
                    val previewText = when {
                        text.isNotBlank() -> text.take(50)
                        imageUrl != null -> "📷 Imagen"
                        videoUrl != null -> "🎬 Video"
                        fileUrl != null -> "📄 ${fileName ?: "Documento"}"
                        else -> "Nuevo mensaje"
                    }
                    db.collection("usuarios").whereEqualTo("uid", myUid).get().addOnSuccessListener { me ->
                        val myProfile = if (!me.isEmpty) me.documents[0].toObject(UserData::class.java) else null
                        val myName = myProfile?.nombre ?: myProfile?.correo?.substringBefore("@") ?: "Usuario"
                        val myPhoto = myProfile?.fotoUrl ?: ""
                        val chatName = _usersMap.value[otherUid]?.nombre ?: "Chat"
                        val mediaPayload = when {
                            imageUrl != null -> mapOf("mediaTipo" to "image", "mediaUrl" to imageUrl)
                            videoUrl != null -> mapOf("mediaTipo" to "video", "mediaUrl" to videoUrl)
                            fileUrl != null -> mapOf("mediaTipo" to "file", "mediaUrl" to (fileUrl ?: ""))
                            else -> emptyMap()
                        }
                        val notification = com.example.cuidaleapp.models.Notification(
                            id = notifRef.id,
                            tipo = "nuevo_mensaje",
                            emisor = myUid,
                            receptor = otherUid,
                            timestamp = System.currentTimeMillis(),
                            leida = false,
                            data = mapOf(
                                "chatId" to chatId,
                                "text" to previewText,
                                "emisorNombre" to myName,
                                "emisorFoto" to myPhoto,
                                "chatNombre" to chatName,
                                "emisor" to myUid
                            ) + mediaPayload
                        )
                        notifRef.set(notification)
                    }
                }
            }
        }
    }

    /**
     * Envía un lote de multimedia (imágenes/videos/documentos) como UN solo
     * mensaje agrupado (estilo WhatsApp). Cada elemento queda en metadata["mediaBatch"]
     * como JSON: [{"kind":"image|video|file","url":"...","name":"..."}].
     */
    fun sendMediaBatch(
        chatId: String,
        media: List<Triple<String, String, String>>
    ) {
        val myUid = auth.currentUser?.uid ?: return
        if (media.isEmpty()) return

        val msgRef = database.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return

        val batchJson = org.json.JSONArray().apply {
            media.forEach { (kind, url, name) ->
                put(org.json.JSONObject().apply {
                    put("kind", kind)
                    put("url", url)
                    put("name", name)
                })
            }
        }.toString()

        val message = ChatMessage(
            id = msgId,
            senderId = myUid,
            text = "",
            timestamp = System.currentTimeMillis(),
            type = "media_group",
            metadata = mapOf(
                "mediaBatch" to batchJson,
                "count" to media.size.toString()
            ),
            readBy = mapOf(myUid to true)
        )
        msgRef.setValue(message)

        val otherUid = chatId.split("_").firstOrNull { it != myUid } ?:
                     chatId.split("_").lastOrNull { it != myUid } ?: ""
        if (otherUid.isNotBlank()) {
            db.collection("usuarios").whereEqualTo("uid", otherUid).get().addOnSuccessListener { query ->
                if (query.isEmpty) return@addOnSuccessListener
                val recipientDoc = query.documents.firstOrNull { it.getBoolean("completado") == true || !it.getString("rut").isNullOrBlank() }
                    ?: query.documents.first()
                val notifRef = recipientDoc.reference.collection("notificaciones").document()
                val images = media.count { it.first == "image" }
                val videos = media.count { it.first == "video" }
                val files = media.count { it.first == "file" }
                val previewText = buildString {
                    if (images > 0) append("📷 $images ${if (images == 1) "imagen" else "imágenes"}")
                    if (videos > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("🎬 $videos ${if (videos == 1) "video" else "videos"}")
                    }
                    if (files > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("📄 $files ${if (files == 1) "documento" else "documentos"}")
                    }
                    if (isEmpty()) append("Multimedia")
                }
                db.collection("usuarios").whereEqualTo("uid", myUid).get().addOnSuccessListener { me ->
                    val myProfile = if (!me.isEmpty) me.documents[0].toObject(UserData::class.java) else null
                    val myName = myProfile?.nombre ?: myProfile?.correo?.substringBefore("@") ?: "Usuario"
                    val myPhoto = myProfile?.fotoUrl ?: ""
                    val chatName = _usersMap.value[otherUid]?.nombre ?: "Chat"
                    val notification = com.example.cuidaleapp.models.Notification(
                        id = notifRef.id,
                        tipo = "nuevo_mensaje",
                        emisor = myUid,
                        receptor = otherUid,
                        timestamp = System.currentTimeMillis(),
                        leida = false,
                        data = mapOf(
                            "chatId" to chatId,
                            "text" to previewText,
                            "emisorNombre" to myName,
                            "emisorFoto" to myPhoto,
                            "chatNombre" to chatName,
                            "emisor" to myUid,
                            "mediaTipo" to "image",
                            "mediaUrl" to (media.firstOrNull { it.first == "image" }?.second ?: media.first().second)
                        )
                    )
                    notifRef.set(notification)
                }
            }
        }
    }

    private fun queueOfflineMessage(
        chatId: String,
        text: String,
        imageUrl: String? = null,
        videoUrl: String? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToName: String? = null,
        type: String = "text",
        metadata: Map<String, String> = emptyMap()
    ) {
        val myUid = auth.currentUser?.uid ?: return
        val id = "off_${System.currentTimeMillis()}_${(0 until 100000).random()}"
        val msg = com.example.cuidaleapp.utils.OutboxMessage(
            id = id,
            chatId = chatId,
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            fileUrl = fileUrl,
            fileName = fileName,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToName = replyToName,
            type = type,
            metadata = metadata,
            timestamp = System.currentTimeMillis()
        )
        val updated = (_outbox.value + msg).sortedBy { it.timestamp }
        _outbox.value = updated
        chatCache.saveOutbox(myUid, chatId, updated)

        val localMsg = ChatMessage(
            id = id,
            senderId = myUid,
            text = text,
            timestamp = msg.timestamp,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            fileUrl = fileUrl,
            fileName = fileName,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToName = replyToName,
            type = type,
            metadata = metadata + mapOf("pending_send" to "true"),
            readBy = mapOf(myUid to true)
        )
        _messages.value = (_messages.value + localMsg).sortedBy { it.timestamp }
    }

    fun sendOutboxMessage(msg: com.example.cuidaleapp.utils.OutboxMessage) {
        val myUid = auth.currentUser?.uid ?: return
        performSend(
            chatId = msg.chatId,
            text = msg.text,
            imageUrl = msg.imageUrl,
            videoUrl = msg.videoUrl,
            fileUrl = msg.fileUrl,
            fileName = msg.fileName,
            replyToId = msg.replyToId,
            replyToText = msg.replyToText,
            replyToName = msg.replyToName,
            type = msg.type,
            metadata = msg.metadata,
            isSystem = false,
            // Conserva el timestamp original (momento en que se escribió) para
            // no alterar el orden ni el día en el historial.
            timestampOverride = msg.timestamp
        )
        // Elimina la copia local pendiente para evitar duplicados visuales.
        _messages.value = _messages.value.filterNot { it.id == msg.id }
        _outbox.value = _outbox.value.filterNot { it.id == msg.id }
        chatCache.saveOutbox(myUid, msg.chatId, _outbox.value.filter { it.chatId == msg.chatId })
    }

    fun deleteOutboxMessage(id: String, chatId: String) {
        val myUid = auth.currentUser?.uid ?: return
        _outbox.value = _outbox.value.filterNot { it.id == id }
        chatCache.saveOutbox(myUid, chatId, _outbox.value.filter { it.chatId == chatId })
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    fun archiveOldMessages(chatId: String) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        
        database.child("messages").child(chatId)
            .orderByChild("timestamp")
            .endAt(sevenDaysAgo.toDouble())
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val batch = db.batch()
                    val messagesToArchive = mutableListOf<DataSnapshot>()
                    
                    snapshot.children.forEach { child ->
                        val msg = child.getValue(ChatMessage::class.java) ?: return@forEach
                        if (!msg.pinned) {
                            val docRef = db.collection("archived_messages").document(chatId)
                                .collection("history").document(msg.id)
                            batch.set(docRef, msg)
                            messagesToArchive.add(child)
                        }
                    }
                    
                    if (messagesToArchive.isNotEmpty()) {
                        batch.commit().addOnSuccessListener {
                            messagesToArchive.forEach { it.ref.removeValue() }
                        }
                    }
                }
            }
    }

    fun editMessage(chatId: String, messageId: String, newText: String) {
        val updates = mapOf("text" to newText, "edited" to true)
        database.child("messages").child(chatId).child(messageId).updateChildren(updates)
    }

    fun deleteMessage(chatId: String, messageId: String) {
        val updates = mapOf("text" to "Este mensaje fue eliminado", "deleted" to true)
        database.child("messages").child(chatId).child(messageId).updateChildren(updates)

        // Actualización optimista local: refleja el borrado de inmediato en la UI
        // y en la caché offline (sin esperar el eco del servidor).
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(deleted = true, text = "Este mensaje fue eliminado") else msg
        }
        _pinnedMessages.value = _messages.value.filter { it.pinned && !it.deleted }
        auth.currentUser?.uid?.let { uid -> chatCache.saveMessages(uid, chatId, _messages.value) }
    }

    fun togglePin(chatId: String, messageId: String, currentPinned: Boolean, senderName: String) {
        database.child("messages").child(chatId).child(messageId).child("pinned").setValue(!currentPinned)
        val action = if (!currentPinned) "fijó" else "desfijó"
        sendMessage(chatId, "$senderName $action un mensaje", isSystem = true)
    }

    fun clearChatNotifications(chatId: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.collection("usuarios").whereEqualTo("uid", myUid).get().addOnSuccessListener { query ->
            if (query.isEmpty) return@addOnSuccessListener
            val myDoc = query.documents.firstOrNull { it.getBoolean("completado") == true || !it.getString("rut").isNullOrBlank() }
                ?: query.documents.first()
            myDoc.reference.collection("notificaciones")
                .whereEqualTo("tipo", "nuevo_mensaje")
                .whereEqualTo("data.chatId", chatId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = db.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
        }
    }

    fun markChatAsRead(chatId: String) {
        val myUid = auth.currentUser?.uid ?: return
        
        database.child("messages").child(chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { child ->
                    val msg = child.getValue(ChatMessage::class.java)
                    if (msg != null && msg.senderId != myUid && !msg.readBy.containsKey(myUid)) {
                        child.ref.child("readBy").child(myUid).setValue(true)
                    }
                }
                clearChatNotifications(chatId)
            }
    }

    private fun markAsRead(chatId: String, messageId: String) {
        val myUid = auth.currentUser?.uid ?: return
        database.child("messages").child(chatId).child(messageId).child("readBy").child(myUid).setValue(true)
    }

    override fun onCleared() {
        super.onCleared()
        removeActiveListener()
        chatsListener?.remove()
        blockingListener?.remove()
        userListeners.forEach { it.value.remove() }
        userListeners.clear()
        chatMessageListeners.forEach { (id, listener) ->
            database.child("messages").child(id).removeEventListener(listener)
        }
        chatTypingListeners.forEach { (id, listener) ->
            database.child("typing").child(id).removeEventListener(listener)
        }
        chatMessageListeners.clear()
        chatTypingListeners.clear()
    }
}
