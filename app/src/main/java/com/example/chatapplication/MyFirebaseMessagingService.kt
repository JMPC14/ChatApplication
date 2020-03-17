package com.example.chatapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squareup.picasso.Picasso
import java.lang.Exception


class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val FCM_BASE_URL = "https://fcm.googleapis.com/"
        const val FCM_SERVER_KEY = "AIzaSyDmP6Xmw9EVIRY6yLYjmgz6fbnrfgER1BQ"
    }

    override fun onNewToken(p0: String) {
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener {
            FirebaseManager.token = it.result?.token
        }
    }

    override fun onMessageReceived(p0: RemoteMessage) {
        Log.d("TAG", "Data: ${p0.data.values}")
        Log.d("TAG", "Notification: ${p0.notification}")

        val mapData: Map<String, String> = p0.data

        handleNow(mapData)
    }

    private fun handleNow(data: Map<String, String>) {
        if (data.containsKey("title") && data.containsKey("message")) {
            sendNotification(data["title"] ?: error("First Error"), data["message"] ?: error("Second Error"))
        }
    }

    private fun sendNotification(uid: String, message: String) {
        val intent = Intent(this, ChatLogActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val channelId = LatestMessagesActivity.channelId
        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        var myBitmap: Bitmap? = null
        var user: User?
        var chatMessage: ChatLogActivity.ChatMessage?

        FirebaseDatabase.getInstance().getReference("/conversations/$message").addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                chatMessage = p0.getValue(ChatLogActivity.ChatMessage::class.java)

                FirebaseDatabase.getInstance().getReference("/users/$uid").addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        user = p0.getValue(User::class.java)

                        Picasso.get().load(user!!.profileImageUrl).into(object: com.squareup.picasso.Target {
                            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                            }

                            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                val newBitmap = ThumbnailUtils.extractThumbnail(bitmap, 200, 200)
                                myBitmap = RoundedBitmapDrawableFactory.create(resources, newBitmap).apply { isCircular = true }.toBitmap()
                            }

                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                            }
                        })

                        val notificationBuilder = NotificationCompat.Builder(this@MyFirebaseMessagingService, channelId)
                            .setSmallIcon(R.drawable.image_bird)
                            .setLargeIcon(myBitmap)
                            .setContentTitle(user!!.username)
                            .setContentText(chatMessage!!.text)
                            .setAutoCancel(true)
                            .setSound(defaultSoundUri)
                            .setContentIntent(pendingIntent)
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val channel = NotificationChannel(channelId, "Cloud Messaging Service", NotificationManager.IMPORTANCE_DEFAULT)
                        notificationManager.createNotificationChannel(channel)
                        notificationManager.notify(0, notificationBuilder.build())
                    }
                })
            }
        })
    }
}