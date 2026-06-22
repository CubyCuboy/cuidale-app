package com.example.cuidaleapp.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
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
    
    val isOnline: StateFlow<Boolean> = ConnectivityObserver.isOnline

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _chatList = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chatList: StateFlow<List<ChatPreview>> = _chatList

    private val _usersMap = MutableStateFlow<Map<String, UserData>>(emptyMap())
    private val _lastMessagesMap = MutableStateFlow<Map<String, ChatMessage?>>(emptyMap())
    private val _unreadCountsMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _chatIdsMap = MutableStateFlow<Map<String, String>>(emptyMap()) // otherUid -> chatId

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

    private val _isOtherTyping = MutableStateFlow(false)
    val isOtherTyping: StateFlow<Boolean> = _isOtherTyping

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

    fun setUserStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val statusRef = database.child("status").child(uid)
        
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
        
        // Verificar si hay un vínculo aceptado antes de mostrar el estado
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
        
        blockingListener = db.collection("usuarios").document(otherUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val bloqueados = snapshot?.get("bloqueados") as? List<String> ?: emptyList()
                _isBlocked.value = bloqueados.contains(myUid)
            }
    }

    fun listenMessages(chatId: String) {
        if (currentChatId == chatId) return
        
        removeActiveListener()
        currentChatId = chatId

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { child ->
                    child.getValue(ChatMessage::class.java)?.let { list.add(it) }
                }
                val sortedList = list.sortedBy { it.timestamp }
                _messages.value = sortedList
                _pinnedMessages.value = sortedList.filter { it.pinned && !it.deleted }
                
                val myUid = auth.currentUser?.uid ?: return
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

    fun listenChats() {
        val myUid = auth.currentUser?.uid ?: return
        if (chatsListener != null) return // Already listening

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
                    _chatList.value = emptyList()
                    _totalUnreadCount.value = 0
                    return@addSnapshotListener
                }

                loadChatPreviews(userIds, chatIds, myUid)
            }
    }

    private fun loadChatPreviews(userIds: List<String>, chatIds: List<String>, myUid: String) {
        userIds.forEachIndexed { index, otherUid ->
            val chatId = chatIds[index]
            _chatIdsMap.value = _chatIdsMap.value + (otherUid to chatId)

            // 1. User Listener
            if (!userListeners.containsKey(otherUid)) {
                val uListener = db.collection("usuarios").document(otherUid)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                        val otherUser = snapshot.toObject(UserData::class.java)?.copy(uid = otherUid) ?: return@addSnapshotListener
                        
                        _usersMap.value = _usersMap.value + (otherUid to otherUser)
                        updateChatList()

                        if (otherUid == currentChatId?.split("_")?.find { it != myUid }) {
                            val bloqueados = snapshot.get("bloqueados") as? List<String> ?: emptyList()
                            _isBlocked.value = bloqueados.contains(myUid)
                        }
                    }
                userListeners[otherUid] = uListener
            }

            // 2. Message Listener for last message and unread count
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
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                chatMessageListeners[chatId] = listener
                database.child("messages").child(chatId).addValueEventListener(listener)
            }
            
            // 3. Typing Listener
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

        val myUid = auth.currentUser?.uid ?: return
        val msgRef = database.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return
        
        val message = ChatMessage(
            id = msgId,
            senderId = if (isSystem) "system" else myUid,
            text = text,
            timestamp = System.currentTimeMillis(),
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

        // Lanzar notificación si no es mensaje de sistema
        if (!isSystem) {
            val otherUid = chatId.split("_").firstOrNull { it != myUid } ?: 
                         chatId.split("_").lastOrNull { it != myUid } ?: ""
            
            if (otherUid.isNotEmpty()) {
                // Verificar si el usuario está conectado antes de crear el documento de notificación
                // Esto ahorra ejecuciones de Cloud Functions si el usuario ya está en la app
                database.child("status").child(otherUid).child("estado").get().addOnSuccessListener { snapshot ->
                    val status = snapshot.getValue(String::class.java)
                    if (status != "en línea") {
                        val notifRef = db.collection("usuarios").document(otherUid).collection("notificaciones").document()
                        val notification = com.example.cuidaleapp.models.Notification(
                            id = notifRef.id,
                            tipo = "nuevo_mensaje",
                            emisor = myUid,
                            receptor = otherUid,
                            timestamp = System.currentTimeMillis(),
                            leida = false,
                            data = mapOf("chatId" to chatId, "text" to text.take(50))
                        )
                        notifRef.set(notification)
                    }
                }
            }
        }
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
    }

    fun togglePin(chatId: String, messageId: String, currentPinned: Boolean, senderName: String) {
        database.child("messages").child(chatId).child(messageId).child("pinned").setValue(!currentPinned)
        val action = if (!currentPinned) "fijó" else "desfijó"
        sendMessage(chatId, "$senderName $action un mensaje", isSystem = true)
    }

    fun clearChatNotifications(chatId: String) {
        val myUid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(myUid).collection("notificaciones")
            .whereEqualTo("tipo", "nuevo_mensaje")
            .whereEqualTo("data.chatId", chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
            }
    }

    fun markChatAsRead(chatId: String) {
        val myUid = auth.currentUser?.uid ?: return
        
        // Operación optimizada: solo buscar mensajes que NO he leído
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
