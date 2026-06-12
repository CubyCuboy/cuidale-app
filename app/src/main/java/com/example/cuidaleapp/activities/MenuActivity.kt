package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.screens.*
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.activities.MenuNavigation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cuidaleapp.utils.AppNavigationState
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

sealed class MenuNavigation {
    object Main : MenuNavigation()
    object Profile : MenuNavigation()
    object EditProfile : MenuNavigation()
    data class Ficha(val startInEditMode: Boolean = false, val returnTo: MenuNavigation = Profile) : MenuNavigation()
    object Search : MenuNavigation()
    data class Settings(val returnTo: MenuNavigation = Profile) : MenuNavigation()
    object Chats : MenuNavigation()
    object Notifications : MenuNavigation()
    object Requests : MenuNavigation()
    data class CreateRequest(val requestId: String? = null) : MenuNavigation()
    data class ManageApplications(val solicitudId: String) : MenuNavigation()
    data class ReviewRequest(val solicitud: SolicitudCuidado) : MenuNavigation()
    object History : MenuNavigation()
    object SocialManagement : MenuNavigation()
    data class OtherFicha(val uid: String, val returnTo: MenuNavigation = Main) : MenuNavigation()
    data class Chat(val chatId: String, val otherUid: String, val otherUser: UserData? = null, val returnTo: MenuNavigation = Chats) : MenuNavigation()
}

class MenuActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        val chatViewModel = androidx.lifecycle.ViewModelProvider(this)[ChatViewModel::class.java]
        chatViewModel.setUserStatus(true)
    }

    override fun onPause() {
        super.onPause()
        val chatViewModel = androidx.lifecycle.ViewModelProvider(this)[ChatViewModel::class.java]
        chatViewModel.setUserStatus(false)
    }

    private var intentTrigger by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
            }
        }

        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("usuarios").document(uid).update("fcmToken", token)
            }
        }

        setContent {
            val menuViewModel: MenuViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val fichaViewModel: FichaViewModel = viewModel()
            val searchViewModel: SearchViewModel = viewModel()
            val vinculosViewModel: VinculosViewModel = viewModel()
            val favoritosViewModel: FavoritosViewModel = viewModel()
            val solicitudViewModel: SolicitudViewModel = viewModel()

            val isDark by menuViewModel.isDarkMode.collectAsState()
            val userData by menuViewModel.userData.collectAsState()
            val isLoggedOut by menuViewModel.isLoggedOut.collectAsState()
            val fichaState by fichaViewModel.state.collectAsState()

            val chatViewModel: ChatViewModel = viewModel()
            val notificationsViewModel: NotificationsViewModel = viewModel()
            
            val unreadCount by chatViewModel.totalUnreadCount.collectAsState()
            val pendingNotifs by notificationsViewModel.unreadCount.collectAsState()
            val solicitudesAceptadas by solicitudViewModel.solicitudesAceptadas.collectAsState()

            LaunchedEffect(Unit) {
                chatViewModel.listenChats()
            }

            var currentScreen by remember { mutableStateOf<MenuNavigation>(Main) }

            LaunchedEffect(currentScreen) {
                val screenName = when(currentScreen) {
                    Main -> "MAIN"
                    Profile -> "PROFILE"
                    EditProfile -> "EDIT_PROFILE"
                    is Ficha -> "FICHA"
                    Search -> "SEARCH"
                    is Settings -> "SETTINGS"
                    Chats -> "CHATS"
                    Notifications -> "NOTIFICATIONS"
                    SocialManagement -> "SOCIAL"
                    History -> "HISTORY"
                    is OtherFicha -> "OTHER_FICHA"
                    is Chat -> "CHAT"
                    Requests -> "REQUESTS"
                    is CreateRequest -> "CREATE_REQUEST"
                    is ManageApplications -> "MANAGE_APPLICATIONS"
                    is ReviewRequest -> "REVIEW_REQUEST"
                }
                AppNavigationState.setScreen(screenName)
            }

            LaunchedEffect(intentTrigger) {
                val target = intent.getStringExtra("TARGET_SCREEN")
                val uid = intent.getStringExtra("USER_UID")
                val chatId = intent.getStringExtra("chatId")

                when (target) {
                    "OTHER_FICHA" -> if (uid != null) currentScreen = OtherFicha(uid, returnTo = SocialManagement)
                    "CHATS" -> currentScreen = Chats
                    "NOTIFICATIONS" -> currentScreen = Notifications
                    "CHAT_DETAIL" -> {
                        if (chatId != null && uid != null) {
                            currentScreen = Chat(chatId, uid, returnTo = Chats)
                        } else {
                            currentScreen = Chats
                        }
                    }
                }
            }

            BackHandler(enabled = currentScreen != Main) {
                currentScreen = when (val cs = currentScreen) {
                    Profile -> Main
                    EditProfile -> Profile
                    is Ficha -> cs.returnTo
                    Search -> Main
                    is Settings -> cs.returnTo
                    Chats -> Main
                    Notifications -> Main
                    Requests -> Main
                    History -> Requests
                    is CreateRequest -> Requests
                    is ManageApplications -> Requests
                    is ReviewRequest -> Requests
                    is Chat -> cs.returnTo
                    is OtherFicha -> cs.returnTo
                    SocialManagement -> Profile
                    Main -> Main
                }
            }

            if (isLoggedOut) {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@MenuActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }

            CuidaleAppTheme(darkTheme = isDark) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f) togetherWith
                        fadeOut(animationSpec = tween(300))
                    },
                    label = "MenuNavigationAnimation"
                ) { cs ->
                    when (cs) {
                        Main -> {
                            MenuScreen(
                                userData = userData,
                                searchViewModel = searchViewModel,
                                fichaState = fichaState,
                                isDark = isDark,
                                unreadCount = unreadCount,
                                pendingNotifs = pendingNotifs,
                                onToggleTheme = { menuViewModel.toggleTheme() },
                                onLogout = { menuViewModel.logout() },
                                onViewProfile = { currentScreen = Profile },
                                onViewFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Main) },
                                onViewSettings = { currentScreen = Settings(returnTo = Main) },
                                onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = Main) },
                                onTabSelected = { tab ->
                                    currentScreen = when (tab) {
                                        "chats" -> Chats
                                        "notifications" -> Notifications
                                        "requests" -> Requests
                                        else -> Main
                                    }
                                }
                            )
                        }
                        Profile -> {
                            ProfileScreen(
                                viewModel = profileViewModel,
                                fichaViewModel = fichaViewModel,
                                onNavigateBack = { currentScreen = Main },
                                onSettingsClick = { currentScreen = Settings() },
                                onSocialClick = {
                                    currentScreen = SocialManagement
                                },
                                onTabSelected = { tab ->
                                    currentScreen = when (tab) {
                                        "chats" -> Chats
                                        "notifications" -> Notifications
                                        "requests" -> Requests
                                        else -> Main
                                    }
                                }
                            )
                        }
                        EditProfile -> {
                            EditProfileScreen(
                                viewModel = profileViewModel,
                                onNavigateBack = { currentScreen = Profile }
                            )
                        }
                        is Ficha -> {
                            FichaScreen(
                                viewModel = fichaViewModel,
                                profileViewModel = profileViewModel,
                                startInEditMode = cs.startInEditMode,
                                onNavigateBack = { currentScreen = cs.returnTo }
                            )
                        }
                        Search -> {
                            SearchProfileScreen(
                                onNavigateBack = { currentScreen = Main },
                                onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = Search) }
                            )
                        }
                        is Settings -> {
                            SettingsScreen(
                                viewModel = profileViewModel,
                                isDark = isDark,
                                onBack = { currentScreen = cs.returnTo },
                                onLogoutClick = { menuViewModel.logout() },
                                onResetPass = { /* Handle reset pass */ }
                            )
                        }
                        Chats -> {
                            ChatsScreen(
                                userData = userData,
                                isDark = isDark,
                                onToggleTheme = { menuViewModel.toggleTheme() },
                                onLogout = { menuViewModel.logout() },
                                onViewProfile = { currentScreen = Profile },
                                onViewFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Chats) },
                                onViewSettings = { currentScreen = Settings(returnTo = Chats) },
                                unreadCount = unreadCount,
                                pendingNotifs = pendingNotifs,
                                onTabSelected = { tab ->
                                    currentScreen = when (tab) {
                                        "notifications" -> Notifications
                                        "home" -> Main
                                        "requests" -> Requests
                                        else -> Chats
                                    }
                                },
                                onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = Chats) },
                                onChatClick = { chatId, otherUser ->
                                    currentScreen = Chat(chatId, otherUser.uid, otherUser, returnTo = Chats)
                                },
                                favoritosViewModel = favoritosViewModel,
                                chatViewModel = chatViewModel,
                                vinculosViewModel = vinculosViewModel
                            )
                        }
                        Notifications -> {
                            NotificationsScreen(
                                userData = userData,
                                isDark = isDark,
                                onToggleTheme = { menuViewModel.toggleTheme() },
                                onLogout = { menuViewModel.logout() },
                                onViewProfile = { currentScreen = Profile },
                                onViewFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Notifications) },
                                onViewSettings = { currentScreen = Settings(returnTo = Notifications) },
                                unreadCount = unreadCount,
                                pendingNotifs = pendingNotifs,
                                onTabSelected = { tab ->
                                    currentScreen = when (tab) {
                                        "chats" -> Chats
                                        "home" -> Main
                                        "requests" -> Requests
                                        else -> Notifications
                                    }
                                },
                                onNotificationClick = { notification ->
                                    when (notification.tipo) {
                                        "solicitud_vinculo" -> {
                                            currentScreen = OtherFicha(uid = notification.emisor, returnTo = Notifications)
                                        }
                                        "vinculo_aceptado" -> {
                                            val chatId = notification.data["chatId"] ?: ""
                                            if (chatId.isNotEmpty()) {
                                                currentScreen = Chat(chatId = chatId, otherUid = notification.emisor, returnTo = Notifications)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        Requests -> {
                            SolicitudesScreen(
                                viewModel = solicitudViewModel,
                                userData = userData,
                                unreadCount = unreadCount,
                                pendingNotifs = pendingNotifs,
                                onTabSelected = { tab ->
                                    currentScreen = when (tab) {
                                        "chats" -> Chats
                                        "notifications" -> Notifications
                                        "home" -> Main
                                        else -> Requests
                                    }
                                },
                                onCreateRequest = { currentScreen = CreateRequest(null) },
                                onEditRequest = { id -> currentScreen = CreateRequest(id) },
                                onViewPatientDetails = { uid ->
                                    // For caregiver viewing accepted service details
                                    val sol = solicitudesAceptadas.find { it.pacienteFichaId == uid }
                                    if (sol != null) {
                                        currentScreen = ReviewRequest(sol)
                                    } else {
                                        currentScreen = OtherFicha(uid, returnTo = Requests)
                                    }
                                },
                                onViewCaregiverProfile = { uid -> currentScreen = OtherFicha(uid, returnTo = Requests) },
                                onNavigateToHistory = { currentScreen = History }
                            )
                        }
                        is CreateRequest -> {
                            CreateSolicitudScreen(
                                requestId = cs.requestId,
                                viewModel = solicitudViewModel,
                                fichaViewModel = fichaViewModel,
                                onNavigateBack = { currentScreen = Requests }
                            )
                        }
                        is ManageApplications -> {
                            PostulacionesScreen(
                                solicitudId = cs.solicitudId,
                                viewModel = solicitudViewModel,
                                onNavigateBack = { currentScreen = Requests },
                                onViewCaregiver = { uid -> currentScreen = OtherFicha(uid, returnTo = cs) }
                            )
                        }
                        is ReviewRequest -> {
                        ReviewSolicitudScreen(
                            solicitud = cs.solicitud,
                            viewModel = solicitudViewModel,
                            isAccepted = (userData.rol == "cuidador" && cs.solicitud.cuidadorUid == userData.uid) || (userData.rol == "representante" && cs.solicitud.estado == "cerrada"),
                            onNavigateBack = { currentScreen = Requests },
                            onViewFullFicha = { uid -> currentScreen = OtherFicha(uid, returnTo = ReviewRequest(cs.solicitud)) },
                            onRedirectToRequests = { currentScreen = Requests },
                            onNavigateToChat = { chatId, otherUid -> 
                                currentScreen = Chat(chatId, otherUid, returnTo = ReviewRequest(cs.solicitud))
                            }
                        )
                    }
                        is Chat -> {
                            var otherUserData by remember { mutableStateOf(cs.otherUser) }
                            LaunchedEffect(cs.otherUid) {
                                if (otherUserData == null) {
                                    val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("usuarios").document(cs.otherUid).get().await()
                                    otherUserData = doc.toObject(UserData::class.java)
                                }
                            }
                            if (otherUserData != null) {
                                ChatScreen(
                                    chatId = cs.chatId,
                                    otherUser = otherUserData!!,
                                    myUid = userData.uid,
                                    myName = userData.nombre,
                                    myRole = userData.rol,
                                    isDark = isDark,
                                    viewModel = chatViewModel,
                                    favoritosViewModel = favoritosViewModel,
                                    onNavigateBack = { currentScreen = cs.returnTo },
                                    onViewOtherProfile = { uid ->
                                        currentScreen = OtherFicha(uid, returnTo = cs)
                                    },
                                    onRedirectToRequests = {
                                        currentScreen = Requests
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is OtherFicha -> {
                            LaunchedEffect(cs.uid) {
                                profileViewModel.loadOtherUserProfile(cs.uid)
                            }
                            OtherFichaScreen(
                                profileViewModel = profileViewModel,
                                vinculosViewModel = vinculosViewModel,
                                favoritosViewModel = favoritosViewModel,
                                onNavigateBack = { currentScreen = cs.returnTo },
                                onChatNavigate = { chatId, otherUid, otherUser ->
                                    currentScreen = Chat(chatId, otherUid, otherUser, returnTo = cs)
                                },
                                onReviewRequest = { sol -> currentScreen = ReviewRequest(sol) }
                            )
                        }
                        SocialManagement -> {
                            SocialManagementScreen(
                                onNavigateBack = { currentScreen = Profile },
                                onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = SocialManagement) },
                                favoritosViewModel = favoritosViewModel,
                                vinculosViewModel = vinculosViewModel
                            )
                        }
                        History -> {
                            HistorialScreen(
                                viewModel = solicitudViewModel,
                                userData = userData,
                                onNavigateBack = { currentScreen = Requests },
                                onViewProfile = { uid -> currentScreen = OtherFicha(uid, returnTo = History) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentTrigger++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Eliminamos hideSystemUI() para que el teclado funcione correctamente con Compose
    }

    private fun hideSystemUI() {
        // Desactivado para evitar errores de insets con el teclado
    }
}
