package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.UiState
import com.example.data.model.Worker
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.WorkerInitialsAvatar
import com.example.ui.theme.StatusAbsentColor
import com.example.ui.theme.StatusExcusedColor
import com.example.ui.theme.StatusLateColor
import com.example.ui.theme.StatusPresentColor

@Composable
fun WorkersScreen(
    workersState: UiState<List<Worker>>,
    recordsState: UiState<List<AttendanceRecord>>,
    onAddWorkerClick: () -> Unit,
    onEditWorkerClick: (Worker) -> Unit,
    onToggleActive: (Worker) -> Unit,
    onDeleteWorker: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showActiveOnly by remember { mutableStateOf(false) }
    var workerToDelete by remember { mutableStateOf<Worker?>(null) }
    var selectedWorkerSummary by remember { mutableStateOf<Worker?>(null) }

    if (workersState is UiState.Loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (workersState is UiState.Error) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Filled.Groups,
                title = "تعذر تحميل قائمة العمال",
                subtitle = workersState.message
            )
        }
        return
    }

    val workers = (workersState as? UiState.Success)?.data.orEmpty()
    val allRecords = (recordsState as? UiState.Success)?.data.orEmpty()

    val filteredWorkers = remember(workers, searchQuery, showActiveOnly) {
        val query = searchQuery.trim()
        workers.filter { w ->
            val activeOk = !showActiveOnly || w.active
            val queryOk = query.isEmpty() ||
                w.fullName.contains(query, ignoreCase = true) ||
                w.roleTitle.contains(query, ignoreCase = true) ||
                w.badgeCode.contains(query, ignoreCase = true) ||
                w.siteName.contains(query, ignoreCase = true)
            activeOk && queryOk
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "فريق العمل المسجل",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "إجمالي العمال: ${workers.size} • النشطون: ${workers.count { it.active }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث بالاسم أو الرقم الوظيفي أو الموقع...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workers_search_input")
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showActiveOnly,
                    onClick = { showActiveOnly = false },
                    label = { Text("الكل (${workers.size})") }
                )
                FilterChip(
                    selected = showActiveOnly,
                    onClick = { showActiveOnly = true },
                    label = { Text("النشطون فقط (${workers.count { it.active }})") }
                )
            }
        }

        if (filteredWorkers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Groups,
                    title = if (workers.isEmpty()) "لا يوجد عمال في القائمة" else "لا توجد نتائج مطابقة",
                    subtitle = if (workers.isEmpty()) {
                        "أضف بيانات العمال (الاسم، المهنة، الرقم الوظيفي، الموقع) لبدء المتابعة اليومية."
                    } else {
                        "جرّب تعديل كلمات البحث أو إظهار جميع العمال."
                    },
                    actionLabel = if (workers.isEmpty()) "إضافة عامل" else null,
                    onActionClick = if (workers.isEmpty()) onAddWorkerClick else null
                )
            }
        } else {
            items(filteredWorkers, key = { it.id }) { worker ->
                WorkerRosterCard(
                    worker = worker,
                    onCardClick = { selectedWorkerSummary = worker },
                    onToggleActive = { onToggleActive(worker) },
                    onEditClick = { onEditWorkerClick(worker) },
                    onDeleteClick = { workerToDelete = worker }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (workerToDelete != null) {
        val target = workerToDelete!!
        AlertDialog(
            onDismissRequest = { workerToDelete = null },
            title = { Text("حذف العامل") },
            text = { Text("هل أنت متأكد من حذف ${target.fullName} من قائمة العمال؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWorker(target.id)
                        workerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { workerToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (selectedWorkerSummary != null) {
        val worker = selectedWorkerSummary!!
        val workerRecords = allRecords.filter { it.workerId == worker.id }
        val present = workerRecords.count { it.statusEnum == AttendanceStatus.PRESENT }
        val late = workerRecords.count { it.statusEnum == AttendanceStatus.LATE }
        val excused = workerRecords.count { it.statusEnum == AttendanceStatus.EXCUSED }
        val absent = workerRecords.count { it.statusEnum == AttendanceStatus.ABSENT }

        AlertDialog(
            onDismissRequest = { selectedWorkerSummary = null },
            title = { Text(worker.fullName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${worker.roleTitle} • ${worker.badgeCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "الموقع: ${worker.siteName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!worker.phoneNumber.isNullOrBlank()) {
                        Text(
                            text = "الجوال: ${worker.phoneNumber}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ملخص الحضور المسجل (${workerRecords.size} يوم):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatPill("حاضر", present, StatusPresentColor, Modifier.weight(1f))
                        StatPill("متأخر", late, StatusLateColor, Modifier.weight(1f))
                        StatPill("بعذر", excused, StatusExcusedColor, Modifier.weight(1f))
                        StatPill("غائب", absent, StatusAbsentColor, Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedWorkerSummary = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun WorkerRosterCard(
    worker: Worker,
    onCardClick: () -> Unit,
    onToggleActive: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val accent = if (worker.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("worker_card_${worker.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkerInitialsAvatar(
                fullName = worker.fullName,
                accentColor = accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = worker.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${worker.roleTitle} • ${worker.badgeCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = worker.siteName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!worker.phoneNumber.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = worker.phoneNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Switch(
                    checked = worker.active,
                    onCheckedChange = { onToggleActive() },
                    modifier = Modifier.testTag("worker_active_switch_${worker.id}")
                )
                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_worker_button_${worker.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "تعديل ${worker.fullName}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_worker_button_${worker.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "حذف ${worker.fullName}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
