package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.screens.*
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.viewmodel.*
import com.example.cuidaleapp.activities.MenuNavigation.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppNavigationState
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    data class ReviewRequest(val solicitud: SolicitudCuidado, val returnTo: MenuNavigation = Requests) : MenuNavigation()
    data class ActiveTasks(val solicitud: SolicitudCuidado) : MenuNavigation()
    data class ServiceReport(val informe: com.example.cuidaleapp.models.InformeTurno) : MenuNavigation()
    object History : MenuNavigation()
    object SocialManagement : MenuNavigation()
    data class OtherFicha(val uid: String, val returnTo: MenuNavigation = Main) : MenuNavigation()
    data class Chat(val chatId: String, val otherUid: String, val otherUser: UserData? = null, val returnTo: MenuNavigation = Chats, val initialMessage: String? = null) : MenuNavigation()
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

    private var intentTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()

        setContent {
            val menuViewModel: MenuViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val fichaViewModel: FichaViewModel = viewModel()
            val searchViewModel: SearchViewModel = viewModel()
            val vinculosViewModel: VinculosViewModel = viewModel()
            val favoritosViewModel: FavoritosViewModel = viewModel()
            val solicitudViewModel: SolicitudViewModel = viewModel()
            val chatViewModel: ChatViewModel = viewModel()
            val notificationsViewModel: NotificationsViewModel = viewModel()

            val isDark by menuViewModel.isDarkMode.collectAsState()
            val userData by menuViewModel.userData.collectAsState()
            val isLoggedOut by menuViewModel.isLoggedOut.collectAsState()
            val fichaState by fichaViewModel.state.collectAsState()
            val searchState by searchViewModel.state.collectAsState()
            val isSearchRefreshing by searchViewModel.isRefreshing.collectAsState()

            val scope = rememberCoroutineScope()
            
            val unreadCount by chatViewModel.totalUnreadCount.collectAsState()
            val pendingNotifs by notificationsViewModel.unreadCount.collectAsState()
            val solicitudesAceptadas by solicitudViewModel.solicitudesAceptadas.collectAsState()

            val misPostulaciones by solicitudViewModel.misPostulaciones.collectAsState()
            val postulacionesRecibidas by solicitudViewModel.postulacionesRecibidas.collectAsState()
            val pendingRequests = if (userData.rol == "cuidador") misPostulaciones.size 
                                  else postulacionesRecibidas.values.flatten().count { it.estado == "pendiente" }

            var currentScreen by remember { mutableStateOf<MenuNavigation>(Main) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            LaunchedEffect(userData.rol, userData.region) {
                if (userData.rol.isNotEmpty()) {
                    searchViewModel.initSearch(userData.rol, userData.region)
                    chatViewModel.listenChats()
                }
            }

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
                    is ActiveTasks -> "ACTIVE_TASKS"
                    is ServiceReport -> "SERVICE_REPORT"
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
                    is ReviewRequest -> cs.returnTo
                    is ActiveTasks -> Requests
                    is ServiceReport -> Requests
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
                fun getScreenWeight(nav: MenuNavigation): Int {
                    return when (nav) {
                        Notifications -> 0
                        Requests -> 1
                        Main -> 2
                        Chats -> 3
                        else -> 10
                    }
                }

                val isAppReady = userData.uid.isNotEmpty() && userData.rol.isNotEmpty() && (searchState !is SearchState.Loading || isSearchRefreshing)
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(isAppReady) {
                    if (isAppReady) {
                        delay(1200)
                        showSplash = false
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(CuidaleThemeExtras.gradient.top, CuidaleThemeExtras.gradient.bottom)))
                ) {
                    if (!showSplash) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            ModalNavigationDrawer(
                                drawerState = drawerState,
                                drawerContent = {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        ModalDrawerSheet(
                                            modifier = Modifier.fillMaxHeight().width(320.dp),
                                            drawerContainerColor = CuidaleThemeExtras.customColors.panelBackground,
                                            drawerTonalElevation = 0.dp
                                        ) {
                                            CuidaleDrawerContent(
                                                nombre = userData.nombre,
                                                apellido = userData.apellido_pa,
                                                fotoUrl = userData.fotoUrl,
                                                rol = userData.rol,
                                                isDark = isDark,
                                                onToggleTheme = { menuViewModel.toggleTheme() },
                                                onProfileClick = {
                                                    scope.launch { drawerState.close() }
                                                    currentScreen = Profile
                                                },
                                                onFichaClick = {
                                                    scope.launch { drawerState.close() }
                                                    currentScreen = Ficha(startInEditMode = false, returnTo = currentScreen)
                                                },
                                                onSocialClick = {
                                                    scope.launch { drawerState.close() }
                                                    currentScreen = SocialManagement
                                                },
                                                onSettingsClick = {
                                                    scope.launch { drawerState.close() }
                                                    currentScreen = Settings(returnTo = currentScreen)
                                                },
                                                onLogoutClick = {
                                                    scope.launch { drawerState.close() }
                                                    menuViewModel.logout()
                                                },
                                                onCloseClick = {
                                                    scope.launch { drawerState.close() }
                                                }
                                            )
                                        }
                                    }
                                },
                                gesturesEnabled = currentScreen == Main || currentScreen == Profile || 
                                                 currentScreen == Chats || currentScreen == Notifications || 
                                                 currentScreen == Requests
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Scaffold(
                                        bottomBar = {
                                            val showBottomBar = currentScreen == Main || currentScreen == Profile || 
                                                              currentScreen == Chats || currentScreen == Notifications || 
                                                              currentScreen == Requests
                                            
                                            if (showBottomBar) {
                                                CuidaleBottomBar(
                                                    selectedTab = when(currentScreen) {
                                                        Main -> "home"
                                                        Chats -> "chats"
                                                        Notifications -> "notifications"
                                                        Requests -> "requests"
                                                        Profile -> "profile"
                                                        else -> "none"
                                                    },
                                                    unreadCount = unreadCount,
                                                    pendingNotifs = pendingNotifs,
                                                    pendingRequests = pendingRequests,
                                                    onTabSelected = { tab ->
                                                        currentScreen = when (tab) {
                                                            "chats" -> Chats
                                                            "notifications" -> Notifications
                                                            "requests" -> Requests
                                                            "home" -> Main
                                                            "profile" -> Profile
                                                            else -> currentScreen
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        containerColor = Color.Transparent,
                                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                                    ) { innerPadding ->
                                        Box(
                                            modifier = Modifier
                                                .padding(innerPadding)
                                                .fillMaxSize()
                                        ) {
                                            AnimatedContent(
                                                targetState = currentScreen,
                                                modifier = Modifier.fillMaxSize(),
                                                transitionSpec = {
                                                        val duration = 500
                                                        val initialS = initialState
                                                        val targetS = targetState
                                                        val initialWeight = getScreenWeight(initialS)
                                                        val targetWeight = getScreenWeight(targetS)
                                                        
                                                        val isGoingBack = when {
                                                            initialS is Chat && targetS == Chats -> true
                                                            initialS is ReviewRequest && targetS == Requests -> true
                                                            initialS is OtherFicha && (targetS == Main || targetS == Search) -> true
                                                            initialS == EditProfile && targetS == Profile -> true
                                                            initialS is Ficha && targetS == Profile -> true
                                                            initialS is Settings && targetS == Profile -> true
                                                            initialWeight != 10 && targetWeight != 10 -> targetWeight < initialWeight
                                                            initialWeight == 10 && targetWeight != 10 -> true
                                                            else -> false
                                                        }

                                                        val enterTransition = if (isGoingBack) {
                                                            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it } + 
                                                            fadeIn(tween(duration)) +
                                                            scaleIn(initialScale = 0.92f, animationSpec = tween(duration, easing = FastOutSlowInEasing))
                                                        } else {
                                                            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it } + 
                                                            fadeIn(tween(duration)) +
                                                            scaleIn(initialScale = 1.08f, animationSpec = tween(duration, easing = FastOutSlowInEasing))
                                                        }

                                                        val exitTransition = if (isGoingBack) {
                                                            slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it } + 
                                                            fadeOut(tween(duration)) +
                                                            scaleOut(targetScale = 1.08f, animationSpec = tween(duration, easing = FastOutSlowInEasing))
                                                        } else {
                                                            slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it } + 
                                                            fadeOut(tween(duration)) +
                                                            scaleOut(targetScale = 0.92f, animationSpec = tween(duration, easing = FastOutSlowInEasing))
                                                        }

                                                        enterTransition togetherWith exitTransition
                                                    },
                                                    label = "MenuNavigationAnimation"
                                                ) { cs ->
                                                    when (cs) {
                                                        Main -> {
                                                            MenuScreen(
                                                                userData = userData,
                                                                searchViewModel = searchViewModel,
                                                                fichaState = fichaState,
                                                                onViewFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Main) },
                                                                onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = Main) }
                                                            )
                                                        }
                                                        Profile -> {
                                                            ProfileScreen(
                                                                viewModel = profileViewModel,
                                                                fichaViewModel = fichaViewModel,
                                                                onNavigateBack = { currentScreen = Main },
                                                                onSettingsClick = { currentScreen = Settings() },
                                                                onSocialClick = { currentScreen = SocialManagement },
                                                                onTabSelected = { tab ->
                                                                    currentScreen = when (tab) {
                                                                        "chats" -> Chats
                                                                        "notifications" -> Notifications
                                                                        "requests" -> Requests
                                                                        "home" -> Main
                                                                        else -> Profile
                                                                    }
                                                                },
                                                                unreadCount = unreadCount,
                                                                pendingNotifs = pendingNotifs,
                                                                solicitudViewModel = solicitudViewModel
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
                                                                onNavigateBack = { currentScreen = cs.returnTo },
                                                                onNavigateToTasks = { currentScreen = Requests }
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
                                                                onResetPass = { 
                                                                    startActivity(Intent(this@MenuActivity, RecoveryPasswordActivity::class.java))
                                                                }
                                                            )
                                                        }
                                                        Chats -> {
                                                            ChatsScreen(
                                                                userData = userData,
                                                                isDark = isDark,
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
                                                                onNotificationClick = { notification ->
                                                                    when (notification.tipo) {
                                                                        "solicitud_vinculo" -> currentScreen = OtherFicha(uid = notification.emisor, returnTo = Notifications)
                                                                        "vinculo_aceptado", "nuevo_mensaje" -> {
                                                                            val chatId = notification.data["chatId"] ?: ""
                                                                            if (chatId.isNotEmpty()) currentScreen = Chat(chatId = chatId, otherUid = notification.emisor, returnTo = Notifications)
                                                                        }
                                                                        "nuevo_usuario" -> currentScreen = OtherFicha(uid = notification.emisor, returnTo = Notifications)
                                                                        "recordatorio_tarea", "tarea_atrasada", "tarea_finalizada", "tarea_postergada" -> {
                                                                            currentScreen = Requests
                                                                        }
                                                                        "turno_finalizado" -> {
                                                                            currentScreen = Requests
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        Requests -> {
                                                            SolicitudesScreen(
                                                                viewModel = solicitudViewModel,
                                                                userData = userData,
                                                                onCreateRequest = { currentScreen = CreateRequest(null) },
                                                                onEditRequest = { id -> currentScreen = CreateRequest(id) },
                                                                onViewPatientDetails = { uid ->
                                                                    val solAceptada = solicitudesAceptadas.find { it.pacienteFichaId == uid }
                                                                    if (solAceptada != null) currentScreen = ReviewRequest(solAceptada, returnTo = Requests)
                                                                    else {
                                                                        val pendingPair = solicitudViewModel.misPostulaciones.value.find { it.first.pacienteFichaId == uid }
                                                                        if (pendingPair != null) currentScreen = ReviewRequest(pendingPair.first, returnTo = Requests)
                                                                        else currentScreen = OtherFicha(uid, returnTo = Requests)
                                                                    }
                                                                },
                                                                onViewCaregiverProfile = { uid -> currentScreen = OtherFicha(uid, returnTo = Requests) },
                                                                onViewTasks = { sol -> currentScreen = ActiveTasks(sol) },
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
                                                                    onViewOtherProfile = { uid -> currentScreen = OtherFicha(uid, returnTo = cs) },
                                                                    onRedirectToRequests = { currentScreen = Requests },
                                                                    onViewRequest = { solId ->
                                                                        scope.launch {
                                                                            try {
                                                                                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                                                    .collection("solicitudes").document(solId).get().await()
                                                                                val sol = doc.toObject(SolicitudCuidado::class.java)
                                                                                if (sol != null) currentScreen = ReviewRequest(sol, returnTo = cs)
                                                                            } catch (e: Exception) {}
                                                                        }
                                                                    }
                                                                )
                                                            } else {
                                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                    CircularProgressIndicator()
                                                                }
                                                            }
                                                        }
                                                        is OtherFicha -> {
                                                            LaunchedEffect(cs.uid) { profileViewModel.loadOtherUserProfile(cs.uid) }
                                                            OtherFichaScreen(
                                                                profileViewModel = profileViewModel,
                                                                vinculosViewModel = vinculosViewModel,
                                                                favoritosViewModel = favoritosViewModel,
                                                                onNavigateBack = { currentScreen = cs.returnTo },
                                                                onChatNavigate = { chatId, otherUid, otherUser ->
                                                                    currentScreen = Chat(chatId, otherUid, otherUser, returnTo = cs)
                                                                },
                                                                onReviewRequest = { sol -> currentScreen = ReviewRequest(sol, returnTo = cs) }
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
                                                        is ActiveTasks -> {
                                                            val tareasViewModel: TareasViewModel = viewModel()
                                                            ActiveServiceTasksScreen(
                                                                solicitud = cs.solicitud,
                                                                tareasViewModel = tareasViewModel,
                                                                currentUserId = userData.uid,
                                                                onNavigateBack = { currentScreen = Requests },
                                                                onNavigateToChat = { chatId, otherUid, initialMsg ->
                                                                    currentScreen = Chat(chatId, otherUid, returnTo = cs, initialMessage = initialMsg)
                                                                }
                                                            )
                                                        }
                                                        is ServiceReport -> {
                                                            InformeServicioScreen(
                                                                informe = cs.informe,
                                                                onNavigateBack = { currentScreen = Requests }
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

                                            val showMenuButton = currentScreen == Main || currentScreen == Chats || 
                                                               currentScreen == Notifications || currentScreen == Requests
                                            
                                            if (showMenuButton) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .windowInsetsPadding(WindowInsets.statusBars)
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.align(Alignment.CenterEnd),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (currentScreen == Main) {
                                                            IconButton(
                                                                onClick = { searchViewModel.refresh() },
                                                                modifier = Modifier
                                                                    .size(44.dp)
                                                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Refresh,
                                                                    "Recargar",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                        }

                                                        CuidaleCircularMenuButton(
                                                            onClick = { scope.launch { drawerState.open() } }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(),
                        exit = fadeOut(tween(1000, easing = LinearOutSlowInEasing))
                    ) {
                        CuidaleWelcomeLoading()
                    }
                }
            }
        }
    }
