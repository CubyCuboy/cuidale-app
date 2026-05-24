package com.example.cuidaleapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable

@Composable
fun InputFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
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
    containerColor: Color,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    isError: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
) {
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(textAlign = textAlign),
        placeholder = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if(textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = textAlign
                )
            }
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
            keyboardType = keyboardType
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
    isError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(isError) {
        if (isError) {
            repeat(6) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 12f else -12f,
                    animationSpec = tween(durationMillis = 60, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    val finalBg = if (isError) colors.errorContainer.copy(alpha = 0.95f) else backgroundColor
    val finalShadow = if (isError) colors.error else shadowColor

    val animatedBg by animateColorAsState(
        targetValue = finalBg,
        animationSpec = tween(400),
        label = "panelBg"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
    ) {
        // Shadow (No animada en color, solo offset fijo)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(shadowColor)
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val finalContainerColor = if (!enabled) {
        if (isDark) Color.Gray.copy(alpha = 0.3f) else Color.LightGray
    } else if (isErrorMode) {
        colors.error
    } else {
        containerColor
    }

    val finalContentColor = if (!enabled) {
        if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray
    } else if (isErrorMode) {
        Color.White
    } else {
        contentColor
    }

    val finalShadowColor = if (!enabled) {
        Color.Transparent
    } else if (isErrorMode) {
        colors.errorContainer
    } else {
        shadowColor
    }

    val buttonBrush = if (!enabled || isErrorMode) {
        Brush.linearGradient(listOf(finalContainerColor, finalContainerColor))
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
            .height(58.dp)
    ) {
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = 6.dp, y = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(finalShadowColor)
            )
        }

        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = finalContentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = finalContentColor
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
                            tint = finalContentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = finalContentColor
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
    unmetIndices: Set<Int> = emptySet(),
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
            validations.forEachIndexed { index, (text, isValid) ->
                ValidationItem(
                    text = text,
                    isValid = isValid,
                    isError = unmetIndices.contains(index),
                    isDark = isDark
                )
            }
        }
    }
}

@Composable
fun ValidationItem(text: String, isValid: Boolean, isError: Boolean, isDark: Boolean) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = if (isDark) customColors.successStrong else Color(0xFF00C853) // Verde vibrante
    val errorColor = MaterialTheme.colorScheme.error
    val inactiveColor = if (isDark) Color.Gray else Color(0xFF455A64) // Gris azulado nítido

    val color by animateColorAsState(
        targetValue = when {
            isValid -> successColor
            isError -> errorColor
            else -> inactiveColor
        },
        animationSpec = tween(300),
        label = "validationColor"
    )

    val icon = when {
        isValid -> Icons.Default.Check
        isError -> Icons.Default.Close
        else -> Icons.Default.Check
    }

    val shakeOffset by animateDpAsState(
        targetValue = if (isError) 6.dp else 0.dp,
        animationSpec = if (isError) {
            repeatable(
                iterations = 4,
                animation = tween(durationMillis = 50, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(0)
        },
        label = "shake"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier
            .padding(vertical = 2.dp)
            .offset { IntOffset(shakeOffset.toPx().roundToInt(), 0) }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
            ),
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuidaleExposedDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    placeholder: String = "Seleccionar..."
) {
    var expanded by remember { mutableStateOf(false) }
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = isError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = customColors.inputBackground,
                    unfocusedContainerColor = customColors.inputBackground,
                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.1f),
                    errorContainerColor = customColors.inputBackground,
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = if (isError) borderColor.copy(alpha = 0.8f) else Color.Transparent,
                    errorBorderColor = borderColor,
                    disabledBorderColor = Color.Transparent.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(customColors.panelBackground)
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = selectionOption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onOptionSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

