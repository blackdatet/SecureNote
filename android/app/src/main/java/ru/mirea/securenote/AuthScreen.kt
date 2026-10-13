package ru.mirea.securenote

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private const val AUTH_LOCAL_NETWORK_PERMISSION =
    "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val profile = viewModel.user

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SecureNote",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            if (profile == null) {
                AuthForm(viewModel)
            } else {
                Text(
                    text = "Вход выполнен",
                    style = MaterialTheme.typography.titleLarge
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Учётная запись",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(text = profile.email)
                    }
                }

                viewModel.message?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = viewModel::logout,
                    enabled = !viewModel.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (viewModel.busy) "Выходим…" else "Выйти"
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthForm(viewModel: AuthViewModel) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }

    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }

    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val busy = viewModel.busy || permissionPending

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionPending = false

        if (granted) {
            viewModel.authenticate(email, password, register)
        } else {
            localError = "Для входа нужен доступ к локальной сети."
        }
    }

    fun submit() {
        if (busy) return

        localError = null
        viewModel.clearMessage()

        if (email.isBlank() || password.isBlank() || (register && confirmation.isBlank())) {
            localError = "Заполни все поля."
            return
        }

        if (register && password != confirmation) {
            localError = "Пароли не совпадают."
            return
        }

        keyboard?.hide()

        val permissionNeeded =
            Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(
                context,
                AUTH_LOCAL_NETWORK_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED

        if (permissionNeeded) {
            permissionPending = true
            permissionLauncher.launch(AUTH_LOCAL_NETWORK_PERMISSION)
        } else {
            viewModel.authenticate(email, password, register)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (register) "Регистрация" else "Вход",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = if (register) {
                "Создай учётную запись для личного журнала инцидентов."
            } else {
                "Войди в свою учётную запись."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it.take(254)
                localError = null
                viewModel.clearMessage()
            },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (!busy) passwordFocus.requestFocus() }
            ),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it.take(128)
                localError = null
                viewModel.clearMessage()
            },
            label = { Text("Пароль") },
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (register) ImeAction.Next else ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (!busy && register) confirmationFocus.requestFocus() },
                onDone = { submit() }
            ),
            trailingIcon = {
                TextButton(
                    onClick = { showPassword = !showPassword },
                    enabled = !busy
                ) {
                    Text(if (showPassword) "Скрыть" else "Показать")
                }
            },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus)
        )

        if (register) {
            Text(
                text = "Пароль: от 8 до 128 символов.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = confirmation,
                onValueChange = {
                    confirmation = it.take(128)
                    localError = null
                    viewModel.clearMessage()
                },
                label = { Text("Повтори пароль") },
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().focusRequester(confirmationFocus)
            )
        }

        val error = localError ?: viewModel.message

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = { submit() },
            enabled = !busy &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                (!register || confirmation.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    permissionPending -> "Ожидаем разрешение…"
                    viewModel.busy -> "Подождите…"
                    register -> "Создать учётную запись"
                    else -> "Войти"
                }
            )
        }

        TextButton(
            onClick = {
                register = !register
                password = ""
                confirmation = ""
                showPassword = false
                localError = null
                viewModel.clearMessage()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (register) {
                    "Уже есть учётная запись? Войти"
                } else {
                    "Нет учётной записи? Зарегистрироваться"
                }
            )
        }
    }
}
