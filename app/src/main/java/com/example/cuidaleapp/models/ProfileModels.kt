package com.example.cuidaleapp.models

import android.net.Uri
import com.google.firebase.Timestamp
import java.util.UUID

enum class VisibilityPolicy {
    PRIVATE,
    CONTACTS_ONLY,
    PUBLIC
}

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Success : ProfileUiState()
    object CodeSent : ProfileUiState()
    object SuccessVerification : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

data class UserPhoto(
    val id: String = "",
    val url: String = "",
    val esPrivada: Boolean = false,
    val storagePath: String = "",
    val sourceType: String = "REMOTE",
    val cacheKey: String = ""
)

data class SocialNetwork(
    val platform: String = "",
    val customPlatform: String? = null,
    val link: String = "",
    val visibility: String = VisibilityPolicy.CONTACTS_ONLY.name
)

enum class ImageSourceType {
    DEFAULT_AVATAR,
    LOCAL_PENDING,
    REMOTE
}

sealed class ImageOperationState {
    object Idle : ImageOperationState()
    data class Processing(val progress: Float = 0f) : ImageOperationState()
    object Success : ImageOperationState()
    object Deleting : ImageOperationState()
    data class Error(val message: String) : ImageOperationState()
}

data class ProfileImage(
    val id: String = UUID.randomUUID().toString(),
    val remoteUrl: String? = null,
    val localUri: Uri? = null,
    val cacheKey: String = "",
    val storagePath: String = "",
    val isPrivate: Boolean = false,
    val isProfilePhoto: Boolean = false,
    val sortOrder: Int = 0,
    val sourceType: ImageSourceType = ImageSourceType.REMOTE,
    val uploadState: ImageOperationState = ImageOperationState.Idle
)

enum class SocialPlatform(val displayName: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    X("X"),
    TIKTOK("TikTok"),
    LINKEDIN("LinkedIn"),
    YOUTUBE("YouTube"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    WEBSITE("Website"),
    OTHER("Otro")
}

data class SocialLink(
    val platform: SocialPlatform = SocialPlatform.OTHER,
    val customPlatform: String? = null,
    val link: String = "",
    val visibility: VisibilityPolicy = VisibilityPolicy.CONTACTS_ONLY,
    val id: String = UUID.randomUUID().toString()
)

enum class ContactType {
    EMAIL,
    PHONE,
    WHATSAPP,
    TELEGRAM,
    OTHER
}

data class ContactMethod(
    val type: ContactType = ContactType.OTHER,
    val value: String = "",
    val visibility: VisibilityPolicy = VisibilityPolicy.PRIVATE,
    val isPrimary: Boolean = false,
    val isVerified: Boolean = false,
    val label: String? = null,
    val id: String = UUID.randomUUID().toString()
)

data class FormacionAcademica(
    val id: String = UUID.randomUUID().toString(),
    val titulo: String = "",
    val institucion: String = "",
    val anioInicio: String = "",
    val anioFin: String = ""
)

data class ExperienciaLaboral(
    val id: String = UUID.randomUUID().toString(),
    val cargo: String = "",
    val empresa: String = "",
    val desde: String = "",
    val hasta: String = "",
    val tiempoExperiencia: String = "",
    val descripcion: String = ""
)

data class Certificado(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String = "",
    val tipoArchivo: String = "", // pdf | doc | docx | png | jpg | jpeg
    val urlArchivo: String = "",
    val storagePath: String = "",
    val fechaSubida: Timestamp? = null,
    val estadoValidacion: String = "pendiente"
)

data class ReferenciaLaboral(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String = "",
    val empresa: String = "",
    val telefono: String = "",
    val cargo: String = "",
    val detalle: String = ""
)

data class CalificacionPublica(
    val autorNombre: String = "",
    val calificacion: Double = 0.0,
    val observacion: String = "",
    val fecha: Timestamp? = null
)

data class ViewerPermissions(
    val canSeePrivatePhotos: Boolean = false,
    val canSeeContactInfo: Boolean = false,
    val isOwner: Boolean = false
)

data class ProfileDomainModel(
    val uid: String = "",
    val nombre: String = "",
    val nombre_s: String = "",
    val apellido_pa: String = "",
    val apellido_ma: String = "",
    val rut: String = "",
    val rol: String = "",
    val verificado: Boolean = false,
    val telefono: String = "",
    val telefonoVerificado: Boolean = false,
    val correo: String = "",
    val mostrarCorreo: Boolean = false,
    val mostrarTelefono: Boolean = false,
    val mostrarRut: Boolean = false,
    val comuna: String = "",
    val region: String = "",
    val id_region: String = "",
    val privacidadVinculos: String = "privado",
    val profilePhoto: ProfileImage? = null,
    val galleryPhotos: List<ProfileImage> = emptyList(),
    val socialLinks: List<SocialLink> = emptyList(),
    val contactMethods: List<ContactMethod> = emptyList(),
    val verificacionOficial: String = "pendiente",
    val fechaNacimiento: String = "",
    val discapacidad: Boolean = false,
    val tipoDiscapacidad: String = "",
    val tiempoDiscapacidad: String = "",
    val valorHoraReferencial: Int = 0,
    val formacionAcademica: List<FormacionAcademica> = emptyList(),
    val experienciasLaborales: List<ExperienciaLaboral> = emptyList(),
    val certificados: List<Certificado> = emptyList(),
    val referenciasLaborales: List<ReferenciaLaboral> = emptyList(),
    val descripcion: String = ""
)

data class GalleryViewerState(
    val isOpen: Boolean = false,
    val initialIndex: Int = 0,
    val photos: List<ProfileImage> = emptyList()
)

data class EditProfileDraftState(
    val uid: String = "",
    val nombre: String = "",
    val nombre_s: String = "",
    val apellido_pa: String = "",
    val apellido_ma: String = "",
    val rut: String = "",
    val rol: String = "",
    val verificado: Boolean = false,
    val telefono: String = "",
    val telefonoVerificado: Boolean = false,
    val correo: String = "",
    val mostrarCorreo: Boolean = false,
    val mostrarTelefono: Boolean = false,
    val mostrarRut: Boolean = false,
    val comuna: String = "",
    val region: String = "",
    val id_region: String = "",
    val privacidadVinculos: String = "privado",
    val profilePhoto: ProfileImage? = null,
    val galleryPhotos: List<ProfileImage> = emptyList(),
    val socialLinks: List<SocialLink> = emptyList(),
    val contactMethods: List<ContactMethod> = emptyList(),
    val verificacionOficial: String = "pendiente",
    val fechaNacimiento: String = "",
    val discapacidad: Boolean = false,
    val tipoDiscapacidad: String = "",
    val tiempoDiscapacidad: String = "",
    val valorHoraReferencial: Int = 0,
    val formacionAcademica: List<FormacionAcademica> = emptyList(),
    val experienciasLaborales: List<ExperienciaLaboral> = emptyList(),
    val certificados: List<Certificado> = emptyList(),
    val referenciasLaborales: List<ReferenciaLaboral> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val descripcion: String = "",
    val error: String? = null
)
