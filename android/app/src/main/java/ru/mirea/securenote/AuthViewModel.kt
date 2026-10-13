package ru.mirea.securenote

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

class AuthViewModel : ViewModel() {
    var user by mutableStateOf<AuthUser?>(null)
        private set

    var accessToken by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        if (!busy) message = null
    }

    fun authenticate(
        email: String,
        password: String,
        register: Boolean
    ) {
        if (busy || user != null) return

        val normalizedEmail = email.trim().lowercase(Locale.ROOT)

        if (
            normalizedEmail.length > 254 ||
            !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()
        ) {
            message = "Введи корректный email."
            return
        }

        if (password.isBlank()) {
            message = "Введи пароль."
            return
        }

        if (password.length > 128) {
            message = "Пароль должен содержать не больше 128 символов."
            return
        }

        if (register && password.length < 8) {
            message = "Для регистрации нужен пароль от 8 символов."
            return
        }

        busy = true
        message = null

        viewModelScope.launch {
            var accountCreated = false

            try {
                val request = AuthRequest(normalizedEmail, password)

                if (register) {
                    ApiClient.api.register(request)
                    accountCreated = true
                }

                val session = ApiClient.api.login(request)

                check(
                    session.accessToken.isNotBlank() &&
                    session.tokenType == "bearer"
                )

                val profile = ApiClient.api.me(
                    "Bearer ${session.accessToken}"
                )

                check(profile.id > 0 && profile.email.isNotBlank())

                accessToken = session.accessToken
                user = profile
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = errorMessage(error)

                message = if (accountCreated) {
                    "Учётная запись создана, но вход не завершён. " +
                        "Переключись на вход и попробуй снова. $reason"
                } else {
                    reason
                }
            } finally {
                busy = false
            }
        }
    }

    fun logout() {
        val token = accessToken ?: return
        if (busy) return

        busy = true
        message = null

        viewModelScope.launch {
            try {
                ApiClient.api.logout("Bearer $token")
                clearSession()
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                if (error.code() == 401) {
                    clearSession()
                } else {
                    message = errorMessage(error)
                }
            } catch (error: Exception) {
                message = errorMessage(error)
            } finally {
                busy = false
            }
        }
    }

    private fun clearSession() {
        user = null
        accessToken = null
        message = null
    }

    private fun errorMessage(error: Exception): String {
        return when (error) {
            is HttpException -> when (error.code()) {
                401 -> "Неверный email или пароль либо сессия истекла."
                409 -> "Пользователь с таким email уже существует."
                422 -> "Backend отклонил данные. Проверь email и пароль."
                else -> "Ошибка backend: ${error.code()}. Попробуй позже."
            }

            is IOException ->
                "Нет подключения. Проверь, запущен ли backend."

            else -> "Не удалось обработать ответ backend."
        }
    }
}
