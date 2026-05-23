package com.example.cuidaleapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun InputFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun CuidaleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    isError: Boolean = false
) {
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword && !isPasswordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = {
            if (isPassword && onPasswordVisibilityChange != null) {
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = "Ver u ocultar contraseña"
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
        ),
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
fun HardShadowPanel(
    backgroundColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(400),
        label = "panelBg"
    )

    val animatedShadow by animateColorAsState(
        targetValue = shadowColor,
        animationSpec = tween(400),
        label = "panelShadow"
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 7.dp, y = 7.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(animatedShadow)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(animatedBg)
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun HardShadowButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isErrorMode: Boolean = false,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val buttonBrush = if (isErrorMode) {
        Brush.linearGradient(listOf(containerColor, containerColor))
    } else if (isDark) {
        Brush.linearGradient(
            listOf(
                containerColor,
                colors.primary.copy(alpha = 0.82f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                containerColor,
                containerColor.copy(alpha = 0.92f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shadowColor)
        )

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(buttonBrush),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun CuidaleStepProgressBar(
    steps: List<String>,
    currentStepIndex: Int
) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = customColors.successStrong

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val isCompleted = index < currentStepIndex
            val isCurrent = index == currentStepIndex
            val isActive = isCompleted || isCurrent

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = successColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) successColor else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    if (index < currentStepIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CuidaleValidationPanel(
    validations: List<Pair<String, Boolean>>,
    isDark: Boolean
) {
    val allRequirementsMet = validations.all { it.second }

    val requirementsPanelBg = if (allRequirementsMet) {
        if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFDFF6E5)
    } else {
        if (isDark) Color.White.copy(alpha = 0.05f) else Color.White
    }

    HardShadowPanel(
        backgroundColor = requirementsPanelBg,
        shadowColor = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            validations.forEach { (text, isValid) ->
                ValidationItem(text = text, isValid = isValid, isDark = isDark)
            }
        }
    }
}

@Composable
fun ValidationItem(text: String, isValid: Boolean, isDark: Boolean) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = customColors.successStrong
    val inactiveColor = if (isDark) Color.Gray else Color.Black.copy(alpha = 0.55f)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (isValid) successColor else inactiveColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) successColor else inactiveColor
        )
    }
}
