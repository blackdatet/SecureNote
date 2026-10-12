package ru.mirea.securenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.mirea.securenote.ui.theme.SecureNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val journalViewModel =
            ViewModelProvider(this)[JournalViewModel::class.java]

        setContent {
            SecureNoteTheme {
                IncidentJournalScreen(
                    incidents = journalViewModel.incidents,
                    onAddIncident = journalViewModel::addIncident
                )
            }
        }
    }
}

data class Incident(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val severity: String,
    val status: String
)

private val demoIncidents = listOf(
    Incident(
        id = 1,
        title = "Подозрительное письмо",
        description = "Получено письмо с неизвестного адреса " +
                "со ссылкой на страницу входа.",
        category = "Фишинг",
        severity = "Высокий",
        status = "Новый"
    ),
    Incident(
        id = 2,
        title = "Вход с неизвестного устройства",
        description = "Пришло уведомление о входе в учётную запись " +
                "с незнакомого устройства.",
        category = "Подозрительный вход",
        severity = "Средний",
        status = "В работе"
    ),
    Incident(
        id = 3,
        title = "Проверка подозрительного файла",
        description = "Файл проверен. Признаков вредоносной " +
                "активности не обнаружено.",
        category = "Другое",
        severity = "Низкий",
        status = "Закрыт"
    )
)

// Пока данные существуют только в памяти.
// Позже ViewModel будет получать их через слой работы с backend.
class JournalViewModel : ViewModel() {
    var incidents by mutableStateOf(demoIncidents)
        private set

    fun addIncident(
        title: String,
        description: String,
        category: String,
        severity: String
    ) {
        if (title.isBlank() || description.isBlank()) return

        val incident = Incident(
            id = (incidents.maxOfOrNull { it.id } ?: 0) + 1,
            title = title.trim(),
            description = description.trim(),
            category = category,
            severity = severity,
            status = "Новый"
        )

        incidents = listOf(incident) + incidents
    }
}

@Composable
private fun IncidentJournalScreen(
    incidents: List<Incident>,
    onAddIncident: (String, String, String, String) -> Unit
) {
    var showForm by rememberSaveable { mutableStateOf(false) }

    if (showForm) {
        IncidentForm(
            onCancel = { showForm = false },
            onSave = { title, description, category, severity ->
                onAddIncident(title, description, category, severity)
                showForm = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showForm = true }
                ) {
                    Text("+ Новая запись")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SecureNote",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Журнал инцидентов",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Всего записей: ${incidents.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Тестовый режим: записи хранятся в памяти",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(
                    items = incidents,
                    key = { incident -> incident.id }
                ) { incident ->
                    IncidentCard(incident)
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: Incident) {
    val severityColor = when (incident.severity) {
        "Высокий" -> MaterialTheme.colorScheme.error
        "Средний" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = incident.category,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = incident.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Уровень опасности: ${incident.severity}",
                style = MaterialTheme.typography.labelLarge,
                color = severityColor
            )

            Text(
                text = "Статус: ${incident.status}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IncidentJournalPreview() {
    SecureNoteTheme {
        IncidentJournalScreen(
            incidents = demoIncidents,
            onAddIncident = { _, _, _, _ -> }
        )
    }
}