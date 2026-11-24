package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AttendanceStatus(val labelAr: String) {
    PRESENT("حاضر"),
    LATE("متأخر"),
    EXCUSED("بعذر"),
    ABSENT("غائب");

    companion object {
        fun safeFrom(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.name == raw } ?: PRESENT
    }
}

enum class ProofMethod(val labelAr: String) {
    GPS_VERIFIED("موثق بالموقع"),
    SUPERVISOR_LOG("توثيق المشرف"),
    PHOTO_ATTACHED("مرفق بصورة");

    companion object {
        fun safeFrom(raw: String?): ProofMethod =
            entries.firstOrNull { it.name == raw } ?: SUPERVISOR_LOG
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val projectName: String = "",
    val shiftStartHour: Long = 7L,
    val shiftStartMinute: Long = 30L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Worker(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val roleTitle: String = "",
    val siteName: String = "",
    val badgeCode: String = "",
    val phoneNumber: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val workerName: String = "",
    val roleTitle: String = "",
    val siteName: String = "",
    val dateKey: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
    val checkInTimeLabel: String = "",
    val checkOutTimeLabel: String? = null,
    val proofMethod: String = ProofMethod.SUPERVISOR_LOG.name,
    val locationSummary: String? = null,
    val photoUriString: String? = null,
    val notes: String? = null,
    @ServerTimestamp val checkInTimestamp: Timestamp? = null,
    val checkOutTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val statusEnum: AttendanceStatus
        get() = AttendanceStatus.safeFrom(status)

    val proofMethodEnum: ProofMethod
        get() = ProofMethod.safeFrom(proofMethod)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
