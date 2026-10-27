package com.phantom.mesh.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DuressCrimson
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.NeonGreen
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import com.phantom.mesh.util.SecurityQuestions

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: (isDuress: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var decoyPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var decoyPasswordVisible by remember { mutableStateOf(false) }

    // Security Question state for first-time registration
    var enableSecurityQuestion by remember { mutableStateOf(false) }
    var selectedQuestion by remember { mutableStateOf(SecurityQuestions.QUESTIONS.first()) }
    var securityAnswer by remember { mutableStateOf("") }
    var showQuestionPickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onAuthSuccess(state.isDuressTriggered)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard.copy(alpha = 0.85f))
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Security Shield
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "PHANTOM MESH",
                style = MaterialTheme.typography.headlineLarge,
                color = NeonCyan,
                letterSpacing = 2.sp
            )

            Text(
                text = "Zero-Trace Hybrid Communicator",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "v2.2.1",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (Alphanumeric)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = NeonCyan) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passphrase") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonCyan) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = if (passwordVisible) NeonCyan else TextMuted
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Forgot Passphrase link in Login mode
            if (!state.isRegisterMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.openForgotPassword() }) {
                        Text(
                            text = "Forgot Passphrase?",
                            color = NeonCyan,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Optional Decoy Password for Register Mode
            AnimatedVisibility(visible = state.isRegisterMode) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = decoyPassword,
                        onValueChange = { decoyPassword = it },
                        label = { Text("Decoy Duress Passphrase (Optional)") },
                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = DuressCrimson) },
                        trailingIcon = {
                            IconButton(onClick = { decoyPasswordVisible = !decoyPasswordVisible }) {
                                Icon(
                                    imageVector = if (decoyPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (decoyPasswordVisible) "Hide decoy password" else "Show decoy password",
                                    tint = if (decoyPasswordVisible) DuressCrimson else TextMuted
                                )
                            }
                        },
                        visualTransformation = if (decoyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DuressCrimson,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entering decoy passphrase on login will nuke local database and present a dummy session.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DuressCrimson.copy(alpha = 0.8f),
                        fontSize = 9.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional Security Recovery Question for first-time registration
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground.copy(alpha = 0.6f))
                            .border(1.dp, if (enableSecurityQuestion) NeonGreen.copy(alpha = 0.4f) else TextMuted.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Setup Security Recovery Question",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (enableSecurityQuestion) NeonGreen else TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Optional - Enables password recovery if forgotten",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Switch(
                                    checked = enableSecurityQuestion,
                                    onCheckedChange = { enableSecurityQuestion = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonGreen,
                                        checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = DarkCard
                                    )
                                )
                            }

                            if (enableSecurityQuestion) {
                                Spacer(modifier = Modifier.height(10.dp))

                                // Question selector box
                                Text(
                                    text = "Select Security Question:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkCard)
                                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { showQuestionPickerDialog = true }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = selectedQuestion,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = NeonCyan
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = securityAnswer,
                                    onValueChange = { securityAnswer = it },
                                    label = { Text("Your Secret Answer") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonGreen,
                                        unfocusedBorderColor = TextMuted,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Answer is hashed with PBKDF2/SHA-256 for cryptographic zero-knowledge verification.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            // Error Display
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.errorMessage ?: "",
                    color = DuressCrimson,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    viewModel.submit(
                        username = username,
                        password = password,
                        decoyPassword = if (state.isRegisterMode) decoyPassword.ifBlank { null } else null,
                        securityQuestion = if (state.isRegisterMode && enableSecurityQuestion && securityAnswer.isNotBlank()) selectedQuestion else null,
                        securityAnswer = if (state.isRegisterMode && enableSecurityQuestion && securityAnswer.isNotBlank()) securityAnswer else null
                    )
                },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = DarkBackground
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DarkBackground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (state.isRegisterMode) "REGISTER IDENTITY" else "AUTHENTICATE",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Toggle
            TextButton(
                onClick = { viewModel.toggleMode() }
            ) {
                Text(
                    text = if (state.isRegisterMode) "Already have an identity? Sign In" else "Create new anonymous identity",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Question Picker Dialog (30 tough questions)
    if (showQuestionPickerDialog) {
        Dialog(onDismissRequest = { showQuestionPickerDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Choose Security Question",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showQuestionPickerDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    Text(
                        text = "Select a complex question only you can answer:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(SecurityQuestions.QUESTIONS) { q ->
                            val isSelected = q == selectedQuestion
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        selectedQuestion = q
                                        showQuestionPickerDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = q,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) NeonCyan else TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    // Forgot Password Recovery Dialog
    if (state.isForgotPwDialogVisible) {
        ForgotPasswordRecoveryDialog(
            state = state,
            onClose = { viewModel.closeForgotPassword() },
            onFetchQuestion = { u -> viewModel.fetchRecoveryQuestion(u) },
            onResetPassword = { u, a, p -> viewModel.resetPassword(u, a, p) }
        )
    }
}

@Composable
private fun ForgotPasswordRecoveryDialog(
    state: AuthUiState,
    onClose: () -> Unit,
    onFetchQuestion: (username: String) -> Unit,
    onResetPassword: (username: String, answer: String, newPass: String) -> Unit
) {
    var recoveryUsername by remember { mutableStateOf(state.recoveryUsername) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET PASSPHRASE",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Step 1: Identify Username
                if (state.recoveryQuestion == null) {
                    Text(
                        text = "Enter your username to look up your cryptographic security question.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = recoveryUsername,
                        onValueChange = { recoveryUsername = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = NeonCyan) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onFetchQuestion(recoveryUsername.trim()) },
                        enabled = recoveryUsername.isNotBlank() && !state.recoveryLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = DarkBackground
                        )
                    ) {
                        if (state.recoveryLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBackground)
                        } else {
                            Text("FIND SECURITY QUESTION", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Step 2: Answer question and input new password
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground)
                            .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Security Question for @${state.recoveryUsername}:",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.recoveryQuestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = { recoveryAnswer = it },
                        label = { Text("Your Secret Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Passphrase (min 6 chars)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonCyan) },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (newPasswordVisible) NeonCyan else TextMuted
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { onResetPassword(state.recoveryUsername, recoveryAnswer.trim(), newPassword) },
                        enabled = recoveryAnswer.isNotBlank() && newPassword.length >= 6 && !state.recoveryLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = DarkBackground
                        )
                    ) {
                        if (state.recoveryLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBackground)
                        } else {
                            Text("RESET & SIGN IN", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Recovery Error
                if (state.recoveryError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.recoveryError,
                        color = DuressCrimson,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }

                // Recovery Success Message
                if (state.recoverySuccessMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.recoverySuccessMessage,
                        color = NeonGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
