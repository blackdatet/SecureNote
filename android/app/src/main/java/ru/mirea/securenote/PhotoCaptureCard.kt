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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureNoteFileProvider : FileProvider()

// Первый этап: локальный снимок. Загрузка в backend добавляется отдельно.
@Composable
fun PhotoCaptureCard(
    ownerId: Int,
    incidentId: Int,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val directory = remember(ownerId, incidentId) {
        incidentPhotoDirectory(context, ownerId, incidentId)
    }
    var photoPath by rememberSaveable(ownerId, incidentId) {
        mutableStateOf(findLocalPhoto(directory)?.absolutePath)
    }
    var pendingPath by rememberSaveable(ownerId, incidentId) {
        mutableStateOf<String?>(null)
    }
    var notice by rememberSaveable(ownerId, incidentId) {
        mutableStateOf<String?>(null)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedPath = pendingPath
        pendingPath = null

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
                    // Старый снимок удаляется только после сохранения нового.
                    val saved = File(directory, "photo_${UUID.randomUUID()}.jpg")
                    check(draft.renameTo(saved)) { "Photo rename failed" }
                    photoPath = saved.absolutePath
                    notice = null
                    directory.listFiles()?.filter {
                        it.name.startsWith("photo_") && it != saved
                    }?.forEach { it.delete() }
                }
            } catch (_: Exception) {
                draft.delete()
                notice = "Не удалось сохранить снимок. Предыдущая фотография не заменена."
            }
        }
    }

    val currentPath = photoPath
    val preview by produceState(
        initialValue = LocalPhotoPreview(),
        key1 = currentPath
    ) {
        value = LocalPhotoPreview(loading = currentPath != null)
        if (currentPath != null) {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { readPhotoPreview(currentPath) }.getOrNull()
            }
            value = LocalPhotoPreview(
                bitmap = bitmap,
                error = if (bitmap == null) "Не удалось открыть фотографию. Сделайте новый снимок." else null
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Фотография", style = MaterialTheme.typography.titleMedium)

            if (currentPath == null) {
                Text("Фотография ещё не сделана.")
            } else {
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
                Text(
                    "Снимок сохранён на устройстве. На сервер пока не загружен.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            context,
                            "${context.packageName}.fileprovider",
                            created
                        )
                        pendingPath = created.absolutePath
                        launcher.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        draft?.delete()
                        pendingPath = null
                        notice = "Приложение камеры недоступно. Инцидент можно использовать без фотографии."
                    } catch (_: SecurityException) {
                        draft?.delete()
                        pendingPath = null
                        notice = "Не удалось открыть камеру: доступ запрещён."
                    } catch (_: Exception) {
                        draft?.delete()
                        pendingPath = null
                        notice = "Не удалось подготовить съёмку. Попробуйте ещё раз."
                    }
                },
                enabled = enabled && pendingPath == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentPath == null) "Сделать фото" else "Переснять")
            }

            if (currentPath != null) {
                OutlinedButton(
                    onClick = {
                        val file = File(currentPath)
                        if (!file.exists() || file.delete()) {
                            photoPath = null
                            notice = null
                        } else {
                            notice = "Не удалось удалить снимок. Попробуйте ещё раз."
                        }
                    },
                    enabled = enabled && pendingPath == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить снимок")
                }
            }
        }
    }
}

private data class LocalPhotoPreview(
    val bitmap: Bitmap? = null,
    val loading: Boolean = false,
    val error: String? = null
)

private fun incidentPhotoDirectory(context: Context, ownerId: Int, incidentId: Int): File {
    require(ownerId > 0 && incidentId > 0)
    return File(context.cacheDir, "photos/user_$ownerId/incident_$incidentId")
}

private fun findLocalPhoto(directory: File): File? =
    directory.listFiles()?.filter {
        it.isFile && it.name.startsWith("photo_") && it.extension == "jpg"
    }?.maxByOrNull { it.lastModified() }

internal fun deleteIncidentPhotoDrafts(context: Context, ownerId: Int, incidentId: Int) {
    incidentPhotoDirectory(context, ownerId, incidentId).deleteRecursively()
}

private fun isReadablePhoto(file: File): Boolean {
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
