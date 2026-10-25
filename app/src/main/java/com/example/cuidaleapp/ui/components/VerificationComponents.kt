package com.example.cuidaleapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import java.util.concurrent.TimeUnit

fun formatVerificationTimer(timeLeft: Long): String {
    if (timeLeft <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
}

@Composable
fun VerificationTimerText(timeLeft: Long, modifier: Modifier = Modifier) {
    val color = if (timeLeft in 1 until 60000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Text(
        text = stringResource(R.string.verification_code_expires, formatVerificationTimer(timeLeft)),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = modifier
    )
}

@Composable
fun VerificationAttemptsText(attemptsLeft: Int, modifier: Modifier = Modifier) {
    val attemptsColor by animateColorAsState(
        targetValue = if (attemptsLeft <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        label = "attemptsColor"
    )
    Text(
        text = stringResource(R.string.verification_attempts_left, attemptsLeft),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = attemptsColor,
        modifier = modifier
    )
}

@Composable
fun VerificationResendLink(
    timeLeft: Long,
    enabled: Boolean,
    onResend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canResend = timeLeft <= 0 && enabled
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.verification_not_received),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (canResend) stringResource(R.string.verification_resend) else stringResource(R.string.verification_resend_in, formatVerificationTimer(timeLeft)),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (canResend) CuidaleThemeExtras.customColors.linkColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(enabled = canResend) { onResend() }
        )
    }
}

@Composable
fun CodeVerificationStep(
    title: String,
    targetLabel: String,
    targetValue: String,
    code: String,
    onCodeChange: (String) -> Unit,
    timeLeft: Long,
    attemptsLeft: Int,
    onResend: () -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
    verifyLabel: String,
    backLabel: String,
    isDark: Boolean,
    isLoading: Boolean,
    error: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    val isExpired = timeLeft <= 0L
    val isBlocked = isExpired || attemptsLeft <= 0 || isLoading
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = panelShadowColor,
            isError = error != null || attemptsLeft <= 0
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val annotatedText = buildAnnotatedString {
                append(targetLabel)
                withStyle(style = SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                    append(targetValue)
                }
            }

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            VerificationTimerText(timeLeft, modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(12.dp))

            VerificationAttemptsText(attemptsLeft, modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(modifier = Modifier.height(24.dp))

            if (isExpired && error == null) {
                Text(
                    text = stringResource(R.string.error_code_expired),
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (attemptsLeft <= 0 && error == null) {
                Text(
                    text = stringResource(R.string.error_attempts_exhausted),
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            InputFieldLabel(stringResource(R.string.verification_otp_label))

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            OtpInputField(
                otpText = code,
                onOtpTextChange = onCodeChange,
                enabled = !isBlocked,
                isError = error != null || attemptsLeft <= 0
            )

            Spacer(modifier = Modifier.height(16.dp))

            VerificationResendLink(
                timeLeft = timeLeft,
                enabled = !isLoading,
                onResend = onResend
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isExpired || attemptsLeft <= 0) {
            HardShadowButton(
                text = stringResource(R.string.verification_resend),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = panelShadowColor,
                icon = Icons.Default.Refresh,
                isDark = isDark,
                enabled = !isLoading,
                onClick = onResend
            )
        } else {
            HardShadowButton(
                text = verifyLabel,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = panelShadowColor,
                icon = Icons.Default.CheckCircle,
                isDark = isDark,
                enabled = code.length == 6 && !isBlocked,
                onClick = onVerify
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack, enabled = !isLoading) {
            Text(backLabel, color = Color.White)
        }
    }
}
