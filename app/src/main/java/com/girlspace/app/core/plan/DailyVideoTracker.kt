package com.girlspace.app.core.plan

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object DailyVideoTracker {

    private val db = FirebaseFirestore.getInstance()

    /** Returns how many videos user uploaded today */
    suspend fun getTodayCount(): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return 0
        val today = todayKey()

        val doc = db.collection("daily_usage")
            .document(uid)
            .get()
            .await()

        return doc.getLong(today)?.toInt() ?: 0
    }

    /** Increments count for today */
    suspend fun increment() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val today = todayKey()

        db.collection("daily_usage")
            .document(uid)
            .update(today, FieldValue.increment(1))
            .await()
    }

    /** Generates a field key like “2025-11-29” */
    private fun todayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
