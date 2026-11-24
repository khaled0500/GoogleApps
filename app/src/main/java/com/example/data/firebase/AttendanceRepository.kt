package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.ProofMethod
import com.example.data.model.UserProfile
import com.example.data.model.Worker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AttendanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً.")
        check(uid == expectedUserId) { "تغير المستخدم الحالي؛ تم رفض العملية." }
        return uid
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveProfile(
        userId: String,
        displayName: String,
        projectName: String,
        shiftStartHour: Int,
        shiftStartMinute: Int,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = displayName.trim().take(80)
            val cleanProject = projectName.trim().take(100)
            require(cleanName.isNotEmpty()) { "يرجى إدخال اسم المشرف أو الجهة." }
            require(cleanProject.isNotEmpty()) { "يرجى إدخال اسم الموقع أو المشروع." }
            require(shiftStartHour in 0..23) { "ساعة بدء الدوام غير صالحة." }
            require(shiftStartMinute in 0..59) { "دقيقة بدء الدوام غير صالحة." }

            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "projectName" to cleanProject,
                    "shiftStartHour" to shiftStartHour,
                    "shiftStartMinute" to shiftStartMinute,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "projectName" to cleanProject,
                        "shiftStartHour" to shiftStartHour,
                        "shiftStartMinute" to shiftStartMinute,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeWorkers(userId: String): Flow<List<Worker>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/workers"
        emitAll(
            db.collection("users").document(uid).collection("workers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Worker::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addWorker(
        userId: String,
        fullName: String,
        roleTitle: String,
        siteName: String,
        badgeCode: String,
        phoneNumber: String?,
        active: Boolean = true,
    ): Result<String> {
        var docPath = "users/$userId/workers"
        return try {
            val uid = requireUserId(userId)
            val cleanName = fullName.trim().take(80)
            val cleanRole = roleTitle.trim().take(60)
            val cleanSite = siteName.trim().take(80)
            val cleanBadge = badgeCode.trim().take(24)
            val cleanPhone = phoneNumber?.trim()?.take(24)?.takeIf { it.isNotEmpty() }

            require(cleanName.length >= 2) { "اسم العامل يجب أن يكون حرفين على الأقل." }
            require(cleanRole.isNotEmpty()) { "يرجى إدخال المهنة أو التخصص." }
            require(cleanSite.isNotEmpty()) { "يرجى إدخال اسم الموقع." }
            require(cleanBadge.isNotEmpty()) { "يرجى إدخال الرقم الوظيفي." }

            val docRef = db.collection("users").document(uid).collection("workers").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "fullName" to cleanName,
                "roleTitle" to cleanRole,
                "siteName" to cleanSite,
                "badgeCode" to cleanBadge,
                "phoneNumber" to cleanPhone,
                "active" to active,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateWorker(
        userId: String,
        workerId: String,
        fullName: String,
        roleTitle: String,
        siteName: String,
        badgeCode: String,
        phoneNumber: String?,
        active: Boolean,
    ): Result<Unit> {
        val docPath = "users/$userId/workers/$workerId"
        return try {
            val uid = requireUserId(userId)
            require(workerId.isNotBlank()) { "معرف العامل غير صالح." }
            val cleanName = fullName.trim().take(80)
            val cleanRole = roleTitle.trim().take(60)
            val cleanSite = siteName.trim().take(80)
            val cleanBadge = badgeCode.trim().take(24)
            val cleanPhone = phoneNumber?.trim()?.take(24) ?: ""

            require(cleanName.length >= 2) { "اسم العامل يجب أن يكون حرفين على الأقل." }
            require(cleanRole.isNotEmpty()) { "يرجى إدخال المهنة أو التخصص." }
            require(cleanSite.isNotEmpty()) { "يرجى إدخال اسم الموقع." }
            require(cleanBadge.isNotEmpty()) { "يرجى إدخال الرقم الوظيفي." }

            val docRef = db.collection("users").document(uid).collection("workers").document(workerId)
            docRef.update(
                mapOf(
                    "fullName" to cleanName,
                    "roleTitle" to cleanRole,
                    "siteName" to cleanSite,
                    "badgeCode" to cleanBadge,
                    "phoneNumber" to cleanPhone,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWorker(userId: String, workerId: String): Result<Unit> {
        val docPath = "users/$userId/workers/$workerId"
        return try {
            val uid = requireUserId(userId)
            require(workerId.isNotBlank()) { "معرف العامل غير صالح." }
            db.collection("users").document(uid).collection("workers").document(workerId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeAttendanceRecords(userId: String): Flow<List<AttendanceRecord>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/attendance_records"
        emitAll(
            db.collection("users").document(uid).collection("attendance_records")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AttendanceRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveAttendanceProof(
        userId: String,
        worker: Worker,
        dateKey: String,
        status: AttendanceStatus,
        checkInTimeLabel: String,
        proofMethod: ProofMethod,
        locationSummary: String?,
        photoUriString: String?,
        notes: String?,
        existingRecordId: String?,
    ): Result<Unit> {
        val recordId = existingRecordId?.takeIf { it.isNotBlank() } ?: "${dateKey}_${worker.id}"
        val docPath = "users/$userId/attendance_records/$recordId"
        return try {
            val uid = requireUserId(userId)
            require(worker.id.isNotBlank()) { "معرف العامل غير صالح." }
            require(dateKey.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) {
                "صيغة التاريخ غير صالحة."
            }
            val cleanWorkerName = worker.fullName.trim().take(80)
            val cleanRole = worker.roleTitle.trim().take(60)
            val cleanSite = worker.siteName.trim().take(80)
            val cleanCheckInLabel = checkInTimeLabel.trim().take(24)
            val cleanLocation = locationSummary?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val cleanPhoto = photoUriString?.trim()?.take(400)?.takeIf { it.isNotEmpty() }
            val cleanNotes = notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() }

            val docRef = db.collection("users").document(uid)
                .collection("attendance_records").document(recordId)
            val existingSnap = docRef.get().await()

            val shouldIncludeCheckInTimestamp =
                status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE

            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "workerId" to worker.id,
                    "workerName" to cleanWorkerName,
                    "roleTitle" to cleanRole,
                    "siteName" to cleanSite,
                    "dateKey" to dateKey,
                    "status" to status.name,
                    "checkInTimeLabel" to cleanCheckInLabel,
                    "proofMethod" to proofMethod.name,
                    "locationSummary" to cleanLocation,
                    "photoUriString" to cleanPhoto,
                    "notes" to cleanNotes,
                    "checkInTimestamp" to if (shouldIncludeCheckInTimestamp) FieldValue.serverTimestamp() else null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>

                docRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "status" to status.name,
                    "checkInTimeLabel" to cleanCheckInLabel,
                    "proofMethod" to proofMethod.name,
                    "locationSummary" to (cleanLocation ?: ""),
                    "photoUriString" to (cleanPhoto ?: ""),
                    "notes" to (cleanNotes ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (shouldIncludeCheckInTimestamp && existingSnap.getTimestamp("checkInTimestamp") == null) {
                    updatePayload["checkInTimestamp"] = FieldValue.serverTimestamp()
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordCheckOut(
        userId: String,
        recordId: String,
        checkOutTimeLabel: String,
        notes: String?,
    ): Result<Unit> {
        val docPath = "users/$userId/attendance_records/$recordId"
        return try {
            val uid = requireUserId(userId)
            require(recordId.isNotBlank()) { "معرف السجل غير صالح." }
            val cleanOutLabel = checkOutTimeLabel.trim().take(24)
            val cleanNotes = notes?.trim()?.take(300)

            val docRef = db.collection("users").document(uid)
                .collection("attendance_records").document(recordId)
            val updateMap = mutableMapOf<String, Any>(
                "checkOutTimeLabel" to cleanOutLabel,
                "checkOutTimestamp" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanNotes != null) {
                updateMap["notes"] = cleanNotes
            }
            docRef.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAttendanceRecord(userId: String, recordId: String): Result<Unit> {
        val docPath = "users/$userId/attendance_records/$recordId"
        return try {
            val uid = requireUserId(userId)
            require(recordId.isNotBlank()) { "معرف السجل غير صالح." }
            db.collection("users").document(uid)
                .collection("attendance_records").document(recordId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
