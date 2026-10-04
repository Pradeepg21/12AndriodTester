package com.satya.a12andriodtester.ui.auth

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.satya.a12andriodtester.R
import com.satya.a12andriodtester.data.AuthRepository
import com.satya.a12andriodtester.ui.rememberActionRunner
import com.satya.a12andriodtester.ui.theme.BrandGradient
import com.satya.a12andriodtester.ui.theme.Indigo

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val runner = rememberActionRunner()
    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val canSubmit = emailValid && password.length >= 6 && (!signUp || name.isNotBlank())

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(BrandGradient)
                .statusBarsPadding()
                .padding(top = 40.dp, bottom = 70.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Verified, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                Spacer(Modifier.height(12.dp))
                Text("Twelve Testers", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    "Closed testing made simple",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Card(
            Modifier.padding(horizontal = 20.dp).offset(y = (-40).dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(6.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (signUp) "Create your account" else "Welcome back",
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedButton(
                    onClick = { runner.run { googleSignIn(context) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !runner.busy.value,
                ) {
                    Text("G", fontWeight = FontWeight.Black, color = Indigo)
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f))
                }

                if (signUp) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Full name") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    email, { email = it.filterNot(Char::isWhitespace) }, label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                    isError = email.isNotBlank() && !emailValid,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password (min 6 characters)") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle password")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        runner.run {
                            if (signUp) AuthRepository.signUpWithEmail(name, email, password)
                            else AuthRepository.signInWithEmail(email, password)
                        }
                    },
                    enabled = canSubmit && !runner.busy.value,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                ) {
                    if (runner.busy.value) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (signUp) "Sign up" else "Sign in")
                }

                if (!signUp) {
                    TextButton(
                        onClick = {
                            if (!emailValid) {
                                Toast.makeText(context, "Enter your email first", Toast.LENGTH_SHORT).show()
                            } else {
                                runner.run("Password reset email sent") { AuthRepository.sendPasswordReset(email) }
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Forgot password?") }
                }
            }
        }

        TextButton(
            onClick = { signUp = !signUp },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).navigationBarsPadding(),
        ) {
            Text(
                if (signUp) "Already have an account? Sign in" else "New here? Create an account",
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private suspend fun googleSignIn(context: Context) {
    val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(context).getCredential(context, request)
    val credential = result.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        AuthRepository.signInWithGoogle(google.idToken)
    } else {
        error("Unexpected credential type")
    }
}
