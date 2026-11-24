package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ProofMethodChip
import com.example.ui.components.StatusBadge
import com.example.ui.components.WorkerInitialsAvatar
import com.example.ui.components.statusColor
import com.example.ui.viewmodel.AttendanceViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

private enum class DateScope(val label: String) {
    TODAY("اليوم"),
    YESTERDAY("أمس"),
    ALL("كل السجلات")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    profileState: UiState<UserProfile?>,
    recordsState: UiState<List<AttendanceRecord>>,
    onDeleteRecord: (String) -> Unit,
    buildReportText: (dateLabel: String, projectName: String, records: List<AttendanceRecord>) -> String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val todayKey = remember { AttendanceViewModel.todayDateKey() }
    val yesterdayKey = remember { AttendanceViewModel.yesterdayDateKey() }

    var dateScope by remember { mutableStateOf(DateScope.TODAY) }
    var statusFilter by remember { mutableStateOf<AttendanceStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }

    val profile = (profileState as? UiState.Success)?.data

    if (recordsState is UiState.Loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (recordsState is UiState.Error) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Filled.History,
                title = "تعذر تحميل سجل الإثباتات",
                subtitle = recordsState.message
            )
        }
        return
    }

    val allRecords = (recordsState as? UiState.Success)?.data.orEmpty()
    val filteredRecords = remember(allRecords, dateScope, statusFilter, searchQuery) {
        val query = searchQuery.trim()
        allRecords.filter { rec ->
            val dateMatches = when (dateScope) {
                DateScope.TODAY -> rec.dateKey == todayKey
                DateScope.YESTERDAY -> rec.dateKey == yesterdayKey
                DateScope.ALL -> true
            }
            val statusMatches = statusFilter == null || rec.statusEnum == statusFilter
            val queryMatches = query.isEmpty() ||
                rec.workerName.contains(query, ignoreCase = true) ||
                rec.siteName.contains(query, ignoreCase = true) ||
                rec.roleTitle.contains(query, ignoreCase = true)
            dateMatches && statusMatches && queryMatches
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
                        text = "سجل إثباتات الحضور",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "عدد السجلات المطابقة: ${filteredRecords.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        val scopeLabel = when (dateScope) {
                            DateScope.TODAY -> todayKey
                            DateScope.YESTERDAY -> yesterdayKey
                            DateScope.ALL -> "جميع الفترات"
                        }
                        val report = buildReportText(
                            scopeLabel,
                            profile?.projectName ?: "الموقع الرئيسي",
                            filteredRecords
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "تقرير حضور العمال - $scopeLabel")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير"))
                    },
                    enabled = filteredRecords.isNotEmpty(),
                    modifier = Modifier.testTag("share_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مشاركة التقرير")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث باسم العامل أو الموقع أو المهنة...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateScope.entries.forEach { scope ->
                        FilterChip(
                            selected = dateScope == scope,
                            onClick = { dateScope = scope },
                            label = { Text(scope.label) }
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("كل الحالات") }
                    )
                    AttendanceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = {
                                statusFilter = if (statusFilter == st) null else st
                            },
                            label = { Text(st.labelAr) }
                        )
                    }
                }
            }
        }

        if (filteredRecords.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.History,
                    title = "لا توجد سجلات في هذه الفترة",
                    subtitle = "عند توثيق حضور العمال من شاشة اليوم ستظهر جميع الإثباتات هنا مع الطابع الزمني."
                )
            }
        } else {
            items(filteredRecords, key = { it.id }) { record ->
                HistoryRecordCard(
                    record = record,
                    onDeleteClick = { recordToDelete = record }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (recordToDelete != null) {
        val target = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("حذف سجل الحضور") },
            text = { Text("هل تريد حذف سجل حضور ${target.workerName} بتاريخ ${target.dateKey}؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecord(target.id)
                        recordToDelete = null
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
                TextButton(onClick = { recordToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun HistoryRecordCard(
    record: AttendanceRecord,
    onDeleteClick: () -> Unit,
) {
    val accent = statusColor(record.statusEnum)
    val serverStampText = remember(record.updatedAt, record.createdAt) {
        val ts = record.updatedAt ?: record.createdAt ?: Timestamp.now()
        SimpleDateFormat("yyyy-MM-dd • HH:mm", Locale.US).format(ts.toDate())
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WorkerInitialsAvatar(
                    fullName = record.workerName,
                    accentColor = accent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.workerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${record.roleTitle} • ${record.siteName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusBadge(status = record.statusEnum)
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "حذف السجل",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProofMethodChip(method = record.proofMethodEnum)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = record.dateKey,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (record.checkInTimeLabel.isNotBlank() && record.checkInTimeLabel != "—") {
                Text(
                    text = buildString {
                        append("وقت الدخول: ${record.checkInTimeLabel}")
                        if (!record.checkOutTimeLabel.isNullOrBlank()) {
                            append("  •  وقت الانصراف: ${record.checkOutTimeLabel}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!record.locationSummary.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = record.locationSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!record.photoUriString.isNullOrBlank()) {
                AsyncImage(
                    model = record.photoUriString,
                    contentDescription = "صورة إثبات حضور ${record.workerName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            if (!record.notes.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Text(
                text = "طابع التوثيق الخادمي: $serverStampText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
