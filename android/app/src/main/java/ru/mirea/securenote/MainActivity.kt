package ru.mirea.securenote

import android.os.Bundle
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.ViewModelProvider
import ru.mirea.securenote.ui.theme.SecureNoteTheme
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val authViewModel =
            ViewModelProvider(this)[AuthViewModel::class.java]

        val journalViewModel =
            ViewModelProvider(this)[RemoteJournalViewModel::class.java]

        setContent {
            SecureNoteTheme {
                SecureNoteApp(authViewModel, journalViewModel)
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
    val status: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
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

@Composable
private fun SecureNoteApp(
    auth: AuthViewModel,
    journal: RemoteJournalViewModel
) {
    val profile = auth.user
    val token = auth.accessToken

    if (profile == null || token == null) {
        LaunchedEffect(Unit) { journal.clearSession() }
        AuthScreen(viewModel = auth)
        return
    }

    key(profile.id, token) {
        LaunchedEffect(token) {
            journal.bindSession(token, auth::expireSession)
        }

        if (!journal.isBoundTo(token)) {
            Scaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            Scaffold(
                topBar = {
                    Column(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(profile.email, modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = auth::logout, enabled = !auth.busy && !journal.busy) {
                                Text(if (auth.busy) "Выходим…" else "Выйти")
                            }
                        }
                        auth.message?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    IncidentJournalScreen(journal, auth.busy)
                }
            }
        }
    }
}

@Composable
private fun IncidentJournalScreen(viewModel: RemoteJournalViewModel, sessionBusy: Boolean = false) {
    val incidents = viewModel.incidents
    val busy = viewModel.busy || sessionBusy
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    val journalStateHolder = rememberSaveableStateHolder()
    val selectedIncident = incidents.firstOrNull { it.id == selectedId }

    val savedId = viewModel.savedIncidentId
    LaunchedEffect(savedId) {
        if (savedId != null) {
            editorOpen = false
            selectedId = savedId
            viewModel.acknowledgeSave()
        }
    }
    val deletedId = viewModel.deletedIncidentId
    LaunchedEffect(deletedId) {
        if (deletedId != null) {
            selectedId = null
            editorOpen = false
            viewModel.acknowledgeDelete()
        }
    }

    when {
        editorOpen && (selectedId == null || selectedIncident != null) -> {
            IncidentForm(
                initialIncident = selectedIncident,
                onCancel = { viewModel.clearMessage(); editorOpen = false },
                onSave = viewModel::saveIncident,
                busy = busy,
                serverError = viewModel.message
            )
        }
        selectedIncident != null -> {
            IncidentDetails(
                incident = selectedIncident,
                onBack = { viewModel.clearMessage(); selectedId = null },
                onEdit = { viewModel.clearMessage(); editorOpen = true },
                onDelete = { viewModel.deleteIncident(selectedIncident.id) },
                busy = busy,
                serverError = viewModel.message
            )
        }
        else -> {
            journalStateHolder.SaveableStateProvider("journal") {
                IncidentList(
                    incidents = incidents,
                    onOpen = { id ->
                        if (!busy) {
                            viewModel.clearMessage()
                            selectedId = id
                            editorOpen = false
                        }
                    },
                    onCreate = {
                        if (!busy && viewModel.loaded) {
                            viewModel.clearMessage()
                            selectedId = null
                            editorOpen = true
                        }
                    },
                    busy = busy,
                    loaded = viewModel.loaded,
                    serverError = viewModel.message,
                    onRefresh = viewModel::refresh
                )
            }
        }
    }
}

@Composable
private fun IncidentList(
    incidents: List<Incident>,
    onOpen: (Int) -> Unit,
    onCreate: () -> Unit,
    busy: Boolean = false,
    loaded: Boolean = true,
    serverError: String? = null,
    onRefresh: (() -> Unit)? = null
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
                Text(if (busy) "Подождите…" else if (!loaded) "Сначала загрузите журнал" else "+ Новая запись")
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Личный журнал",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onRefresh != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Загрузка…" else "Обновить журнал")
                        }
                        if (serverError != null) {
                            Text(serverError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (!loaded) return@LazyColumn

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
    onDelete: () -> Unit,
    busy: Boolean = false,
    serverError: String? = null
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (!busy) onBack() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack, enabled = !busy) {
                Text("Назад к журналу")
            }

            Text(
                text = "Карточка инцидента",
                style = MaterialTheme.typography.headlineMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                IncidentContent(incident = incident, compact = false)
            }

            serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = onEdit,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Редактировать")
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (busy) "Подождите…" else "Удалить запись",
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
                    },
                    enabled = !busy
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
        if (!compact && incident.createdAt != null) {
            Text("Создан: ${formatIncidentDate(incident.createdAt)}", style = MaterialTheme.typography.bodySmall)
        }
        if (!compact && incident.updatedAt != null && incident.updatedAt != incident.createdAt) {
            Text("Изменён: ${formatIncidentDate(incident.updatedAt)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatIncidentDate(value: String): String = try {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
} catch (_: Exception) { value }

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