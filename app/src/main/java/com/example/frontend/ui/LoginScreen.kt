package com.example.frontend.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.emulator.metadata.RomhackingMetadataService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class FocusRequesters(
    val usernameFocus: FocusRequester = FocusRequester(),
    val passwordFocus: FocusRequester = FocusRequester(),
    val loginButtonFocus: FocusRequester = FocusRequester()
) {
    operator fun component1() = usernameFocus
    operator fun component2() = passwordFocus
    operator fun component3() = loginButtonFocus
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    sessionToken: String,
    onSessionTokenChanged: (String) -> Unit,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sm64_launcher_prefs", Context.MODE_PRIVATE) }
    
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val (usernameFocus, passwordFocus, loginButtonFocus) = remember { FocusRequesters() }

    val performLogin = {
        if (username.isNotEmpty() && password.isNotEmpty()) {
            isLoggingIn = true
            loginError = ""
            coroutineScope.launch {
                try {
                    val token = RomhackingMetadataService.loginToRhdc(username, password)
                    prefs.edit().putString("session_token", token).apply()
                    onSessionTokenChanged(token)
                } catch (e: Exception) {
                    loginError = e.message ?: "Authentication failed."
                } finally {
                    isLoggingIn = false
                }
            }
        } else {
            loginError = "Please fill in both fields."
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        usernameFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .height(350.dp)
                .padding(16.dp)
                .border(1.dp, Color(0xFFFFCC00), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column (Visual Branding)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFCC00), Color(0xFFFF8800))
                            )
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ParaLLEl Lite",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Romhacking.com",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black.copy(alpha = 0.8f)
                    )
                }

                // Right Column (Inputs & Button)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCC00)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Username Field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFCC00),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFFCC00),
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(usernameFocus)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    usernameFocus.requestFocus()
                                    val activity = context as? Activity
                                    activity?.window?.let { window ->
                                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.ime())
                                    }
                                }
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    val activity = context as? Activity
                                    activity?.window?.let { window ->
                                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.ime())
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.DirectionDown || keyEvent.key == Key.Enter)) {
                                    passwordFocus.requestFocus()
                                    true
                                } else false
                            }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFCC00),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFFCC00),
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    passwordFocus.requestFocus()
                                    val activity = context as? Activity
                                    activity?.window?.let { window ->
                                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.ime())
                                    }
                                }
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    val activity = context as? Activity
                                    activity?.window?.let { window ->
                                        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.ime())
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.DirectionDown || keyEvent.key == Key.Enter)) {
                                    loginButtonFocus.requestFocus()
                                    true
                                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    usernameFocus.requestFocus()
                                    true
                                } else false
                            }
                    )

                    if (loginError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = loginError, color = Color.Red, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button
                    Button(
                        onClick = { performLogin() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC00)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(loginButtonFocus)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.ButtonA || keyEvent.key == Key.Enter)) {
                                    performLogin()
                                    true
                                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    passwordFocus.requestFocus()
                                    true
                                } else false
                            },
                        enabled = !isLoggingIn
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Text("Sign In", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
