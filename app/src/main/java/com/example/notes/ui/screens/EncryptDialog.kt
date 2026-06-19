package com.example.notes.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** 进阶功能: 加密/解密密码输入对话框 */
enum class EncryptMode { Encrypt, Decrypt }

@Composable
fun EncryptDialog(
    mode: EncryptMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val title = if (mode == EncryptMode.Encrypt) "加密笔记" else "解密笔记"
    val description = if (mode == EncryptMode.Encrypt)
        "请输入密码, 加密后只能通过密码查看内容。忘记密码将无法找回!"
    else "请输入密码以解锁笔记内容"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (mode == EncryptMode.Encrypt) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("再次输入") },
                        singleLine = true,
                        isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (mode == EncryptMode.Encrypt) {
                        if (password.length >= 4 && password == confirmPassword) {
                            onConfirm(password)
                        }
                    } else {
                        if (password.isNotEmpty()) onConfirm(password)
                    }
                }
            ) {
                Text(if (mode == EncryptMode.Encrypt) "加密" else "解密")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

