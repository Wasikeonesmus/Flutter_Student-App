package com.examsystem.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.examsystem.app.data.models.User
import com.examsystem.app.ui.theme.*
import com.examsystem.app.viewmodel.InstructorViewModel
import com.examsystem.app.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorLoginScreen(
    vm: InstructorViewModel,
    onLoginSuccess: (User) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isResettingPassword by remember { mutableStateOf(false) }

    val loginState by vm.loginState.collectAsState()

    LaunchedEffect(loginState) {
        if (loginState is UiState.Success) {
            onLoginSuccess((loginState as UiState.Success<User>).data)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RedPrimary, Color.Black)
                )
            )
    ) {
        // Back Button
        IconButton(
            onClick = { onBack() },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Top Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("E", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ExamPro",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isResettingPassword) "Reset Password"
                       else if (isRegistering) "Create Account"
                       else "Welcome Back",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp
            )
        }

        // Bottom Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(520.dp),
            shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                if (isResettingPassword) {
                    // ── Forgot Password ──────────────────────────────────────
                    Text(
                        text = "Enter your registered email to receive a password reset link.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Gmail / Email") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.LightGray,
                            focusedIndicatorColor = RedPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                } else if (isRegistering) {
                    // ── Sign Up (single step — no verification code) ─────────
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.LightGray,
                            focusedIndicatorColor = RedPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Gmail / Email") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.LightGray,
                            focusedIndicatorColor = RedPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Create Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.LightGray,
                            focusedIndicatorColor = RedPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                } else {
                    // ── Sign In ───────────────────────────────────────────────
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Gmail / Email") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.LightGray,
                            focusedIndicatorColor = RedPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.LightGray,
                            focusedIndicatorColor = RedPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (loginState is UiState.Error) {
                    Text(
                        text = (loginState as UiState.Error).message,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        when {
                            isResettingPassword -> {
                                if (email.isBlank()) {
                                    Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                vm.sendPasswordReset(
                                    email = email.trim(),
                                    onSuccess = {
                                        Toast.makeText(context, "Password reset link sent to your email!", Toast.LENGTH_LONG).show()
                                        isResettingPassword = false
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                            isRegistering -> {
                                when {
                                    name.isBlank() -> Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                    email.isBlank() -> Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                                    password.length < 6 -> Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                    else -> vm.register(email.trim(), password, name.trim())
                                }
                            }
                            else -> {
                                if (email.isBlank() || password.isBlank()) {
                                    Toast.makeText(context, "Please enter all fields", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                vm.login(email.trim(), password)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = loginState !is UiState.Loading
                ) {
                    if (loginState is UiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        val btnText = when {
                            isResettingPassword -> "SEND RESET LINK"
                            isRegistering -> "CREATE ACCOUNT"
                            else -> "SIGN IN"
                        }
                        Text(text = btnText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isResettingPassword) {
                    Text(
                        text = "Back to Sign In",
                        color = Color.Gray,
                        modifier = Modifier.clickable {
                            isResettingPassword = false
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isRegistering) {
                            Text(
                                text = "Forgot Password?",
                                color = RedPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    isResettingPassword = true
                                }
                            )
                        }
                        Text(
                            text = if (isRegistering) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                            color = Color.Gray,
                            modifier = Modifier.clickable {
                                isRegistering = !isRegistering
                            }
                        )
                    }
                }
            }
        }
    }
}
