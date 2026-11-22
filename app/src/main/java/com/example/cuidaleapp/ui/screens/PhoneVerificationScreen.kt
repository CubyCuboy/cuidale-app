package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.PhoneVerificationViewModel
import com.example.cuidaleapp.viewmodel.PhoneVerificationUiState
import android.app.Activity
import androidx.compose.foundation.shape.CircleShape

@Composable
fun PhoneVerificationScreen(
    isDark: Boolean,
    onNavigateBack: () -> Unit,
    onVerificationSuccess: () -> Unit,
    viewModel: PhoneVerificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val phone by viewModel.phoneNumber.collectAsState()
    val code by viewModel.code.collectAsState()
    val timeLeft by viewModel.timeLeft.collectAsState()
    val attemptsLeft by viewModel.attemptsLeft.collectAsState()
    val context = LocalContext.current

    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val stepsLabels = listOf(
        stringResource(R.string.verification_step_phone),
        stringResource(R.string.verification_step_code),
        stringResource(R.string.verification_step_done)
    )
    
    val currentStepIndex = when(uiState) {
        is PhoneVerificationUiState.Step1Phone -> 0
        is PhoneVerificationUiState.Step2Code -> 1
        is PhoneVerificationUiState.Success -> 2
        is PhoneVerificationUiState.Loading -> if (timeLeft > 0) 1 else 0
        is PhoneVerificationUiState.Error -> if (timeLeft > 0) 1 else 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = onGradientText
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.verification_title_phone),
                style = MaterialTheme.typography.displaySmall,
                color = onGradientText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleStepProgressBar(
                steps = stepsLabels,
                currentStepIndex = currentStepIndex
            )

            Spacer(modifier = Modifier.height(32.dp))

            Crossfade(targetState = uiState, animationSpec = tween(500), label = "phoneVerificationTransition") { state ->
                val error = if (state is PhoneVerificationUiState.Error) state.message else null
                
                when (state) {
                    is PhoneVerificationUiState.Step1Phone -> {
                        Step1PhoneContent(
                            phone = phone,
                            onPhoneChange = viewModel::onPhoneChange,
                            onSend = { viewModel.sendVerificationCode(context as Activity) },
                            isDark = isDark,
                            isLoading = false,
                            error = error
                        )
                    }
                    is PhoneVerificationUiState.Step2Code -> {
                        Step2CodeContent(
                            code = code,
                            phone = phone,
                            timeLeft = timeLeft,
                            attemptsLeft = attemptsLeft,
                            onCodeChange = viewModel::onCodeChange,
                            onVerify = viewModel::verifyCode,
                            onResend = { viewModel.sendVerificationCode(context as Activity) },
                            onBack = viewModel::resetToStep1,
                            isDark = isDark,
                            isLoading = false,
                            error = error
                        )
                    }
                    is PhoneVerificationUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = onGradientText)
                        }
                    }
                    is PhoneVerificationUiState.Success -> {
                        SuccessContent(onVerificationSuccess)
                    }
                    is PhoneVerificationUiState.Error -> {
                        if (timeLeft > 0 || code.isNotEmpty()) {
                             Step2CodeContent(
                                code = code, phone = phone, timeLeft = timeLeft, attemptsLeft = attemptsLeft,
                                onCodeChange = viewModel::onCodeChange, onVerify = viewModel::verifyCode, 
                                onResend = { viewModel.sendVerificationCode(context as Activity) }, 
                                onBack = viewModel::resetToStep1, isDark = isDark, isLoading = false, error = error
                            )
                        } else {
                            Step1PhoneContent(
                                phone = phone, onPhoneChange = viewModel::onPhoneChange, 
                                onSend = { viewModel.sendVerificationCode(context as Activity) }, 
                                isDark = isDark, isLoading = false, error = error
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun Step1PhoneContent(
    phone: String,
    onPhoneChange: (String) -> Unit,
    onSend: () -> Unit,
    isDark: Boolean,
    isLoading: Boolean,
    error: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = panelShadowColor,
            isError = error != null
        ) {
            Text(
                text = stringResource(R.string.phone_verification_enter),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel(stringResource(R.string.phone_number_label))
            
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            CuidaleTextField(
                value = phone,
                onValueChange = onPhoneChange,
                placeholder = stringResource(R.string.phone_placeholder),
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                keyboardType = KeyboardType.Phone,
                enabled = !isLoading,
                isError = error != null,
                leadingIcon = {
                    Text(stringResource(R.string.phone_country_prefix), modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = stringResource(R.string.phone_send_sms),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            shadowColor = panelShadowColor,
            icon = Icons.Default.Phone,
            isDark = isDark,
            enabled = phone.length >= 8 && !isLoading,
            onClick = onSend
        )
    }
}

@Composable
fun Step2CodeContent(
    code: String,
    phone: String,
    timeLeft: Long,
    attemptsLeft: Int,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean,
    isLoading: Boolean,
    error: String? = null
) {
    CodeVerificationStep(
        title = stringResource(R.string.verification_phone_code_title),
        targetLabel = stringResource(R.string.verification_code_sent_phone),
        targetValue = phone,
        code = code,
        onCodeChange = onCodeChange,
        timeLeft = timeLeft,
        attemptsLeft = attemptsLeft,
        onResend = onResend,
        onVerify = onVerify,
        onBack = onBack,
        verifyLabel = stringResource(R.string.verification_verify_phone_button),
        backLabel = stringResource(R.string.verification_change_phone_button),
        isDark = isDark,
        isLoading = isLoading,
        error = error
    )
}
