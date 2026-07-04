package com.parallellite.launcher.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parallellite.launcher.R
import com.parallellite.launcher.ui.launcher.LoginUiState
import com.parallellite.launcher.ui.theme.BrandColors

/**
 * Full-screen modal sign-in. Single centered card, keyboard-aware (scrolls above
 * the IME), with Next/Done actions and a show/hide password toggle. Purely
 * presentational — all auth logic lives in the ViewModel.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onLogin: (username: String, password: String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val submit = { onLogin(username.trim(), password) }

    // Auto-focus the username field and raise the IME on first display.
    LaunchedEffect(Unit) {
        usernameFocus.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.Outline, RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = BrandColors.MarioGold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.login_signin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandColors.MarioGold,
                unfocusedBorderColor = BrandColors.Outline,
                focusedLabelColor = BrandColors.MarioGold,
                unfocusedLabelColor = BrandColors.TextMuted,
                focusedTextColor = BrandColors.TextPrimary,
                unfocusedTextColor = BrandColors.TextPrimary,
                cursorColor = BrandColors.MarioGold,
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocus)
                    .onFocusChanged { if (it.isFocused) keyboard?.show() },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = fieldColors,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); submit() }),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = if (passwordVisible) stringResource(R.string.login_hide) else stringResource(R.string.login_show),
                            color = BrandColors.MarioGold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .onFocusChanged { if (it.isFocused) keyboard?.show() },
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error,
                    color = BrandColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { keyboard?.hide(); submit() },
                enabled = !state.isLoggingIn,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.MarioGold,
                    disabledContainerColor = BrandColors.SurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(R.string.login_sign_in),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
