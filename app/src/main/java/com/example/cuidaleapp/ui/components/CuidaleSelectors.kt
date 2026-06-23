package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

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
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = isError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = customColors.inputBackground,
                    unfocusedContainerColor = customColors.inputBackground,
                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.1f),
                    errorContainerColor = customColors.inputBackground,
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = if (isError) borderColor.copy(alpha = 0.8f) else { if (customColors.isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) },
                    errorBorderColor = borderColor,
                    disabledBorderColor = Color.Transparent.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.background(customColors.panelBackground)) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(text = { Text(text = selectionOption, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onOptionSelected(selectionOption); expanded = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuidaleMultiSelectDropdown(
    label: String,
    options: List<String>,
    selectedOptions: List<String>,
    onOptionToggled: (String) -> Unit,
    maxSelection: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    placeholder: String = "Seleccionar..."
) {
    var expanded by remember { mutableStateOf(false) }
    val customColors = CuidaleThemeExtras.customColors

    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (selectedOptions.isEmpty()) "" else selectedOptions.joinToString(", "),
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = customColors.inputBackground,
                    unfocusedContainerColor = customColors.inputBackground,
                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.1f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (customColors.isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                    disabledBorderColor = Color.Transparent.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.background(customColors.panelBackground)) {
                options.forEach { option ->
                    val isSelected = selectedOptions.contains(option)
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isSelected, onCheckedChange = null); Spacer(modifier = Modifier.width(8.dp)); Text(text = option, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) } },
                        onClick = { if (isSelected || selectedOptions.size < maxSelection) onOptionToggled(option) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
        if (selectedOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedOptions) { option ->
                    SuggestionChip(onClick = { onOptionToggled(option) }, label = { Text(text = option, style = MaterialTheme.typography.labelLarge) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (customColors.isDark) 0.25f else 0.1f), labelColor = MaterialTheme.colorScheme.primary, iconContentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp), border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = if (customColors.isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), borderWidth = 1.dp))
                }
            }
        }
    }
}

@Composable
fun CuidaleBinaryChoice(label: String, selectedOption: String?, onOptionSelected: (String) -> Unit, extraContent: @Composable (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOptionSelected("Si") }) { RadioButton(selected = selectedOption == "Si", onClick = null); Text(text = "Si", modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface) }
            Spacer(modifier = Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOptionSelected("No") }) { RadioButton(selected = selectedOption == "No", onClick = null); Text(text = "No", modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface) }
        }
        if (selectedOption == "Si" && extraContent != null) { Spacer(modifier = Modifier.height(8.dp)); extraContent() }
    }
}
