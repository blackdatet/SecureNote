package ru.mirea.securenote

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

private const val LOCAL_NETWORK_PERMISSION =
    "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun BackendConnectionCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var requestingPermission by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf("Проверка ещё не выполнена")
    }

    fun checkConnection() {
        if (checking) return

        checking = true
        message = "Проверяем подключение…"

        scope.launch {
            try {
                val response = ApiClient.api.health()

                message = if (
                    response.status == "ok" &&
                    response.service == "SecureNote backend"
                ) {
                    "Backend доступен"
                } else {
                    "Получен неожиданный ответ backend"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                message = "Ошибка backend: ${error.code()}"
            } catch (error: IOException) {
                message = "Нет подключения. Проверь, запущен ли backend."
            } catch (error: Exception) {
                message = "Не удалось прочитать ответ backend"
            } finally {
                checking = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        requestingPermission = false

        if (granted) {
            checkConnection()
        } else {
            message = "Для подключения нужен доступ к локальной сети."
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Подключение к backend",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                enabled = !checking && !requestingPermission,
                onClick = {
                    val permissionNeeded =
                        Build.VERSION.SDK_INT >= 37 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            LOCAL_NETWORK_PERMISSION
                        ) != PackageManager.PERMISSION_GRANTED

                    if (permissionNeeded) {
                        requestingPermission = true
                        message = "Ожидаем разрешение на доступ к локальной сети…"
                        permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                    } else {
                        checkConnection()
                    }
                }
            ) {
                Text(
                    when {
                        requestingPermission -> "Ожидаем разрешение…"
                        checking -> "Проверяем…"
                        else -> "Проверить подключение"
                    }
                )
            }
        }
    }
}
