package ru.mirea.securenote

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureNoteFileProvider : FileProvider()

@Composable
fun PhotoCaptureCard(
    ownerId: Int,
    incidentId: Int,
    token: String,
    viewModel: IncidentPhotoViewModel,
    onSessionExpired: (String) -> Unit,
    onPhotoChanged: () -> Unit,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val directory = remember(ownerId, incidentId) {
        incidentPhotoDirectory(context, ownerId, incidentId)
    }
    var pendingPath by rememberSaveable(ownerId, incidentId) {
        mutableStateOf<String?>(null)
    }
    var notice by rememberSaveable(ownerId, incidentId) {
        mutableStateOf<String?>(null)
    }
    var confirmDelete by rememberSaveable(ownerId, incidentId) {
        mutableStateOf(false)
    }

    LaunchedEffect(ownerId, incidentId, token) {
        viewModel.bind(ownerId, incidentId, token, onSessionExpired, onPhotoChanged)
    }
    val bound = viewModel.matches(ownerId, incidentId, token)
    val canAct = bound && enabled && !viewModel.busy && !viewModel.cameraPending

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedPath = pendingPath
        pendingPath = null
        viewModel.setCameraPending(ownerId, incidentId, token, false)
        if (capturedPath != null) {
            val draft = File(capturedPath)
            try {
                if (!success) {
                    draft.delete()
                    notice = "Съёмка отменена."
                } else if (!isReadablePhoto(draft)) {
                    draft.delete()
                    notice = "Камера не сохранила корректную фотографию. Попробуйте ещё раз."
                } else {
                    val saved = File(directory, "photo_${UUID.randomUUID()}.jpg")
                    check(draft.renameTo(saved))
                    viewModel.acceptDraft(ownerId, incidentId, token, saved.absolutePath)
                    notice = null
                }
            } catch (_: Exception) {
                draft.delete()
                notice = "Не удалось сохранить новый снимок. Предыдущая фотография не заменена."
            }
        }
    }

    val draftPath = if (bound) viewModel.draftPath else null
    val serverInfo = if (bound) viewModel.serverInfo else null
    val currentPath = draftPath ?: if (bound) viewModel.serverPath else null
    val preview by produceState(initialValue = LocalPhotoPreview(), key1 = currentPath) {
        value = LocalPhotoPreview(loading = currentPath != null)
        if (currentPath != null) {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { readPhotoPreview(currentPath) }.getOrNull()
            }
            value = LocalPhotoPreview(
                bitmap = bitmap,
                error = if (bitmap == null) "Не удалось открыть фотографию. Обновите её или сделайте новый снимок." else null
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Фотография", style = MaterialTheme.typography.titleMedium)
            if (!bound || (viewModel.busy && !viewModel.loaded && draftPath == null)) {
                CircularProgressIndicator()
                Text("Получаем фотографию…")
            } else if (currentPath != null) {
                when {
                    preview.loading -> CircularProgressIndicator()
                    preview.bitmap != null -> Image(
                        bitmap = preview.bitmap!!.asImageBitmap(),
                        contentDescription = "Фотография инцидента",
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                preview.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else if (viewModel.loaded && serverInfo == null) {
                Text("Фотография ещё не добавлена.")
            } else if (!viewModel.busy) {
                Text("Не удалось получить фотографию. Нажмите «Обновить фото».")
            }

            if (bound) {
                if (draftPath != null) {
                    Text(
                        if (serverInfo == null) "Новый снимок ещё не загружен."
                        else "Новый снимок ещё не загружен. После загрузки он заменит сохранённую фотографию.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (serverInfo != null) {
                    Text(
                        "Фотография сохранена на сервере.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "JPEG · ${serverInfo.width} × ${serverInfo.height} пикс. · " +
                            formatPhotoSize(serverInfo.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                viewModel.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                viewModel.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Button(
                onClick = {
                    notice = null
                    var draft: File? = null
                    try {
                        check(directory.isDirectory || directory.mkdirs())
                        val created = File.createTempFile("draft_", ".jpg", directory)
                        draft = created
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", created
                        )
                        pendingPath = created.absolutePath
                        viewModel.setCameraPending(ownerId, incidentId, token, true)
                        launcher.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        draft?.delete()
                        pendingPath = null
                        viewModel.setCameraPending(ownerId, incidentId, token, false)
                        notice = "Приложение камеры недоступно. Инцидент можно использовать без фотографии."
                    } catch (_: SecurityException) {
                        draft?.delete()
                        pendingPath = null
                        viewModel.setCameraPending(ownerId, incidentId, token, false)
                        notice = "Не удалось открыть камеру: доступ запрещён."
                    } catch (_: Exception) {
                        draft?.delete()
                        pendingPath = null
                        viewModel.setCameraPending(ownerId, incidentId, token, false)
                        notice = "Не удалось подготовить съёмку. Попробуйте ещё раз."
                    }
                },
                enabled = canAct && pendingPath == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (draftPath != null || serverInfo != null) "Переснять" else "Сделать фото")
            }

            if (draftPath != null) {
                Button(
                    onClick = { notice = null; viewModel.uploadDraft() },
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (viewModel.busy) "Подождите…" else "Загрузить фото")
                }
                OutlinedButton(
                    onClick = { notice = null; viewModel.discardDraft() },
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отменить новый снимок")
                }
            }

            OutlinedButton(
                onClick = { notice = null; viewModel.refresh() },
                enabled = canAct,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (bound && viewModel.busy) "Подождите…" else "Обновить фото")
            }

            if (serverInfo != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = canAct,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить фото с сервера", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && bound && serverInfo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить фотографию?") },
            text = { Text("Сохранённая фотография будет удалена из инцидента.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        notice = null
                        viewModel.deleteServerPhoto()
                    },
                    enabled = canAct
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}

private fun formatPhotoSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f КиБ", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f МиБ", bytes / (1024.0 * 1024.0))
}

private data class LocalPhotoPreview(
    val bitmap: Bitmap? = null,
    val loading: Boolean = false,
    val error: String? = null
)

internal fun incidentPhotoDirectory(context: Context, ownerId: Int, incidentId: Int): File {
    require(ownerId > 0 && incidentId > 0)
    return File(context.cacheDir, "photos/user_$ownerId/incident_$incidentId")
}

internal fun findLocalPhoto(directory: File): File? =
    directory.listFiles()?.filter {
        it.isFile && it.name.startsWith("photo_") && it.extension == "jpg"
    }?.maxByOrNull { it.lastModified() }

internal fun deleteIncidentPhotoDrafts(context: Context, ownerId: Int, incidentId: Int) {
    incidentPhotoDirectory(context, ownerId, incidentId).deleteRecursively()
}

internal fun isReadablePhoto(file: File): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth > 0 && options.outHeight > 0
}

private fun readPhotoPreview(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
    }
    if (matrix.isIdentity) return bitmap
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
