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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.mirea.securenote.ui.theme.SecureNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SecureNoteTheme {
                IncidentJournalScreen()
            }
        }
    }
}

// Временная модель для проверки интерфейса.
private data class DemoIncident(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val severity: String,
    val status: String
)

// Демонстрационные записи. Позже данные будут приходить через backend.
private val demoIncidents = listOf(
    DemoIncident(
        id = 1,
        title = "Подозрительное письмо",
        description = "Получено письмо с неизвестного адреса " +
                "со ссылкой на страницу входа.",
        category = "Фишинг",
        severity = "Высокий",
        status = "Новый"
    ),
    DemoIncident(
        id = 2,
        title = "Вход с неизвестного устройства",
        description = "Пришло уведомление о входе в учётную запись " +
                "с незнакомого устройства.",
        category = "Подозрительный вход",
        severity = "Средний",
        status = "В работе"
    ),
    DemoIncident(
        id = 3,
        title = "Проверка подозрительного файла",
        description = "Файл проверен. Признаков вредоносной " +
                "активности не обнаружено.",
        category = "Другое",
        severity = "Низкий",
        status = "Закрыт"
    )
)

@Composable
private fun IncidentJournalScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
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
                Text(
                    text = "Демонстрационные записи · ${demoIncidents.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(
                items = demoIncidents,
                key = { incident -> incident.id }
            ) { incident ->
                IncidentCard(incident)
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: DemoIncident) {
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
        IncidentJournalScreen()
    }
}