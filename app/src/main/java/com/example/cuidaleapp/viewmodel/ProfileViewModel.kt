package com.example.cuidaleapp.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.utils.ImageUtils
import com.example.cuidaleapp.utils.ProfileImageCacheManager
import com.example.cuidaleapp.models.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://cuidale-app.firebasestorage.app")
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    private val _remoteState = MutableStateFlow(ProfileRemoteState())
    val remoteState: StateFlow<ProfileRemoteState> = _remoteState.asStateFlow()

    private val _draftState = MutableStateFlow(EditProfileDraftState())
    val draftState: StateFlow<EditProfileDraftState> = _draftState.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _galleryState = MutableStateFlow(GalleryViewerState())
    val galleryState: StateFlow<GalleryViewerState> = _galleryState.asStateFlow()

    private val _otherProfileState = MutableStateFlow(ProfileRemoteState())
    val otherProfileState: StateFlow<ProfileRemoteState> = _otherProfileState.asStateFlow()

    private var verificationId: String? = null

    init {
        loadUserProfile()
    }

    private fun UserData.toProfileImage(): ProfileImage? {
        profilePhoto?.let { map ->
            val remoteUrl = map["remoteUrl"] as? String
            if (!remoteUrl.isNullOrBlank()) {
                val sourceTypeStr = map["sourceType"] as? String ?: "REMOTE"
                val sourceType = try { ImageSourceType.valueOf(sourceTypeStr) } catch (e: Exception) { ImageSourceType.REMOTE }
                
                return ProfileImage(
                    id = map["id"] as? String ?: "profile_main",
                    remoteUrl = remoteUrl,
                    localUri = null,
                    cacheKey = map["cacheKey"] as? String ?: remoteUrl,
                    storagePath = map["storagePath"] as? String ?: "",
                    isPrivate = map["isPrivate"] as? Boolean ?: false,
                    isProfilePhoto = true,
                    sortOrder = (map["sortOrder"] as? Number)?.toInt() ?: 0,
                    sourceType = sourceType,
                    uploadState = ImageOperationState.Idle
                )
            }
        }

        if (fotoUrl.isBlank()) return null

        return if (fotoUrl.startsWith("res:")) {
            ProfileImage(
                id = fotoUrl,
                remoteUrl = null,
                localUri = null,
                cacheKey = fotoUrl,
                storagePath = "",
                isPrivate = false,
                isProfilePhoto = true,
                sortOrder = 0,
                sourceType = ImageSourceType.DEFAULT_AVATAR,
                uploadState = ImageOperationState.Idle
            )
        } else {
            ProfileImage(
                id = "profile_main",
                remoteUrl = fotoUrl,
                localUri = null,
                cacheKey = fotoUrl,
                storagePath = "perfiles/$uid/foto_perfil.jpg",
                isPrivate = false,
                isProfilePhoto = true,
                sortOrder = 0,
                sourceType = ImageSourceType.REMOTE,
                uploadState = ImageOperationState.Idle
            )
        }
    }

    private fun UserData.toDomain(isOwner: Boolean, canSeeContactInfo: Boolean, canSeePrivatePhotos: Boolean): ProfileDomainModel {
        val contacts = mutableListOf<ContactMethod>()

        if (correo.isNotBlank() && (isOwner || (canSeeContactInfo && mostrarCorreo))) {
            contacts.add(
                ContactMethod(
                    id = "primary_email",
                    type = ContactType.EMAIL,
                    value = correo,
                    visibility = if (mostrarCorreo) VisibilityPolicy.CONTACTS_ONLY else VisibilityPolicy.PRIVATE,
                    isPrimary = true,
                    isVerified = verificado,
                    label = "Correo de registro"
                )
            )
        }

        if (correoAlternativo.isNotBlank() && (isOwner || canSeeContactInfo)) {
            contacts.add(
                ContactMethod(
                    id = "alt_email",
                    type = ContactType.EMAIL,
                    value = correoAlternativo,
                    visibility = VisibilityPolicy.CONTACTS_ONLY,
                    isPrimary = false,
                    isVerified = false,
                    label = "Correo alternativo"
                )
            )
        }

        if (telefono.isNotBlank() && (isOwner || (canSeeContactInfo && mostrarTelefono))) {
            contacts.add(
                ContactMethod(
                    id = "primary_phone",
                    type = ContactType.PHONE,
                    value = telefono,
                    visibility = if (mostrarTelefono) VisibilityPolicy.CONTACTS_ONLY else VisibilityPolicy.PRIVATE,
                    isPrimary = true,
                    isVerified = telefonoVerificado,
                    label = "Teléfono"
                )
            )
        }

        val socialLinks = redesSociales.mapNotNull { social ->
            val visibility = runCatching {
                VisibilityPolicy.valueOf(social.visibility)
            }.getOrDefault(VisibilityPolicy.CONTACTS_ONLY)

            if (!isOwner && !canSeeContactInfo && visibility != VisibilityPolicy.PUBLIC) {
                null
            } else {
                SocialLink(
                    platform = SocialPlatform.entries.find {
                        it.displayName.equals(social.platform, ignoreCase = true)
                    } ?: SocialPlatform.OTHER,
                    customPlatform = social.customPlatform,
                    link = social.link,
                    visibility = visibility
                )
            }
        }

        val gallery = fotos.mapIndexedNotNull { index, photo ->
            if (!canSeePrivatePhotos && photo.esPrivada) {
                null
            } else {
                val sourceType = try { ImageSourceType.valueOf(photo.sourceType) } catch (e: Exception) { ImageSourceType.REMOTE }
                ProfileImage(
                    id = if (photo.id.isNotBlank()) photo.id else "${photo.url}_${index}",
                    remoteUrl = if (photo.url.startsWith("res:")) null else photo.url,
                    localUri = null,
                    cacheKey = if (photo.cacheKey.isNotBlank()) photo.cacheKey else photo.url,
                    storagePath = photo.storagePath,
                    isPrivate = photo.esPrivada,
                    isProfilePhoto = false,
                    sortOrder = index,
                    sourceType = if (photo.url.startsWith("res:")) ImageSourceType.DEFAULT_AVATAR else sourceType,
                    uploadState = ImageOperationState.Idle
                )
            }
        }

        return ProfileDomainModel(
            uid = uid,
            nombre = nombre,
            nombre_s = nombre_s,
            apellido_pa = apellido_pa,
            apellido_ma = apellido_ma,
            rut = if (isOwner || mostrarRut) rut else "",
            rol = rol,
            verificado = verificado,
            telefono = if (isOwner || (canSeeContactInfo && mostrarTelefono)) telefono else "",
            telefonoVerificado = telefonoVerificado,
            correo = if (isOwner || (canSeeContactInfo && mostrarCorreo)) correo else "",
            correoAlternativo = if (isOwner || canSeeContactInfo) correoAlternativo else "",
            mostrarCorreo = mostrarCorreo,
            mostrarTelefono = mostrarTelefono,
            mostrarRut = mostrarRut,
            comuna = comuna,
            region = region,
            id_region = id_region,
            privacidadVinculos = privacidadVinculos,
            descripcion = descripcion,
            profilePhoto = toProfileImage(),
            galleryPhotos = gallery,
            socialLinks = socialLinks,
            contactMethods = contacts
        )
    }

    private suspend fun loadUserProfileInternal() {
        val uid = auth.currentUser?.uid ?: return
        _remoteState.update { it.copy(isLoading = true, error = null) }

        try {
            val doc = db.collection("usuarios").document(uid).get().await()
            val user = doc.toObject(UserData::class.java)?.copy(uid = uid)

            if (user != null) {
                val domainModel = user.toDomain(
                    isOwner = true,
                    canSeeContactInfo = true,
                    canSeePrivatePhotos = true
                )

                _remoteState.value = ProfileRemoteState(
                    data = domainModel,
                    permissions = ViewerPermissions(
                        canSeePrivatePhotos = true,
                        canSeeContactInfo = true,
                        isOwner = true
                    ),
                    isLoading = false,
                    error = null
                )

                saveUserToCache(user)
                prefetchProfileImages(domainModel)
            } else {
                _remoteState.update { it.copy(isLoading = false, error = "No se encontró el usuario") }
            }
        } catch (e: Exception) {
            _remoteState.update {
                it.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Error al cargar perfil"
                )
            }
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            loadUserProfileInternal()
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                loadUserProfileInternal()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun startEditing() {
        if (_draftState.value.isDirty) return
        val current = _remoteState.value.data ?: return
        _draftState.value = EditProfileDraftState(
            uid = current.uid,
            nombre = current.nombre,
            nombre_s = current.nombre_s,
            apellido_pa = current.apellido_pa,
            apellido_ma = current.apellido_ma,
            rut = current.rut,
            rol = current.rol,
            verificado = current.verificado,
            telefono = current.telefono,
            telefonoVerificado = current.telefonoVerificado,
            correo = current.correo,
            correoAlternativo = current.correoAlternativo,
            mostrarCorreo = current.mostrarCorreo,
            mostrarTelefono = current.mostrarTelefono,
            mostrarRut = current.mostrarRut,
            comuna = current.comuna,
            region = current.region,
            id_region = current.id_region,
            privacidadVinculos = current.privacidadVinculos,
            descripcion = current.descripcion,
            profilePhoto = current.profilePhoto,
            galleryPhotos = current.galleryPhotos,
            socialLinks = current.socialLinks,
            contactMethods = current.contactMethods,
            isDirty = false,
            isSaving = false,
            error = null
        )
    }

    fun updateDraftField(update: (EditProfileDraftState) -> EditProfileDraftState) {
        _draftState.update { update(it).copy(isDirty = true) }
    }

    fun setDefaultProfileAvatar(drawableName: String) {
        val uid = auth.currentUser?.uid ?: return
        val avatarValue = "res:$drawableName"

        _draftState.update {
            it.copy(
                profilePhoto = ProfileImage(
                    id = avatarValue,
                    remoteUrl = null,
                    localUri = null,
                    cacheKey = avatarValue,
                    storagePath = "",
                    isPrivate = false,
                    isProfilePhoto = true,
                    sortOrder = 0,
                    sourceType = ImageSourceType.DEFAULT_AVATAR,
                    uploadState = ImageOperationState.Success
                ),
                isDirty = true
            )
        }

        viewModelScope.launch {
            try {
                db.collection("usuarios").document(uid).update("fotoUrl", avatarValue).await()
                loadUserProfileInternal()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al guardar avatar por defecto")
            }
        }
    }

    fun uploadProfilePhoto(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return

        val tempImage = ProfileImage(
            id = "pending_profile",
            localUri = uri,
            remoteUrl = null,
            storagePath = "perfiles/$uid/foto_perfil.jpg",
            sourceType = ImageSourceType.LOCAL_PENDING,
            cacheKey = "pending_profile",
            isPrivate = false,
            isProfilePhoto = true,
            uploadState = ImageOperationState.Processing(0f)
        )

        _draftState.update {
            it.copy(
                profilePhoto = tempImage,
                isDirty = true
            )
        }

        viewModelScope.launch {
            try {
                ImageUtils.validateImage(getApplication<Application>().applicationContext, uri).getOrThrow()
                val bytes = ImageUtils.getCompressedImage(getApplication<Application>().applicationContext, uri)
                    ?: throw Exception("No se pudo comprimir la imagen")

                val storageRef = storage.reference.child("perfiles/$uid/foto_perfil.jpg")
                val metadata = storageMetadata { contentType = "image/jpeg" }

                storageRef.putBytes(bytes, metadata)
                    .addOnProgressListener { taskSnapshot ->
                        val progress = if (taskSnapshot.totalByteCount > 0) {
                            taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
                        } else {
                            0f
                        }

                        _draftState.update { draft ->
                            draft.copy(
                                profilePhoto = draft.profilePhoto?.copy(
                                    uploadState = ImageOperationState.Processing(progress)
                                ),
                                isDirty = true
                            )
                        }
                    }
                    .await()

                val url = storageRef.downloadUrl.await().toString()
                
                val photoMap = mapOf(
                    "id" to "profile_main",
                    "remoteUrl" to url,
                    "cacheKey" to url,
                    "storagePath" to "perfiles/$uid/foto_perfil.jpg",
                    "isPrivate" to false,
                    "isProfilePhoto" to true,
                    "sortOrder" to 0,
                    "sourceType" to ImageSourceType.REMOTE.name
                )
                
                db.collection("usuarios").document(uid).update(
                    "fotoUrl", url,
                    "profilePhoto", photoMap
                ).await()

                val updatedProfilePhoto = draftState.value.profilePhoto?.copy(
                    remoteUrl = url,
                    localUri = null,
                    cacheKey = url,
                    sourceType = ImageSourceType.REMOTE,
                    uploadState = ImageOperationState.Success
                )

                _draftState.update { draft ->
                    draft.copy(
                        profilePhoto = updatedProfilePhoto,
                        isDirty = true
                    )
                }

                _remoteState.update { remote ->
                    remote.copy(
                        data = remote.data?.copy(profilePhoto = updatedProfilePhoto)
                    )
                }
            } catch (e: Exception) {
                _draftState.update { draft ->
                    draft.copy(
                        profilePhoto = draft.profilePhoto?.copy(
                            uploadState = ImageOperationState.Error(e.localizedMessage ?: "Error al subir foto de perfil")
                        ),
                        isDirty = true
                    )
                }
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al subir foto de perfil")
            }
        }
    }

    fun uploadGalleryPhoto(uri: Uri, isPrivate: Boolean = false) {
        val uid = auth.currentUser?.uid ?: return
        val currentCount = _draftState.value.galleryPhotos.size

        if (currentCount >= 6) {
            _uiState.value = ProfileUiState.Error("Máximo 6 fotos permitidas")
            return
        }

        val tempId = UUID.randomUUID().toString()
        val tempImage = ProfileImage(
            id = tempId,
            localUri = uri,
            remoteUrl = null,
            storagePath = "perfiles/$uid/fotos/$tempId.jpg",
            sourceType = ImageSourceType.LOCAL_PENDING,
            cacheKey = tempId,
            isPrivate = isPrivate,
            isProfilePhoto = false,
            sortOrder = currentCount,
            uploadState = ImageOperationState.Processing(0f)
        )

        _draftState.update {
            it.copy(
                galleryPhotos = it.galleryPhotos + tempImage,
                isDirty = true
            )
        }

        viewModelScope.launch {
            try {
                ImageUtils.validateImage(getApplication<Application>().applicationContext, uri).getOrThrow()
                val bytes = ImageUtils.getCompressedImage(getApplication<Application>().applicationContext, uri)
                    ?: throw Exception("No se pudo comprimir la imagen")

                val storageRef = storage.reference.child("perfiles/$uid/fotos/$tempId.jpg")
                val metadata = storageMetadata { contentType = "image/jpeg" }

                storageRef.putBytes(bytes, metadata)
                    .addOnProgressListener { taskSnapshot ->
                        val progress = if (taskSnapshot.totalByteCount > 0) {
                            taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
                        } else {
                            0f
                        }

                        _draftState.update { draft ->
                            draft.copy(
                                galleryPhotos = draft.galleryPhotos.map {
                                    if (it.id == tempId) {
                                        it.copy(uploadState = ImageOperationState.Processing(progress))
                                    } else {
                                        it
                                    }
                                },
                                isDirty = true
                            )
                        }
                    }
                    .await()

                val url = storageRef.downloadUrl.await().toString()

                val currentRemotePhotos = getCurrentUserRaw()?.fotos ?: emptyList()
                val newPhoto = UserPhoto(
                    id = tempId,
                    url = url,
                    esPrivada = isPrivate,
                    storagePath = "perfiles/$uid/fotos/$tempId.jpg",
                    sourceType = ImageSourceType.REMOTE.name,
                    cacheKey = url
                )
                val updatedPhotos = currentRemotePhotos + newPhoto

                db.collection("usuarios").document(uid).update("fotos", updatedPhotos).await()

                var finalPhoto: ProfileImage? = null
                _draftState.update { draft ->
                    val updatedGallery = draft.galleryPhotos.map {
                        if (it.id == tempId) {
                            it.copy(
                                remoteUrl = url,
                                localUri = null,
                                cacheKey = url,
                                storagePath = "perfiles/$uid/fotos/$tempId.jpg",
                                sourceType = ImageSourceType.REMOTE,
                                uploadState = ImageOperationState.Success
                            ).also { photo -> finalPhoto = photo }
                        } else {
                            it
                        }
                    }
                    draft.copy(
                        galleryPhotos = updatedGallery,
                        isDirty = true
                    )
                }

                _remoteState.update { remote ->
                    remote.copy(
                        data = remote.data?.copy(
                            galleryPhotos = remote.data.galleryPhotos + listOfNotNull(finalPhoto)
                        )
                    )
                }
            } catch (e: Exception) {
                _draftState.update { draft ->
                    draft.copy(
                        galleryPhotos = draft.galleryPhotos.map {
                            if (it.id == tempId) {
                                it.copy(uploadState = ImageOperationState.Error(e.localizedMessage ?: "Error al subir foto"))
                            } else {
                                it
                            }
                        },
                        isDirty = true
                    )
                }
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al subir foto")
            }
        }
    }

    fun deleteGalleryPhoto(image: ProfileImage) {
        val uid = auth.currentUser?.uid ?: return

        _draftState.update { draft ->
            draft.copy(
                galleryPhotos = draft.galleryPhotos.map {
                    if (it.id == image.id) it.copy(uploadState = ImageOperationState.Deleting) else it
                },
                isDirty = true
            )
        }

        viewModelScope.launch {
            try {
                val currentUser = getCurrentUserRaw() ?: throw Exception("No se pudo cargar el usuario")
                val updatedPhotos = currentUser.fotos.filterNot { it.url == image.remoteUrl }

                db.collection("usuarios").document(uid).update("fotos", updatedPhotos).await()

                image.remoteUrl?.let { url ->
                    try {
                        storage.getReferenceFromUrl(url).delete().await()
                    } catch (_: Exception) {
                    }
                }

                try {
                    ProfileImageCacheManager.evictFromCache(getApplication<Application>().applicationContext, image.cacheKey)
                } catch (_: Exception) {
                }

                _draftState.update { draft ->
                    draft.copy(
                        galleryPhotos = draft.galleryPhotos.filter { it.id != image.id },
                        isDirty = true
                    )
                }

                var profilePhotoRemoved = false
                if (_draftState.value.profilePhoto?.remoteUrl == image.remoteUrl) {
                    db.collection("usuarios").document(uid).update(
                        "fotoUrl", "",
                        "profilePhoto", FieldValue.delete()
                    ).await()
                    _draftState.update { draft ->
                        draft.copy(profilePhoto = null, isDirty = true)
                    }
                    profilePhotoRemoved = true
                }

                _remoteState.update { remote ->
                    remote.copy(
                        data = remote.data?.copy(
                            galleryPhotos = remote.data.galleryPhotos.filter { it.id != image.id },
                            profilePhoto = if (profilePhotoRemoved) null else remote.data.profilePhoto
                        )
                    )
                }
            } catch (e: Exception) {
                _draftState.update { draft ->
                    draft.copy(
                        galleryPhotos = draft.galleryPhotos.map {
                            if (it.id == image.id) it.copy(uploadState = ImageOperationState.Error("Error al eliminar")) else it
                        },
                        isDirty = true
                    )
                }
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al eliminar foto")
            }
        }
    }

    fun togglePhotoPrivacy(image: ProfileImage) {
        val uid = auth.currentUser?.uid ?: return
        val newPrivacy = !image.isPrivate

        _draftState.update { draft ->
            draft.copy(
                galleryPhotos = draft.galleryPhotos.map {
                    if (it.id == image.id) it.copy(isPrivate = newPrivacy) else it
                },
                isDirty = true
            )
        }

        viewModelScope.launch {
            try {
                val currentUser = getCurrentUserRaw() ?: throw Exception("No se pudo cargar el usuario")
                val updatedPhotos = currentUser.fotos.map {
                    if (it.url == image.remoteUrl) {
                        it.copy(esPrivada = newPrivacy)
                    } else {
                        it
                    }
                }

                db.collection("usuarios").document(uid).update("fotos", updatedPhotos).await()
                
                _remoteState.update { remote ->
                    remote.copy(
                        data = remote.data?.copy(
                            galleryPhotos = remote.data.galleryPhotos.map {
                                if (it.remoteUrl == image.remoteUrl) it.copy(isPrivate = newPrivacy) else it
                            }
                        )
                    )
                }
            } catch (e: Exception) {
                _draftState.update { draft ->
                    draft.copy(
                        galleryPhotos = draft.galleryPhotos.map {
                            if (it.id == image.id) it.copy(isPrivate = image.isPrivate) else it
                        },
                        isDirty = true
                    )
                }
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al cambiar privacidad de la foto")
            }
        }
    }

    fun setAsProfilePicture(image: ProfileImage) {
        val uid = auth.currentUser?.uid ?: return
        val finalValue = when (image.sourceType) {
            ImageSourceType.DEFAULT_AVATAR -> image.cacheKey
            ImageSourceType.REMOTE -> image.remoteUrl
            ImageSourceType.LOCAL_PENDING -> image.remoteUrl
        } ?: return

        viewModelScope.launch {
            try {
                val photoMap = mapOf(
                    "id" to image.id,
                    "remoteUrl" to image.remoteUrl.orEmpty(),
                    "cacheKey" to image.cacheKey,
                    "storagePath" to image.storagePath,
                    "isPrivate" to image.isPrivate,
                    "isProfilePhoto" to true,
                    "sortOrder" to image.sortOrder,
                    "sourceType" to image.sourceType.name
                )

                db.collection("usuarios").document(uid).update(
                    "fotoUrl", finalValue,
                    "profilePhoto", photoMap
                ).await()

                val updatedProfilePhoto = image.copy(isProfilePhoto = true)
                _draftState.update {
                    it.copy(
                        profilePhoto = updatedProfilePhoto,
                        isDirty = true
                    )
                }
                
                _remoteState.update { remote ->
                    remote.copy(
                        data = remote.data?.copy(profilePhoto = updatedProfilePhoto)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al cambiar foto de perfil")
            }
        }
    }

    fun saveProfileChanges() {
        val uid = auth.currentUser?.uid ?: return
        val draft = _draftState.value

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            _draftState.update { it.copy(isSaving = true, error = null) }

            try {
                val primaryPhone = draft.contactMethods.find { it.type == ContactType.PHONE && it.isPrimary }
                val primaryEmail = draft.contactMethods.find { it.type == ContactType.EMAIL && it.isPrimary }
                val altEmail = draft.contactMethods.find { it.id == "alt_email" }

                val photoValue = when (draft.profilePhoto?.sourceType) {
                    ImageSourceType.DEFAULT_AVATAR -> draft.profilePhoto.cacheKey
                    ImageSourceType.REMOTE -> draft.profilePhoto.remoteUrl
                    ImageSourceType.LOCAL_PENDING -> draft.profilePhoto.remoteUrl
                    null -> ""
                }.orEmpty()

                val photoMap = draft.profilePhoto?.let {
                    mapOf(
                        "id" to it.id,
                        "remoteUrl" to it.remoteUrl.orEmpty(),
                        "cacheKey" to it.cacheKey,
                        "storagePath" to it.storagePath,
                        "isPrivate" to it.isPrivate,
                        "isProfilePhoto" to true,
                        "sortOrder" to it.sortOrder,
                        "sourceType" to it.sourceType.name
                    )
                }

                val updates = mutableMapOf<String, Any>(
                    "nombre" to capitalizeWords(draft.nombre),
                    "nombre_s" to capitalizeWords(draft.nombre_s),
                    "apellido_pa" to capitalizeWords(draft.apellido_pa),
                    "apellido_ma" to capitalizeWords(draft.apellido_ma),
                    "rut" to draft.rut.trim(),
                    "telefono" to (primaryPhone?.value?.trim() ?: draft.telefono.trim()),
                    "correoAlternativo" to (altEmail?.value?.trim() ?: draft.correoAlternativo.trim()),
                    "mostrarCorreo" to (primaryEmail?.visibility == VisibilityPolicy.CONTACTS_ONLY),
                    "mostrarTelefono" to (primaryPhone?.visibility == VisibilityPolicy.CONTACTS_ONLY),
                    "mostrarRut" to draft.mostrarRut,
                    "comuna" to draft.comuna,
                    "region" to draft.region,
                    "id_region" to draft.id_region,
                    "privacidadVinculos" to draft.privacidadVinculos,
                    "descripcion" to draft.descripcion.trim(),
                    "fotoUrl" to photoValue,
                    "profilePhoto" to (photoMap ?: FieldValue.delete()),
                    "redesSociales" to draft.socialLinks.map {
                        SocialNetwork(
                            platform = it.platform.displayName,
                            customPlatform = it.customPlatform,
                            link = it.link,
                            visibility = it.visibility.name
                        )
                    },
                    "fotos" to draft.galleryPhotos.mapNotNull {
                        val url = when (it.sourceType) {
                            ImageSourceType.REMOTE -> it.remoteUrl
                            ImageSourceType.DEFAULT_AVATAR -> it.cacheKey
                            ImageSourceType.LOCAL_PENDING -> it.remoteUrl
                        }
                        if (url.isNullOrBlank()) null else UserPhoto(
                            id = it.id,
                            url = url,
                            esPrivada = it.isPrivate,
                            storagePath = it.storagePath,
                            sourceType = it.sourceType.name,
                            cacheKey = it.cacheKey
                        )
                    }
                )

                db.collection("usuarios").document(uid).update(updates).await()
                loadUserProfileInternal()

                _draftState.update {
                    it.copy(
                        isDirty = false,
                        isSaving = false,
                        error = null
                    )
                }

                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                _draftState.update {
                    it.copy(
                        isSaving = false,
                        error = e.localizedMessage ?: "Error al guardar cambios"
                    )
                }
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al guardar cambios")
            }
        }
    }

    fun loadOtherUserProfile(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return

        if (myUid == targetUid) {
            loadUserProfile()
            return
        }

        viewModelScope.launch {
            _otherProfileState.update { it.copy(isLoading = true, error = null) }
            try {
                val id1 = "${myUid}_$targetUid"
                val id2 = "${targetUid}_$myUid"

                val v1 = db.collection("vinculos").document(id1).get().await()
                val v2 = db.collection("vinculos").document(id2).get().await()

                val isAccepted =
                    (v1.exists() && v1.getString("estado") == "aceptado") ||
                            (v2.exists() && v2.getString("estado") == "aceptado")

                val doc = db.collection("usuarios").document(targetUid).get().await()
                val user = doc.toObject(UserData::class.java)?.copy(uid = targetUid)

                if (user != null) {
                    val filteredDomain = user.toDomain(
                        isOwner = false,
                        canSeeContactInfo = isAccepted,
                        canSeePrivatePhotos = isAccepted
                    )

                    _otherProfileState.value = ProfileRemoteState(
                        data = filteredDomain,
                        permissions = ViewerPermissions(
                            canSeePrivatePhotos = isAccepted,
                            canSeeContactInfo = isAccepted,
                            isOwner = false
                        ),
                        isLoading = false,
                        error = null
                    )
                } else {
                    _otherProfileState.update { it.copy(isLoading = false, error = "Usuario no encontrado") }
                }
            } catch (e: Exception) {
                _otherProfileState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Error al cargar perfil"
                    )
                }
            }
        }
    }

    private fun prefetchProfileImages(domain: ProfileDomainModel) {
        viewModelScope.launch {
            try {
                domain.profilePhoto?.remoteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    ProfileImageCacheManager.preloadImage(getApplication<Application>().applicationContext, url)
                }

                domain.galleryPhotos.forEach { photo ->
                    photo.remoteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        ProfileImageCacheManager.preloadImage(getApplication<Application>().applicationContext, url)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun getCurrentUserRaw(): UserData? {
        val uid = auth.currentUser?.uid ?: return null
        val doc = db.collection("usuarios").document(uid).get().await()
        return doc.toObject(UserData::class.java)?.copy(uid = uid)
    }

    private fun saveUserToCache(user: UserData) {
        prefs?.edit()?.apply {
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
            putBoolean("user_mostrarCorreo", user.mostrarCorreo)
            putBoolean("user_mostrarTelefono", user.mostrarTelefono)
            putBoolean("user_mostrarRut", user.mostrarRut)
            putString("user_comuna", user.comuna)
            putString("user_region", user.region)
            putString("user_id_region", user.id_region)
            putString("user_privacidadVinculos", user.privacidadVinculos)
            putString("user_correoAlternativo", user.correoAlternativo)
            putString("user_descripcion", user.descripcion)
            apply()
        }
    }

    private fun capitalizeWords(text: String): String {
        if (text.isBlank()) return ""
        return text.trim().split("\\s+".toRegex()).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                ProfileImageCacheManager.clearAllCache(getApplication<Application>().applicationContext)
            } catch (_: Exception) {
            }

            auth.signOut()
            prefs.edit().clear().apply()
            onSuccess()
        }
    }

    fun clearState() {
        _uiState.value = ProfileUiState.Idle
    }

    fun sendVerificationCode(phoneNumber: String, activity: Activity) {
        _uiState.value = ProfileUiState.Loading

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {}

                override fun onVerificationFailed(e: FirebaseException) {
                    _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al enviar código")
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    _uiState.value = ProfileUiState.CodeSent
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(code: String) {
        val vId = verificationId ?: return
        PhoneAuthProvider.getCredential(vId, code)

        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                db.collection("usuarios").document(uid).update("telefonoVerificado", true).await()
                loadUserProfileInternal()
                _uiState.value = ProfileUiState.SuccessVerification
            } catch (_: Exception) {
                _uiState.value = ProfileUiState.Error("Código inválido")
            }
        }
    }

    fun openGallery(index: Int, photos: List<ProfileImage>) {
        _galleryState.value = GalleryViewerState(
            isOpen = true,
            initialIndex = index,
            photos = photos
        )
    }

    fun closeGallery() {
        _galleryState.value = _galleryState.value.copy(isOpen = false)
    }
}
