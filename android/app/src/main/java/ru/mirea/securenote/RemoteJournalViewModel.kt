package ru.mirea.securenote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class RemoteJournalViewModel : ViewModel() {
    var incidents by mutableStateOf<List<Incident>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var savedIncidentId by mutableStateOf<Int?>(null)
        private set
    var deletedIncidentId by mutableStateOf<Int?>(null)
        private set

    private var sessionToken by mutableStateOf<String?>(null)
    private var requestJob: Job? = null
    private var onExpired: ((String) -> Unit)? = null

    fun isBoundTo(token: String): Boolean = sessionToken == token

    fun bindSession(token: String, onSessionExpired: (String) -> Unit) {
        if (sessionToken == token) return
        clearSession()
        sessionToken = token
        onExpired = onSessionExpired
        refresh()
    }

    fun clearSession() {
        requestJob?.cancel()
        requestJob = null
        sessionToken = null
        onExpired = null
        incidents = emptyList()
        busy = false
        loaded = false
        message = null
        savedIncidentId = null
        deletedIncidentId = null
    }

    fun clearMessage() {
        if (!busy) message = null
    }

    fun acknowledgeSave() { savedIncidentId = null }
    fun acknowledgeDelete() { deletedIncidentId = null }

    fun refresh() {
        runRequest { token ->
            val records = ApiClient.api.incidents("Bearer $token").map { it.toIncident() }
            if (sessionToken == token) {
                incidents = records
                loaded = true
            }
        }
    }

    fun saveIncident(incident: Incident) {
        if (busy || !loaded) return
        val title = incident.title.trim()
        val description = incident.description.trim()
        if (title.isEmpty() || description.isEmpty()) {
            message = "Заполни название и описание."
            return
        }

        runRequest { token ->
            val response = if (incident.id == 0) {
                ApiClient.api.createIncident(
                    "Bearer $token",
                    IncidentCreateRequest(title, description, incident.category, incident.severity)
                )
            } else {
                ApiClient.api.updateIncident(
                    "Bearer $token", incident.id,
                    IncidentUpdateRequest(title, description, incident.category, incident.severity, incident.status)
                )
            }
            val saved = response.toIncident()
            if (sessionToken == token) {
                incidents = if (incident.id == 0) {
                    listOf(saved) + incidents
                } else {
                    incidents.map { if (it.id == saved.id) saved else it }
                }
                savedIncidentId = saved.id
            }
        }
    }

    fun deleteIncident(id: Int) {
        if (busy || !loaded) return
        runRequest { token ->
            ApiClient.api.deleteIncident("Bearer $token", id)
            if (sessionToken == token) {
                incidents = incidents.filterNot { it.id == id }
                deletedIncidentId = id
            }
        }
    }

    private fun runRequest(operation: suspend (String) -> Unit) {
        val token = sessionToken ?: return
        if (busy) return
        busy = true
        message = null

        requestJob = viewModelScope.launch {
            try {
                operation(token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (sessionToken == token) {
                    if (error is HttpException && error.code() == 401) {
                        val callback = onExpired
                        clearSession()
                        callback?.invoke(token)
                    } else {
                        message = when (error) {
                            is HttpException -> when (error.code()) {
                                404 -> "Запись не найдена. Обнови журнал."
                                422 -> "Backend отклонил данные. Проверь поля записи."
                                else -> "Ошибка backend: ${error.code()}. Попробуй снова."
                            }
                            is IOException -> "Нет подключения. Проверь, запущен ли backend."
                            else -> "Не удалось обработать ответ backend."
                        }
                    }
                }
            } finally {
                if (sessionToken == token) busy = false
            }
        }
    }
}
