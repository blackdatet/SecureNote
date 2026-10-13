package ru.mirea.securenote

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException

private data class PhotoScope(val ownerId: Int, val incidentId: Int, val token: String)

class IncidentPhotoViewModel(application: Application) : AndroidViewModel(application) {
    var draftPath by mutableStateOf<String?>(null)
        private set
    var serverPath by mutableStateOf<String?>(null)
        private set
    var serverInfo by mutableStateOf<PhotoInfoResponse?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var operationLabel by mutableStateOf<String?>(null)
        private set
    var cameraPending by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    private var scope by mutableStateOf<PhotoScope?>(null)
    private var requestJob: Job? = null
    private var onExpired: ((String) -> Unit)? = null
    private var onChanged: (() -> Unit)? = null
    private val maxPhotoBytes = 10L * 1024 * 1024

    fun matches(ownerId: Int, incidentId: Int, token: String): Boolean =
        scope == PhotoScope(ownerId, incidentId, token)

    fun bind(
        ownerId: Int,
        incidentId: Int,
        token: String,
        onSessionExpired: (String) -> Unit,
        onPhotoChanged: () -> Unit
    ) {
        if (!matches(ownerId, incidentId, token)) {
            clearSession()
            scope = PhotoScope(ownerId, incidentId, token)
            draftPath = findLocalPhoto(directory(scope!!))?.absolutePath
        }
        onExpired = onSessionExpired
        onChanged = onPhotoChanged
        if (!busy && !cameraPending) refresh()
    }

    fun clearSession() {
        requestJob?.cancel()
        requestJob = null
        scope = null
        onExpired = null
        onChanged = null
        serverPath?.let { File(it).delete() }
        serverPath = null
        serverInfo = null
        // Несохранённый снимок остаётся в кеше своего пользователя для повторной попытки.
        draftPath = null
        loaded = false
        busy = false
        operationLabel = null
        cameraPending = false
        message = null
        notice = null
    }

    fun setCameraPending(ownerId: Int, incidentId: Int, token: String, pending: Boolean) {
        if (matches(ownerId, incidentId, token)) cameraPending = pending
    }

    fun acceptDraft(ownerId: Int, incidentId: Int, token: String, path: String) {
        if (!matches(ownerId, incidentId, token)) {
            File(path).delete()
            return
        }
        val previous = draftPath
        draftPath = path
        message = null
        notice = null
        if (previous != null && previous != path) File(previous).delete()
    }

    fun discardDraft() {
        if (busy || cameraPending) return
        val path = draftPath ?: return
        val file = File(path)
        if (file.exists() && !file.delete()) {
            message = "Не удалось удалить локальный снимок. Попробуйте ещё раз."
            return
        }
        draftPath = null
        message = null
        notice = null
    }

    fun refresh() {
        runRequest(label = "Обновляем фотографию…") { current ->
            val response = ApiClient.api.photoInfo("Bearer ${current.token}", current.incidentId)
            if (!response.isSuccessful) throw HttpException(response)
            val info = response.body()
            info?.validate(current.incidentId)
            if (scope != current) return@runRequest

            if (info == null) {
                serverPath?.let { File(it).delete() }
                serverPath = null
                serverInfo = null
                loaded = true
            } else {
                downloadPhoto(current)
                if (scope == current) {
                    serverInfo = info
                    loaded = true
                }
            }
        }
    }

    fun uploadDraft() {
        if (busy || cameraPending) return
        val path = draftPath ?: return
        val file = File(path)
        if (!file.isFile || file.length() == 0L) {
            message = "Снимок недоступен. Сделайте новую фотографию."
            return
        }
        if (file.length() > maxPhotoBytes) {
            message = "Фотография должна быть не больше 10 МБ."
            return
        }

        var uploadConfirmed = false
        runRequest(label = "Загружаем фотографию…", failurePrefix = {
            if (uploadConfirmed) "Фотография уже загружена. Не удалось обновить её отображение. " else ""
        }) { current ->
            val part = MultipartBody.Part.createFormData(
                "file", "incident-photo.jpg", file.asRequestBody("image/jpeg".toMediaType())
            )
            val info = ApiClient.api.uploadPhoto("Bearer ${current.token}", current.incidentId, part)
            uploadConfirmed = true
            info.validate(current.incidentId)
            if (scope != current) return@runRequest

            val fallback = File(directory(current), "server_${UUID.randomUUID()}.jpg")
            check(file.renameTo(fallback)) { "Local photo rename failed" }
            val previous = serverPath
            draftPath = null
            serverPath = fallback.absolutePath
            serverInfo = info
            loaded = true
            notice = "Фотография загружена."
            previous?.let { if (it != fallback.absolutePath) File(it).delete() }
            onChanged?.invoke()

            // Получаем именно сохранённый backend JPEG. При сбое остаётся локальное отображение.
            operationLabel = "Получаем сохранённую фотографию…"
            downloadPhoto(current)
        }
    }

    fun deleteServerPhoto() {
        if (serverInfo == null) return
        runRequest(label = "Удаляем фотографию…") { current ->
            ApiClient.api.deletePhoto("Bearer ${current.token}", current.incidentId)
            if (scope == current) {
                serverPath?.let { File(it).delete() }
                serverPath = null
                serverInfo = null
                loaded = true
                notice = "Фотография удалена с сервера."
                onChanged?.invoke()
            }
        }
    }

    private fun directory(current: PhotoScope): File =
        incidentPhotoDirectory(getApplication<Application>(), current.ownerId, current.incidentId)

    private suspend fun downloadPhoto(current: PhotoScope) {
        val target = File(directory(current), "server_${UUID.randomUUID()}.jpg")
        var adopted = false
        try {
            val body = ApiClient.api.photo("Bearer ${current.token}", current.incidentId)
            body.use { response ->
                withContext(Dispatchers.IO) {
                    val type = response.contentType()
                    check(type?.type == "image" && type?.subtype == "jpeg")
                    check(response.contentLength() <= maxPhotoBytes)
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    response.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                total += count
                                check(total <= maxPhotoBytes)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    check(isReadablePhoto(target))
                }
            }
            if (scope == current) {
                val previous = serverPath
                serverPath = target.absolutePath
                adopted = true
                previous?.let { if (it != target.absolutePath) File(it).delete() }
            }
        } finally {
            if (!adopted) target.delete()
        }
    }

    private fun runRequest(
        label: String,
        failurePrefix: () -> String = { "" },
        action: suspend (PhotoScope) -> Unit
    ) {
        val current = scope ?: return
        if (busy || cameraPending) return
        busy = true
        operationLabel = label
        message = null
        notice = null
        requestJob = viewModelScope.launch {
            try {
                action(current)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (scope == current) {
                    if (error is HttpException && error.code() == 401) {
                        val callback = onExpired
                        clearSession()
                        callback?.invoke(current.token)
                    } else {
                        val reason = when (error) {
                            is HttpException -> when (error.code()) {
                                404 -> "Фотография или инцидент не найдены. Обновите карточку."
                                413 -> "Фотография должна быть не больше 10 МБ и 20 мегапикселей."
                                415, 422 -> "Backend отклонил файл. Сделайте новый снимок JPEG или PNG."
                                else -> "Ошибка backend: ${error.code()}. Попробуйте ещё раз."
                            }
                            is IOException -> "Нет подключения. Проверьте, запущен ли backend."
                            else -> "Не удалось обработать фотографию. Попробуйте обновить её."
                        }
                        message = failurePrefix() + reason
                    }
                }
            } finally {
                if (scope == current) {
                    busy = false
                    operationLabel = null
                }
            }
        }
    }
}
