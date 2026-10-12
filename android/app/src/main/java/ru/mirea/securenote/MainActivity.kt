package ru.mirea.securenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.mirea.securenote.ui.theme.SecureNoteTheme
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

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
                    onSaveIncident = journalViewModel::saveIncident,
                    onDeleteIncident = journalViewModel::deleteIncident
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

// Временное хранение в памяти до подключения backend.
class JournalViewModel : ViewModel() {
    var incidents by mutableStateOf(demoIncidents)
        private set

    private var nextId = (demoIncidents.maxOfOrNull { it.id } ?: 0) + 1

    fun saveIncident(incident: Incident) {
        if (incident.title.isBlank() || incident.description.isBlank()) return

        val cleanedIncident = incident.copy(
            title = incident.title.trim().take(120),
            description = incident.description.trim().take(2000)
        )

        if (incident.id == 0) {
            val newIncident = cleanedIncident.copy(
                id = nextId++,
                status = "Новый"
            )
            incidents = listOf(newIncident) + incidents
        } else {
            incidents = incidents.map { existing ->
                if (existing.id == incident.id) cleanedIncident else existing
            }
        }
    }

    fun deleteIncident(id: Int) {
        incidents = incidents.filterNot { it.id == id }
    }
}

@Composable
private fun IncidentJournalScreen(
    incidents: List<Incident>,
    onSaveIncident: (Incident) -> Unit,
    onDeleteIncident: (Int) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }

    val journalStateHolder = rememberSaveableStateHolder()
    val selectedIncident = incidents.firstOrNull { it.id == selectedId }

    when {
        editorOpen && (selectedId == null || selectedIncident != null) -> {
            IncidentForm(
                initialIncident = selectedIncident,
                onCancel = { editorOpen = false },
                onSave = { incident ->
                    onSaveIncident(incident)
                    editorOpen = false
                }
            )
        }

        selectedIncident != null -> {
            IncidentDetails(
                incident = selectedIncident,
                onBack = { selectedId = null },
                onEdit = { editorOpen = true },
                onDelete = {
                    onDeleteIncident(selectedIncident.id)
                    selectedId = null
                }
            )
        }

        else -> {
            journalStateHolder.SaveableStateProvider("journal") {
                IncidentList(
                    incidents = incidents,
                    onOpen = { id ->
                        selectedId = id
                        editorOpen = false
                    },
                    onCreate = {
                        selectedId = null
                        editorOpen = true
                    }
                )
            }
        }
    }
}

@Composable
private fun IncidentList(
    incidents: List<Incident>,
    onOpen: (Int) -> Unit,
    onCreate: () -> Unit
) {
    var categoryFilter by rememberSaveable { mutableStateOf("Все") }
    var severityFilter by rememberSaveable { mutableStateOf("Все") }
    var statusFilter by rememberSaveable { mutableStateOf("Все") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSummary by rememberSaveable { mutableStateOf(false) }

    val categories = listOf(
        "Фишинг",
        "Подозрительный вход",
        "Утечка данных",
        "Другое"
    )

    val filteredIncidents = incidents.filter { incident ->
        (categoryFilter == "Все" || incident.category == categoryFilter) &&
                (severityFilter == "Все" || incident.severity == severityFilter) &&
                (statusFilter == "Все" || incident.status == statusFilter)
    }

    val closedCount = incidents.count { it.status == "Закрыт" }
    val openCount = incidents.count {
        it.status == "Новый" || it.status == "В работе"
    }

    val filtersActive =
        categoryFilter != "Все" ||
                severityFilter != "Все" ||
                statusFilter != "Все"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate) {
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
                BackendConnectionCard()
            }
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Тестовый режим: записи хранятся в памяти",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Сводка по всему журналу",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Всего: ${incidents.size}",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Открыто: $openCount · Закрыто: $closedCount",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        TextButton(
                            onClick = { showSummary = !showSummary }
                        ) {
                            Text(
                                if (showSummary) "Скрыть категории"
                                else "Показать по категориям"
                            )
                        }

                        if (showSummary) {
                            categories.forEach { category ->
                                val count = incidents.count {
                                    it.category == category
                                }

                                Text(
                                    text = "$category: $count",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showFilters = !showFilters },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showFilters) "Скрыть фильтры"
                        else "Показать фильтры"
                    )
                }
            }

            if (showFilters) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IncidentChoice(
                                label = "Категория",
                                selected = categoryFilter,
                                options = listOf("Все") + categories,
                                onSelected = { categoryFilter = it }
                            )

                            IncidentChoice(
                                label = "Уровень опасности",
                                selected = severityFilter,
                                options = listOf(
                                    "Все",
                                    "Низкий",
                                    "Средний",
                                    "Высокий"
                                ),
                                onSelected = { severityFilter = it }
                            )

                            IncidentChoice(
                                label = "Статус",
                                selected = statusFilter,
                                options = listOf(
                                    "Все",
                                    "Новый",
                                    "В работе",
                                    "Закрыт"
                                ),
                                onSelected = { statusFilter = it }
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Показано: ${filteredIncidents.size} " +
                                "из ${incidents.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (filtersActive) {
                        Text(
                            text = "Категория: $categoryFilter\n" +
                                    "Опасность: $severityFilter\n" +
                                    "Статус: $statusFilter",
                            style = MaterialTheme.typography.bodySmall
                        )

                        TextButton(
                            onClick = {
                                categoryFilter = "Все"
                                severityFilter = "Все"
                                statusFilter = "Все"
                            }
                        ) {
                            Text("Сбросить фильтры")
                        }
                    }
                }
            }

            if (filteredIncidents.isEmpty()) {
                item {
                    Text(
                        text = if (incidents.isEmpty()) {
                            "Записей пока нет. Добавьте первый инцидент."
                        } else {
                            "По выбранным фильтрам ничего не найдено."
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            items(filteredIncidents, key = { it.id }) { incident ->
                Card(
                    onClick = { onOpen(incident.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IncidentContent(incident = incident, compact = true)
                }
            }
        }
    }
}

@Composable
private fun IncidentDetails(
    incident: Incident,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("Назад к журналу")
            }

            Text(
                text = "Карточка инцидента",
                style = MaterialTheme.typography.headlineMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                IncidentContent(incident = incident, compact = false)
            }

            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Редактировать")
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Удалить запись",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить инцидент?") },
            text = {
                Text("Запись «${incident.title}» будет удалена из журнала.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(
                        text = "Удалить",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun IncidentContent(incident: Incident, compact: Boolean) {
    val severityColor = when (incident.severity) {
        "Высокий" -> MaterialTheme.colorScheme.error
        "Средний" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 3 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis
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

@Preview(showBackground = true)
@Composable
private fun IncidentJournalPreview() {
    SecureNoteTheme {
        IncidentList(
            incidents = demoIncidents,
            onOpen = {},
            onCreate = {}
        )
    }
}