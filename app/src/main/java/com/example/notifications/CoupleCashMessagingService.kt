package com.example.notifications

import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CoupleCashMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val dbId = applicationContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
                .collection("users")
                .document(uid)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        } catch (e: Exception) {
            Log.w("CoupleCashFCM", "Gagal memperbarui FCM token: ${e.message}")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "CoupleCash"
        val body = message.notification?.body ?: message.data["body"] ?: "Ada pembaruan keuangan keluarga"
        CoupleCashNotificationHelper.showNotification(
            context = applicationContext,
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            title = title,
            body = body
        )
    }
}
