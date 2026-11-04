package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun CuidaleTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        HardShadowPanel(
            backgroundColor = MaterialTheme.colorScheme.surface,
            shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f),
            cornerRadius = 28.dp,
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.cuidale_time_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), 
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { 
                        Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold, color = Color.Gray) 
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        Text(stringResource(R.string.action_accept), fontWeight = FontWeight.Black) 
                    }
                }
            }
        }
    }
}
