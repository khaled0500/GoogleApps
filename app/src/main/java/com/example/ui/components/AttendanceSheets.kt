package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.location.SiteLocationHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.ProofMethod
import com.example.data.model.UserProfile
import com.example.data.model.Worker
import com.example.ui.viewmodel.AttendanceViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AttendanceProofSheet(
    worker: Worker,
    existingRecord: AttendanceRecord?,
    suggestedStatus: AttendanceStatus,
    currentVerifiedLocation: String?,
    isFetchingLocation: Boolean,
    isBusy: Boolean,
    onRequestLocation: () -> Unit,
    onSave: (
        status: AttendanceStatus,
        timeLabel: String,
        proofMethod: ProofMethod,
        locationSummary: String?,
        photoUriString: String?,
        notes: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedStatus by remember(worker.id, existingRecord?.id) {
        mutableStateOf(existingRecord?.statusEnum ?: suggestedStatus)
    }
    var timeLabel by remember(worker.id, existingRecord?.id) {
        mutableStateOf(
            existingRecord?.checkInTimeLabel?.takeIf { it.isNotBlank() }
                ?: AttendanceViewModel.currentTimeLabel()
        )
    }
    var locationText by remember(worker.id, existingRecord?.id, currentVerifiedLocation) {
        mutableStateOf(
            existingRecord?.locationSummary?.takeIf { it.isNotBlank() }
                ?: currentVerifiedLocation
                ?: ""
        )
    }
    var photoUriString by remember(worker.id, existingRecord?.id) {
        mutableStateOf(existingRecord?.photoUriString?.takeIf { it.isNotBlank() })
    }
    var notes by remember(worker.id, existingRecord?.id) {
        mutableStateOf(existingRecord?.notes ?: "")
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRequestLocation()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Non-persistable picker URIs remain valid for the active session
            }
            photoUriString = uri.toString().take(400)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "توثيق حضور: ${worker.fullName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${worker.roleTitle} • ${worker.siteName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "حالة الحضور",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttendanceStatus.entries.forEach { status ->
                    val accent = statusColor(status)
                    val selected = selectedStatus == status
                    FilterChip(
                        selected = selected,
                        onClick = { selectedStatus = status },
                        label = { Text(status.labelAr) },
                        leadingIcon = {
                            Icon(
                                imageVector = statusIcon(status),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent,
                            selectedLeadingIconColor = accent
                        )
                    )
                }
            }

            if (selectedStatus == AttendanceStatus.PRESENT || selectedStatus == AttendanceStatus.LATE) {
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { if (it.length <= 24) timeLabel = it },
                    label = { Text("وقت الحضور") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { timeLabel = AttendanceViewModel.currentTimeLabel() }) {
                            Text("الآن")
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Schedule, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = locationText,
                onValueChange = { if (it.length <= 160) locationText = it },
                label = { Text("إثبات الموقع (إحداثيات أو اسم البوابة)") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                },
                trailingIcon = {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            onClick = {
                                if (SiteLocationHelper.hasCoarseLocationPermission(context)) {
                                    onRequestLocation()
                                } else {
                                    locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = "تحديد الموقع",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحديد")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_proof_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (photoUriString == null) "إرفاق صورة إثبات" else "تغيير الصورة")
                }

                if (photoUriString != null) {
                    TextButton(onClick = { photoUriString = null }) {
                        Text("إزالة", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (!photoUriString.isNullOrBlank()) {
                AsyncImage(
                    model = photoUriString,
                    contentDescription = "صورة إثبات الحضور",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 300) notes = it },
                label = { Text("ملاحظات المشرف (اختياري)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            val resolvedMethod = when {
                !photoUriString.isNullOrBlank() -> ProofMethod.PHOTO_ATTACHED
                locationText.isNotBlank() -> ProofMethod.GPS_VERIFIED
                else -> ProofMethod.SUPERVISOR_LOG
            }

            Button(
                onClick = {
                    onSave(
                        selectedStatus,
                        timeLabel,
                        resolvedMethod,
                        locationText.takeIf { it.isNotBlank() },
                        photoUriString,
                        notes.takeIf { it.isNotBlank() }
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_proof_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("حفظ وتأكيد الإثبات")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerFormSheet(
    existingWorker: Worker?,
    defaultSiteName: String,
    isBusy: Boolean,
    onSave: (
        fullName: String,
        roleTitle: String,
        siteName: String,
        badgeCode: String,
        phoneNumber: String?,
        active: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fullName by remember(existingWorker?.id) { mutableStateOf(existingWorker?.fullName ?: "") }
    var roleTitle by remember(existingWorker?.id) { mutableStateOf(existingWorker?.roleTitle ?: "") }
    var siteName by remember(existingWorker?.id) {
        mutableStateOf(existingWorker?.siteName ?: defaultSiteName)
    }
    var badgeCode by remember(existingWorker?.id) {
        mutableStateOf(existingWorker?.badgeCode ?: "")
    }
    var phoneNumber by remember(existingWorker?.id) {
        mutableStateOf(existingWorker?.phoneNumber ?: "")
    }
    var active by remember(existingWorker?.id) { mutableStateOf(existingWorker?.active ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingWorker == null) "إضافة عامل جديد" else "تعديل بيانات العامل",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { if (it.length <= 80) fullName = it },
                label = { Text("الاسم الكامل *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("worker_name_input")
            )

            OutlinedTextField(
                value = roleTitle,
                onValueChange = { if (it.length <= 60) roleTitle = it },
                label = { Text("المهنة / التخصص * (مثال: كهربائي، فني)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("worker_role_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = badgeCode,
                    onValueChange = { if (it.length <= 24) badgeCode = it },
                    label = { Text("الرقم الوظيفي *") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("worker_badge_input")
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { if (it.length <= 24) phoneNumber = it },
                    label = { Text("الجوال (اختياري)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("worker_phone_input")
                )
            }

            OutlinedTextField(
                value = siteName,
                onValueChange = { if (it.length <= 80) siteName = it },
                label = { Text("موقع العمل *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("worker_site_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "عامل نشط في الوردية الحالية",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "يظهر العامل النشط في قائمة التحضير اليومي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = { active = it }
                )
            }

            if (!validationError.isNullOrBlank()) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    when {
                        fullName.trim().length < 2 ->
                            validationError = "يرجى إدخال اسم العامل (حرفان على الأقل)."
                        roleTitle.trim().isEmpty() ->
                            validationError = "يرجى إدخال المهنة أو التخصص."
                        badgeCode.trim().isEmpty() ->
                            validationError = "يرجى إدخال الرقم الوظيفي."
                        siteName.trim().isEmpty() ->
                            validationError = "يرجى إدخال اسم الموقع."
                        else -> {
                            validationError = null
                            onSave(
                                fullName.trim(),
                                roleTitle.trim(),
                                siteName.trim(),
                                badgeCode.trim(),
                                phoneNumber.trim().takeIf { it.isNotEmpty() },
                                active
                            )
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_worker_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (existingWorker == null) "إضافة العامل" else "حفظ التعديلات")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsSheet(
    profile: UserProfile?,
    isBusy: Boolean,
    onSave: (displayName: String, projectName: String, startHour: Int, startMinute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayName by remember(profile) {
        mutableStateOf(profile?.displayName ?: "مشرف الموقع")
    }
    var projectName by remember(profile) {
        mutableStateOf(profile?.projectName ?: "الموقع الرئيسي")
    }
    var startHour by remember(profile) {
        mutableIntStateOf((profile?.shiftStartHour ?: 7L).toInt())
    }
    var startMinute by remember(profile) {
        mutableIntStateOf((profile?.shiftStartMinute ?: 30L).toInt())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "إعدادات الموقع والوردية",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 80) displayName = it },
                label = { Text("اسم المشرف / الجهة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = projectName,
                onValueChange = { if (it.length <= 100) projectName = it },
                label = { Text("اسم المشروع أو الموقع الافتراضي") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "موعد بداية الدوام (يُحسب التأخير بعد +15 دقيقة)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { startHour = (startHour + 23) % 24 },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("- ساعة")
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(
                        text = String.format("%02d:%02d", startHour, startMinute),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                    )
                }
                OutlinedButton(
                    onClick = { startHour = (startHour + 1) % 24 },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ ساعة")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 15, 30, 45).forEach { mins ->
                    FilterChip(
                        selected = startMinute == mins,
                        onClick = { startMinute = mins },
                        label = { Text(String.format(":%02d", mins)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = {
                    if (displayName.isNotBlank() && projectName.isNotBlank()) {
                        onSave(displayName.trim(), projectName.trim(), startHour, startMinute)
                    }
                },
                enabled = !isBusy && displayName.isNotBlank() && projectName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("حفظ الإعدادات")
            }
        }
    }
}

@Composable
fun AccountDialog(
    displayName: String?,
    email: String?,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("حساب المشرف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = displayName?.takeIf { it.isNotBlank() } ?: "مستخدم مسجل",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!email.isNullOrBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "جميع سجلات الحضور والعمال محفوظة ومشفرة ضمن حسابك السحابي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
