package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAddChannel: (String) -> Unit
) {
    var channelInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val presetChannels = listOf(
        "digiato" to "دیجیاتو",
        "akharinkhabar" to "آخرین خبر",
        "varzesh3" to "ورزش ۳",
        "isna94" to "خبرگزاری ایسنا",
        "zoomit_official" to "زومیت"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "افزودن کانال تلگرام",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "آیدی کانال، نام کاربری یا لینک آن را وارد کنید (مثلاً: digiato یا https://t.me/akharinkhabar):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = channelInput,
                    onValueChange = {
                        channelInput = it
                        isError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_input_field"),
                    label = { Text("نام کاربری یا لینک کانال") },
                    placeholder = { Text("مثال: digiato") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null)
                    },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("لطفاً یک نام کاربری معتبر وارد کنید") }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "کانال‌های پیشنهادی پرطرفدار:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetChannels.forEach { (username, title) ->
                        SuggestionChip(
                            onClick = { channelInput = username },
                            label = { Text(text = "$title (@$username)") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (channelInput.isNotBlank()) {
                        onAddChannel(channelInput)
                        onDismiss()
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.testTag("confirm_add_channel_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text(text = "افزودن کانال", modifier = Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_channel_button")
            ) {
                Text("انصراف")
            }
        }
    )
}
