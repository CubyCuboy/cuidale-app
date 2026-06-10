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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference
    private val db = FirebaseFirestore.getInstance()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _chatList = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chatList: StateFlow<List<ChatPreview>> = _chatList

    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount: StateFlow<Int> = _totalUnreadCount

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

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
        statusListener?.let { database.child("status").child(otherUid).removeEventListener(it) }
        
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _otherUserStatus.value = snapshot.child("estado").getValue(String::class.java) ?: "desconectado"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("status").child(otherUid).addValueEventListener(statusListener!!)
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

    fun uploadFile(chatId: String, fileUri: Uri, context: android.content.Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val fileSize = getFileSize(fileUri, context)
        if (fileSize > 25 * 1024 * 1024) {
            onError("El archivo excede el límite de 25MB")
            return
        }

        _isUploading.value = true
        val originalFileName = getFileName(fileUri, context)
        val fileNameInStorage = "chat_files/${chatId}/${System.currentTimeMillis()}_$originalFileName"
        val ref = storage.child(fileNameInStorage)
        
        ref.putFile(fileUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    _isUploading.value = false
                    val mimeType = context.contentResolver.getType(fileUri) ?: ""
                    when {
                        mimeType.startsWith("image") -> {
                            sendMessage(chatId, "", imageUrl = uri.toString())
                        }
                        mimeType.startsWith("video") -> {
                            sendMessage(chatId, "", videoUrl = uri.toString())
                        }
                        else -> {
                            sendMessage(chatId, "", fileUrl = uri.toString(), fileName = originalFileName)
                        }
                    }
                    onSuccess(uri.toString())
                }
            }
            .addOnFailureListener {
                _isUploading.value = false
                onError(it.message ?: "Error al subir archivo")
            }
    }

    private fun getFileSize(uri: Uri, context: android.content.Context): Long {
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    }

    private fun getFileName(uri: Uri, context: android.content.Context): String {
        var name = "archivo"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
        return name
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
        db.collection("usuarios").whereIn("uid", userIds).get().addOnSuccessListener { userDocs ->
            val usersMap = userDocs.toObjects(UserData::class.java).associateBy { it.uid }
            
            chatIds.forEachIndexed { index, chatId ->
                val otherUid = userIds[index]
                val otherUser = usersMap[otherUid] ?: UserData(uid = otherUid, nombre = "Usuario")
                
                if (chatMessageListeners.containsKey(chatId)) return@forEachIndexed
                
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
                        
                        val newPreview = ChatPreview(otherUser, lastMsg, unread, chatId)
                        val currentList = _chatList.value.toMutableList()
                        currentList.removeAll { it.chatId == chatId }
                        currentList.add(newPreview)
                        val sorted = currentList.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
                        _chatList.value = sorted
                        _totalUnreadCount.value = sorted.sumOf { it.unreadCount }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                chatMessageListeners[chatId] = listener
                database.child("messages").child(chatId).addValueEventListener(listener)
            }
        }
    }

    fun sendMessage(chatId: String, text: String, imageUrl: String? = null, videoUrl: String? = null, fileUrl: String? = null, fileName: String? = null, isSystem: Boolean = false) {
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
            readBy = if (isSystem) emptyMap() else mapOf(myUid to true)
        )
        msgRef.setValue(message)

        // Crear notificación si no es mensaje de sistema
        if (!isSystem) {
            val otherUid = chatId.split("_").firstOrNull { it != myUid } ?: 
                         chatId.split("_").lastOrNull { it != myUid } ?: ""
            
            if (otherUid.isNotEmpty()) {
                val notifRef = db.collection("usuarios").document(otherUid).collection("notificaciones").document()
                val notification = com.example.cuidaleapp.models.Notification(
                    id = notifRef.id,
                    tipo = "nuevo_mensaje",
                    emisor = myUid,
                    receptor = otherUid,
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("chatId" to chatId, "text" to text.take(50))
                )
                notifRef.set(notification)
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

    private fun markAsRead(chatId: String, messageId: String) {
        val myUid = auth.currentUser?.uid ?: return
        database.child("messages").child(chatId).child(messageId).child("readBy").child(myUid).setValue(true)
    }

    override fun onCleared() {
        super.onCleared()
        removeActiveListener()
        chatsListener?.remove()
        blockingListener?.remove()
        chatMessageListeners.forEach { (id, listener) ->
            database.child("messages").child(id).removeEventListener(listener)
        }
        chatMessageListeners.clear()
    }
}
