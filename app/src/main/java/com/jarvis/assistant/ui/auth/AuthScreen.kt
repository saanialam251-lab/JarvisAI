package com.jarvis.assistant.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.jarvis.assistant.ui.theme.Cyan

/**
 * Login / sign-up — three options in one screen:
 *   [Google] | [Email + password] | [Phone number + OTP]
 */
@Composable
fun AuthScreen(
    onSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(res.data)
                .getResult(ApiException::class.java)
            vm.googleTokenResult(account.idToken)
        } catch (e: Exception) { /* cancelled / not configured */ }
    }

    LaunchedEffect(state.success) { if (state.success) onSuccess() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("J A R V I S", style = MaterialTheme.typography.headlineLarge, color = Cyan)
            Text("Your voice-first Android assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))

            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Google") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Email") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Phone") })
            }
            Spacer(Modifier.height(24.dp))

            when (tab) {
                0 -> GoogleTab { intent -> googleLauncher.launch(intent) }
                1 -> EmailTab(state, vm)
                2 -> PhoneTab(state, vm)
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = Cyan)
            }
        }
    }
}

@Composable
private fun GoogleTab(launch: (android.content.Intent) -> Unit) {
    val context = LocalContext.current
    Button(
        onClick = {
            // NOTE: add your Firebase web client id in AuthRepository.googleSignInIntent()
            val intent = GoogleSignIn.getClient(context,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail().build()).signInIntent
            launch(intent)
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) { Text("Continue with Google") }
    Spacer(Modifier.height(12.dp))
    Text("One-tap sign-in with your Google account",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmailTab(state: AuthUiState, vm: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isNew by remember { mutableStateOf(false) }

    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(),
        label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(),
        label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(isNew, { isNew = it })
        Text(if (isNew) "Create new account" else "I have an account",
            style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = { vm.email(email.trim(), pass, isNew) },
        Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
        Text(if (isNew) "Sign up" else "Sign in")
    }
}

@Composable
private fun PhoneTab(state: AuthUiState, vm: AuthViewModel) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    if (state.phoneVerificationId == null) {
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(),
            label = { Text("Phone number (+country code)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = { Icon(Icons.Default.Phone, null) })
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.phone(phone.trim()) }, Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)) { Text("Send OTP") }
    } else {
        Text(state.phoneHint ?: "Enter the 6-digit code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(),
            label = { Text("OTP code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.confirmOtp(code.trim()) }, Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)) { Text("Verify & sign in") }
    }
}
