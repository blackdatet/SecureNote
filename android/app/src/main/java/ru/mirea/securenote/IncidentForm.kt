package ru.mirea.securenote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp

@Composable
fun IncidentForm(
    initialIncident: Incident?,
    onCancel: () -> Unit,
    onSave: (Incident) -> Unit
) {
    val editing = initialIncident != null

    var title by rememberSaveable(initialIncident?.id) {
        mutableStateOf(initialIncident?.title ?: "")
    }
    var description by rememberSaveable(initialIncident?.id) {
        mutableStateOf(initialIncident?.description ?: "")
    }
    var category by rememberSaveable(initialIncident?.id) {
        mutableStateOf(initialIncident?.category ?: "Другое")
    }
    var severity by rememberSaveable(initialIncident?.id) {
        mutableStateOf(initialIncident?.severity ?: "Низкий")
    }
    var status by rememberSaveable(initialIncident?.id) {
        mutableStateOf(initialIncident?.status ?: "Новый")
    }
    var attemptedSave by rememberSaveable(initialIncident?.id) {
        mutableStateOf(false)
    }

    val titleError = attemptedSave && title.isBlank()
    val descriptionError = attemptedSave && description.isBlank()

    BackHandler(onBack = onCancel)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onCancel) {
                Text("Отмена")
            }

            Text(
                text = if (editing) "Редактирование" else "Новый инцидент",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
                isError = titleError,
                supportingText = {
                    Text(
                        if (titleError) "Введите название"
                        else "${title.length}/120"
                    )
                }
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание события") },
                minLines = 4,
                maxLines = 8,
                isError = descriptionError,
                supportingText = {
                    Text(
                        if (descriptionError) "Введите описание"
                        else "${description.length}/2000"
                    )
                }
            )

            IncidentChoice(
                label = "Категория",
                selected = category,
                options = listOf(
                    "Фишинг",
                    "Подозрительный вход",
                    "Утечка данных",
                    "Другое"
                ),
                onSelected = { category = it }
            )

            IncidentChoice(
                label = "Уровень опасности",
                selected = severity,
                options = listOf("Низкий", "Средний", "Высокий"),
                onSelected = { severity = it }
            )

            if (editing) {
                IncidentChoice(
                    label = "Статус",
                    selected = status,
                    options = listOf("Новый", "В работе", "Закрыт"),
                    onSelected = { status = it }
                )
            } else {
                Text(
                    text = "Начальный статус: Новый",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    attemptedSave = true

                    if (title.isNotBlank() && description.isNotBlank()) {
                        onSave(
                            Incident(
                                id = initialIncident?.id ?: 0,
                                title = title.trim(),
                                description = description.trim(),
                                category = category,
                                severity = severity,
                                status = status
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (editing) "Сохранить изменения"
                    else "Добавить в журнал"
                )
            }
        }
    }
}

@Composable
private fun IncidentChoice(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("$selected ▾")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}