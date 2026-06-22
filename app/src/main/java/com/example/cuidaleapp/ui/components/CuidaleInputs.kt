package com.example.cuidaleapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun InputFieldLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun CuidaleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = Color.Unspecified,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = LocalTextStyle.current.copy(
            textAlign = textAlign,
            color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface
        ),
        placeholder = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if(textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = textAlign
                )
            }
        },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (isPassword && onPasswordVisibilityChange != null) {
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, contentDescription = null)
                }
            } else {
                trailingIcon?.invoke()
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = if (isError) borderColor.copy(alpha = 0.55f) else Color.Transparent,
            errorBorderColor = borderColor
        )
    )
}

@Composable
fun OtpInputField(otpText: String, onOtpTextChange: (String) -> Unit, modifier: Modifier = Modifier, otpCount: Int = 6, isError: Boolean = false, enabled: Boolean = true) {
    val focusRequesters = remember { List(otpCount) { FocusRequester() } }
    LaunchedEffect(Unit) { if (enabled && otpText.length < otpCount) focusRequesters[otpText.length].requestFocus() }
    BasicTextField(
        value = otpText,
        onValueChange = { if (it.length <= otpCount && it.all { char -> char.isDigit() }) onOtpTextChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled,
        decorationBox = {
            Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(otpCount) { index ->
                    val char = when { index >= otpText.length -> "" else -> otpText[index].toString() }
                    val isFocused = index == otpText.length
                    OtpCharBox(
                        char = char,
                        isFocused = isFocused && enabled,
                        isError = isError,
                        isDark = CuidaleThemeExtras.customColors.isDark,
                        modifier = Modifier.weight(1f).focusRequester(focusRequesters[index])
                    )
                }
            }
        }
    )
}

@Composable
private fun OtpCharBox(char: String, isFocused: Boolean, isError: Boolean, isDark: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val customColors = CuidaleThemeExtras.customColors
    val borderColor by animateColorAsState(targetValue = when { isError -> colors.error; isFocused -> colors.primary; else -> if (isDark) Color.White.copy(alpha = 0.35f) else colors.outline.copy(alpha = 0.25f) }, label = "otpBorderColor")
    val borderWidth by animateDpAsState(targetValue = if (isFocused || isError) 2.dp else 1.dp, label = "otpBorderWidth")
    Box(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(customColors.inputBackground).border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = char, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = if (isError) colors.error else colors.onSurface)
    }
}
