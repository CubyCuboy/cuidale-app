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

    data class ChatPreview(
        val otherUser: UserData,
        val lastMessage: ChatMessage?,
        val unreadCount: Int,
        val chatId: String
    )

    private var messagesListener: ValueEventListener? = null
    private var statusListener: ValueEventListener? = null
    private var blockingListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentChatId: String? = null

    fun setUserStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val status = if (isOnline) "en línea" else "desconectado"
        database.child("status").child(uid).setValue(
            mapOf(
                "estado" to status,
                "lastSeen" to ServerValue.TIMESTAMP
            )
        )
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
        
        // Escuchar si EL OTRO USUARIO ME bloqueó a MÍ
        blockingListener = db.collection("usuarios").document(otherUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val noDeseados = snapshot?.get("no_deseados") as? List<String> ?: emptyList()
                _isBlocked.value = noDeseados.contains(myUid)
            }
    }

    fun listenMessages(chatId: String) {
        if (currentChatId == chatId) return
        
        removeListener()
        currentChatId = chatId

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { child ->
                    child.getValue(ChatMessage::class.java)?.let { list.add(it) }
                }
                val sortedList = list.sortedBy { it.timestamp }
                _messages.value = sortedList
                _pinnedMessages.value = sortedList.filter { it.isPinned && !it.isDeleted }
                
                // Marcar como leído
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

    private fun removeListener() {
        currentChatId?.let { chatId ->
            messagesListener?.let { listener ->
                database.child("messages").child(chatId).removeEventListener(listener)
            }
        }
        messagesListener = null
    }

    fun listenChats() {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("vinculos")
            .whereArrayContains("uids", myUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ChatViewModel", "Error en listenChats: ${e.message}")
                    return@addSnapshotListener
                }

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
                
                database.child("messages").child(chatId).limitToLast(1)
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val lastMsg = snapshot.children.firstOrNull()?.getValue(ChatMessage::class.java)
                            
                            database.child("messages").child(chatId).get().addOnSuccessListener { allMsgs ->
                                var unread = 0
                                allMsgs.children.forEach { m ->
                                    val msg = m.getValue(ChatMessage::class.java)
                                    if (msg != null && msg.senderId != myUid && !msg.readBy.containsKey(myUid)) {
                                        unread++
                                    }
                                }
                                
                                val newPreview = ChatPreview(otherUser, lastMsg, unread, chatId)
                                val currentList = _chatList.value.toMutableList()
                                currentList.removeAll { it.chatId == chatId }
                                currentList.add(newPreview)
                                _chatList.value = currentList.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
        }
    }

    fun sendMessage(chatId: String, text: String, imageUrl: String? = null, videoUrl: String? = null, fileUrl: String? = null, fileName: String? = null, isSystem: Boolean = false) {
        if (_isBlocked.value && !isSystem) return // No enviar si estamos bloqueados

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
    }

    /**
     * Mueve los mensajes antiguos (más de 7 días) de Realtime Database a Firestore
     * para evitar saturar la base de datos en tiempo real.
     */
    fun archiveOldMessages(chatId: String) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        
        database.child("messages").child(chatId)
            .orderByChild("timestamp")
            .endAt(sevenDaysAgo.toDouble())
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val batch = db.batch()
                    snapshot.children.forEach { child ->
                        val msg = child.getValue(ChatMessage::class.java) ?: return@forEach
                        val docRef = db.collection("archived_messages").document(chatId)
                            .collection("history").document(msg.id)
                        batch.set(docRef, msg)
                    }
                    
                    batch.commit().addOnSuccessListener {
                        // Una vez guardados en Firestore, los borramos de RTDB
                        snapshot.children.forEach { it.ref.removeValue() }
                        Log.d("ChatViewModel", "Mensajes antiguos archivados correctamente")
                    }
                }
            }
    }

    fun editMessage(chatId: String, messageId: String, newText: String) {
        val updates = mapOf(
            "text" to newText,
            "isEdited" to true
        )
        database.child("messages").child(chatId).child(messageId).updateChildren(updates)
    }

    fun deleteMessage(chatId: String, messageId: String) {
        val updates = mapOf(
            "text" to "Este mensaje fue eliminado",
            "isDeleted" to true
        )
        database.child("messages").child(chatId).child(messageId).updateChildren(updates)
    }

    fun togglePin(chatId: String, messageId: String, currentPinned: Boolean, senderName: String) {
        database.child("messages").child(chatId).child(messageId).child("isPinned").setValue(!currentPinned)
        val action = if (!currentPinned) "fijó" else "desfijó"
        sendMessage(chatId, "$senderName $action un mensaje", isSystem = true)
    }

    private fun markAsRead(chatId: String, messageId: String) {
        val myUid = auth.currentUser?.uid ?: return
        database.child("messages").child(chatId).child(messageId).child("readBy").child(myUid).setValue(true)
    }

    override fun onCleared() {
        super.onCleared()
        removeListener()
        blockingListener?.remove()
    }
}
