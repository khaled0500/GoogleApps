package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AttendanceRepository
import com.example.data.location.SiteLocationHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.ProofMethod
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.Worker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val currentUserId: String,
    private val defaultDisplayName: String,
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل ملف الموقع"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val workersState: StateFlow<UiState<List<Worker>>> = repository.observeWorkers(currentUserId)
        .map<List<Worker>, UiState<List<Worker>>> { list ->
            val sorted = list.sortedWith(
                compareByDescending<Worker> { it.active }
                    .thenBy { it.fullName }
            )
            UiState.Success(sorted)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing workers", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل قائمة العمال"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val recordsState: StateFlow<UiState<List<AttendanceRecord>>> =
        repository.observeAttendanceRecords(currentUserId)
            .map<List<AttendanceRecord>, UiState<List<AttendanceRecord>>> { list ->
                val sorted = list.sortedWith(
                    compareByDescending<AttendanceRecord> { it.dateKey }
                        .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                )
                UiState.Success(sorted)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing attendance records", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل سجل الحضور"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _verifiedLocationSummary = MutableStateFlow<String?>(null)
    val verifiedLocationSummary: StateFlow<String?> = _verifiedLocationSummary.asStateFlow()

    private val _isFetchingLocation = MutableStateFlow(false)
    val isFetchingLocation: StateFlow<Boolean> = _isFetchingLocation.asStateFlow()

    private var profileBootstrapped = false

    init {
        viewModelScope.launch {
            profileState.collect { state ->
                if (!profileBootstrapped && state is UiState.Success && state.data == null) {
                    profileBootstrapped = true
                    repository.saveProfile(
                        userId = currentUserId,
                        displayName = defaultDisplayName.ifBlank { "مشرف الموقع" },
                        projectName = "الموقع الرئيسي",
                        shiftStartHour = 7,
                        shiftStartMinute = 30
                    )
                }
            }
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    fun captureSiteLocation(context: Context) {
        if (_isFetchingLocation.value) return
        viewModelScope.launch {
            _isFetchingLocation.value = true
            val result = SiteLocationHelper.fetchCoarseLocationSummary(context.applicationContext)
            result.onSuccess { summary ->
                _verifiedLocationSummary.value = summary
                _feedbackMessage.value = "تم التحقق من إحداثيات الموقع بنجاح."
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "تعذر الحصول على الموقع الحالي."
            }
            _isFetchingLocation.value = false
        }
    }

    fun saveSiteProfile(
        displayName: String,
        projectName: String,
        shiftStartHour: Int,
        shiftStartMinute: Int,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveProfile(
                userId = currentUserId,
                displayName = displayName,
                projectName = projectName,
                shiftStartHour = shiftStartHour,
                shiftStartMinute = shiftStartMinute
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم حفظ إعدادات الموقع والدوام."
                onComplete()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر حفظ الإعدادات."
            }
        }
    }

    fun saveWorker(
        existingWorkerId: String?,
        fullName: String,
        roleTitle: String,
        siteName: String,
        badgeCode: String,
        phoneNumber: String?,
        active: Boolean,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = if (existingWorkerId.isNullOrBlank()) {
                repository.addWorker(
                    userId = currentUserId,
                    fullName = fullName,
                    roleTitle = roleTitle,
                    siteName = siteName,
                    badgeCode = badgeCode,
                    phoneNumber = phoneNumber,
                    active = active
                ).map { Unit }
            } else {
                repository.updateWorker(
                    userId = currentUserId,
                    workerId = existingWorkerId,
                    fullName = fullName,
                    roleTitle = roleTitle,
                    siteName = siteName,
                    badgeCode = badgeCode,
                    phoneNumber = phoneNumber,
                    active = active
                )
            }
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = if (existingWorkerId.isNullOrBlank()) {
                    "تمت إضافة العامل بنجاح."
                } else {
                    "تم تحديث بيانات العامل."
                }
                onComplete()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر حفظ بيانات العامل."
            }
        }
    }

    fun toggleWorkerActive(worker: Worker) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateWorker(
                userId = currentUserId,
                workerId = worker.id,
                fullName = worker.fullName,
                roleTitle = worker.roleTitle,
                siteName = worker.siteName,
                badgeCode = worker.badgeCode,
                phoneNumber = worker.phoneNumber,
                active = !worker.active
            )
            _isBusy.value = false
            result.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر تحديث حالة العامل."
            }
        }
    }

    fun deleteWorker(workerId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteWorker(currentUserId, workerId)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم حذف العامل من القائمة."
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر حذف العامل."
            }
        }
    }

    fun quickCheckInWorker(worker: Worker, profile: UserProfile?) {
        val suggestedStatus = suggestCheckInStatus(profile)
        val proofMethod = if (!_verifiedLocationSummary.value.isNullOrBlank()) {
            ProofMethod.GPS_VERIFIED
        } else {
            ProofMethod.SUPERVISOR_LOG
        }
        submitAttendanceProof(
            worker = worker,
            dateKey = todayDateKey(),
            status = suggestedStatus,
            checkInTimeLabel = currentTimeLabel(),
            proofMethod = proofMethod,
            locationSummary = _verifiedLocationSummary.value,
            photoUriString = null,
            notes = null,
            existingRecordId = null
        )
    }

    fun submitAttendanceProof(
        worker: Worker,
        dateKey: String,
        status: AttendanceStatus,
        checkInTimeLabel: String,
        proofMethod: ProofMethod,
        locationSummary: String?,
        photoUriString: String?,
        notes: String?,
        existingRecordId: String?,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val resolvedTimeLabel = when (status) {
                AttendanceStatus.PRESENT, AttendanceStatus.LATE ->
                    checkInTimeLabel.ifBlank { currentTimeLabel() }
                AttendanceStatus.EXCUSED, AttendanceStatus.ABSENT ->
                    checkInTimeLabel.ifBlank { "—" }
            }
            val result = repository.saveAttendanceProof(
                userId = currentUserId,
                worker = worker,
                dateKey = dateKey,
                status = status,
                checkInTimeLabel = resolvedTimeLabel,
                proofMethod = proofMethod,
                locationSummary = locationSummary,
                photoUriString = photoUriString,
                notes = notes,
                existingRecordId = existingRecordId
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم توثيق حالة ${worker.fullName}: ${status.labelAr}"
                onComplete()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر حفظ إثبات الحضور."
            }
        }
    }

    fun checkOutWorker(record: AttendanceRecord, notes: String? = null) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.recordCheckOut(
                userId = currentUserId,
                recordId = record.id,
                checkOutTimeLabel = currentTimeLabel(),
                notes = notes ?: record.notes
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم تسجيل انصراف ${record.workerName}"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر تسجيل الانصراف."
            }
        }
    }

    fun deleteAttendanceRecord(recordId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteAttendanceRecord(currentUserId, recordId)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "تم حذف السجل."
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "تعذر حذف السجل."
            }
        }
    }

    fun suggestCheckInStatus(profile: UserProfile?): AttendanceStatus {
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startHour = (profile?.shiftStartHour ?: 7L).toInt()
        val startMin = (profile?.shiftStartMinute ?: 30L).toInt()
        val graceDeadline = startHour * 60 + startMin + 15
        return if (nowMinutes > graceDeadline) AttendanceStatus.LATE else AttendanceStatus.PRESENT
    }

    fun buildReportText(
        dateLabel: String,
        projectName: String,
        records: List<AttendanceRecord>,
    ): String {
        val present = records.count { it.statusEnum == AttendanceStatus.PRESENT }
        val late = records.count { it.statusEnum == AttendanceStatus.LATE }
        val excused = records.count { it.statusEnum == AttendanceStatus.EXCUSED }
        val absent = records.count { it.statusEnum == AttendanceStatus.ABSENT }

        return buildString {
            appendLine("تقرير إثبات الحضور اليومي - $projectName")
            appendLine("التاريخ: $dateLabel")
            appendLine("الملخص: حاضر ($present) | متأخر ($late) | بعذر ($excused) | غائب ($absent)")
            appendLine("--------------------------------")
            records.forEachIndexed { index, rec ->
                append("${index + 1}. ${rec.workerName} (${rec.roleTitle}) - ${rec.statusEnum.labelAr}")
                if (rec.checkInTimeLabel.isNotBlank() && rec.checkInTimeLabel != "—") {
                    append(" | حضور: ${rec.checkInTimeLabel}")
                }
                if (!rec.checkOutTimeLabel.isNullOrBlank()) {
                    append(" | انصراف: ${rec.checkOutTimeLabel}")
                }
                if (!rec.locationSummary.isNullOrBlank()) {
                    append(" | ${rec.locationSummary}")
                }
                appendLine()
            }
        }
    }

    companion object {
        private const val TAG = "AttendanceVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun todayDateKey(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return fmt.format(Date())
        }

        fun yesterdayDateKey(): String {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return fmt.format(cal.time)
        }

        fun todayDisplayArabic(): String {
            val fmt = SimpleDateFormat("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar"))
            return fmt.format(Date())
        }

        fun currentTimeLabel(): String {
            val fmt = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("ar"))
            return fmt.format(Date())
        }
    }
}
