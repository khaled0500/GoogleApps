package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.location.SiteLocationHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.Worker
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ProofMethodChip
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryMetricCard
import com.example.ui.components.WorkerInitialsAvatar
import com.example.ui.components.statusColor
import com.example.ui.theme.StatusLateColor
import com.example.ui.theme.StatusPresentColor
import com.example.ui.viewmodel.AttendanceViewModel

private enum class TodayFilter(val label: String) {
    ALL("الكل"),
    PENDING("بانتظار الإثبات"),
    LOGGED("تم إثباتهم")
}

@Composable
fun TodayScreen(
    profileState: UiState<UserProfile?>,
    workersState: UiState<List<Worker>>,
    recordsState: UiState<List<AttendanceRecord>>,
    verifiedLocation: String?,
    isFetchingLocation: Boolean,
    isBusy: Boolean,
    onRequestLocation: () -> Unit,
    onQuickCheckIn: (Worker) -> Unit,
    onOpenProofSheet: (Worker, AttendanceRecord?) -> Unit,
    onCheckOut: (AttendanceRecord) -> Unit,
    onAddWorkerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val todayKey = remember { AttendanceViewModel.todayDateKey() }
    val todayLabel = remember { AttendanceViewModel.todayDisplayArabic() }
    var activeFilter by remember { mutableStateOf(TodayFilter.ALL) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRequestLocation()
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val workers = (workersState as? UiState.Success)?.data.orEmpty()
    val activeWorkers = remember(workers) { workers.filter { it.active } }
    val allRecords = (recordsState as? UiState.Success)?.data.orEmpty()
    val todayRecordsByWorker = remember(allRecords, todayKey) {
        allRecords.filter { it.dateKey == todayKey }.associateBy { it.workerId }
    }

    val presentCount = activeWorkers.count {
        todayRecordsByWorker[it.id]?.statusEnum == AttendanceStatus.PRESENT
    }
    val lateCount = activeWorkers.count {
        todayRecordsByWorker[it.id]?.statusEnum == AttendanceStatus.LATE
    }
    val loggedCount = activeWorkers.count { todayRecordsByWorker.containsKey(it.id) }
    val pendingCount = (activeWorkers.size - loggedCount).coerceAtLeast(0)
    val attendancePercent = if (activeWorkers.isEmpty()) 0f
    else (presentCount + lateCount).toFloat() / activeWorkers.size.toFloat()

    val filteredWorkers = remember(activeWorkers, todayRecordsByWorker, activeFilter) {
        when (activeFilter) {
            TodayFilter.ALL -> activeWorkers
            TodayFilter.PENDING -> activeWorkers.filter { !todayRecordsByWorker.containsKey(it.id) }
            TodayFilter.LOGGED -> activeWorkers.filter { todayRecordsByWorker.containsKey(it.id) }
        }
    }

    if (workersState is UiState.Loading || recordsState is UiState.Loading) {
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
                title = "تعذر تحميل بيانات العمال",
                subtitle = workersState.message
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DailyHeroBanner(
                projectName = profile?.projectName ?: "الموقع الرئيسي",
                todayLabel = todayLabel,
                shiftStart = String.format(
                    "%02d:%02d",
                    (profile?.shiftStartHour ?: 7L).toInt(),
                    (profile?.shiftStartMinute ?: 30L).toInt()
                ),
                attendedCount = presentCount + lateCount,
                totalCount = activeWorkers.size,
                progress = attendancePercent
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    label = "حاضرون",
                    value = presentCount.toString(),
                    accentColor = StatusPresentColor,
                    icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    label = "متأخرون",
                    value = lateCount.toString(),
                    accentColor = StatusLateColor,
                    icon = Icons.Filled.Schedule,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    label = "بالانتظار",
                    value = pendingCount.toString(),
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    icon = Icons.Filled.HourglassBottom,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            LocationVerificationStrip(
                verifiedLocation = verifiedLocation,
                isFetching = isFetchingLocation,
                onVerifyClick = {
                    if (SiteLocationHelper.hasCoarseLocationPermission(context)) {
                        onRequestLocation()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "كشف التحضير اليومي (${activeWorkers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onAddWorkerClick,
                    modifier = Modifier.testTag("today_add_worker_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة عامل")
                }
            }
        }

        if (activeWorkers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TodayFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        }

        if (activeWorkers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Groups,
                    title = "لا يوجد عمال مسجلون بعد",
                    subtitle = "أضف عمال الموقع لبدء إثبات الحضور والانصراف اليومي بالوقت والموقع.",
                    actionLabel = "إضافة أول عامل",
                    onActionClick = onAddWorkerClick
                )
            }
        } else if (filteredWorkers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Verified,
                    title = "لا توجد عناصر مطابقة للفلتر",
                    subtitle = "جرّب التبديل إلى (الكل) لعرض جميع عمال الموقع النشطين."
                )
            }
        } else {
            items(filteredWorkers, key = { it.id }) { worker ->
                val todayRecord = todayRecordsByWorker[worker.id]
                TodayWorkerCard(
                    worker = worker,
                    record = todayRecord,
                    isBusy = isBusy,
                    onQuickCheckIn = { onQuickCheckIn(worker) },
                    onOpenProofSheet = { onOpenProofSheet(worker, todayRecord) },
                    onCheckOut = { todayRecord?.let(onCheckOut) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun DailyHeroBanner(
    projectName: String,
    todayLabel: String,
    shiftStart: String,
    attendedCount: Int,
    totalCount: Int,
    progress: Float,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_site_banner_1795534471693),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xD90A2E23),
                                Color(0xB30F2438)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = projectName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = todayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "الدوام $shiftStart",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "نسبة الحضور المؤكد",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = "$attendedCount من $totalCount (${(progress * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD580)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color(0xFFFFBA42),
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationVerificationStrip(
    verifiedLocation: String?,
    isFetching: Boolean,
    onVerifyClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (verifiedLocation != null) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = if (verifiedLocation != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column {
                    Text(
                        text = verifiedLocation ?: "توثيق إحداثيات الموقع غير مفعل بعد",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (verifiedLocation != null) {
                            "يُرفق تلقائياً مع كل إثبات حضور سريع"
                        } else {
                            "اضغط لتحميل موقعك التقريبي كدليل حضور ميداني"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onVerifyClick,
                enabled = !isFetching,
                modifier = Modifier.testTag("verify_location_button")
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (verifiedLocation != null) "تحديث" else "توثيق")
                }
            }
        }
    }
}

@Composable
private fun TodayWorkerCard(
    worker: Worker,
    record: AttendanceRecord?,
    isBusy: Boolean,
    onQuickCheckIn: () -> Unit,
    onOpenProofSheet: () -> Unit,
    onCheckOut: () -> Unit,
) {
    val accent = record?.let { statusColor(it.statusEnum) } ?: MaterialTheme.colorScheme.primary
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_worker_card_${worker.id}")
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${worker.roleTitle} • ${worker.badgeCode} • ${worker.siteName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (record != null) {
                    StatusBadge(status = record.statusEnum)
                }
            }

            if (record != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProofMethodChip(method = record.proofMethodEnum)
                    if (record.checkInTimeLabel.isNotBlank() && record.checkInTimeLabel != "—") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append("دخول: ${record.checkInTimeLabel}")
                                    if (!record.checkOutTimeLabel.isNullOrBlank()) {
                                        append(" | خروج: ${record.checkOutTimeLabel}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (record == null) {
                    Button(
                        onClick = onQuickCheckIn,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("quick_checkin_button_${worker.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إثبات حضور سريع")
                    }
                } else if ((record.statusEnum == AttendanceStatus.PRESENT ||
                        record.statusEnum == AttendanceStatus.LATE) &&
                    record.checkOutTimeLabel.isNullOrBlank()
                ) {
                    Button(
                        onClick = onCheckOut,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("checkout_button_${worker.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تسجيل انصراف")
                    }
                }

                OutlinedButton(
                    onClick = onOpenProofSheet,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("proof_details_button_${worker.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (record == null) "توثيق مفصل" else "تعديل الإثبات")
                }
            }
        }
    }
}
