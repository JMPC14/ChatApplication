package com.example.chatapplication.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.media.ThumbnailUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.drawable.toBitmap
import com.example.chatapplication.ChatLogActivity
import com.example.chatapplication.models.ChatMessage
import com.example.chatapplication.R
import com.example.chatapplication.models.User
import com.example.chatapplication.objects.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squareup.picasso.Picasso
import java.lang.Exception

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val FCM_BASE_URL = "https://fcm.googleapis.com/"
        const val FCM_SERVER_KEY = "AIzaSyDmP6Xmw9EVIRY6yLYjmgz6fbnrfgER1BQ"
        var channelId = "chat_notifications"
        var NOTIFICATION_ID = 1
        var NOT_USER_KEY = "NOT_USER_KEY"
        var NOTIFICATION_REPLY_KEY = "Text"
        var CID = "CID"
    }

    override fun onNewToken(p0: String) {
        val uid = FirebaseAuth.getInstance().uid
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener {
            FirebaseManager.token = it.result?.token
            FirebaseDatabase.getInstance().getReference("/users/$uid").child("token").setValue(
                FirebaseManager.token
            )
        }
    }

    override fun onMessageReceived(p0: RemoteMessage) {
        Log.d("TAG", "Data: ${p0.data.values}")

        val mapData: Map<String, String> = p0.data

        handleNow(mapData)
    }

    private fun handleNow(data: Map<String, String>) {
        if (data.containsKey("title") && data.containsKey("message")) {
            sendNotification(data["title"] ?: error("Title null"), data["message"] ?: error("Message null"))
        }
    }

    private fun sendNotification(uid: String, message: String) {
        if ((FirebaseManager.foreground == true) || FirebaseManager.otherUser != null && FirebaseManager.otherUser!!.uid == uid) { return }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        var myBitmap: Bitmap? = null
        var user: User?
        var chatMessage: ChatMessage?

        FirebaseDatabase.getInstance().getReference("/conversations/$message")
            .addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                chatMessage = p0.getValue(ChatMessage::class.java)

                FirebaseDatabase.getInstance().getReference("/users/${FirebaseAuth.getInstance().uid}/blocklist")
                    .addListenerForSingleValueEvent(object: ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                        }

                        override fun onDataChange(p0: DataSnapshot) {
                            p0.children.forEach {
                                if (it.value == uid) {
                                    return
                                }
                            }

                            FirebaseDatabase.getInstance().getReference("/users/$uid")
                                .addListenerForSingleValueEvent(object: ValueEventListener {
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

                                        val intent = Intent(this@MyFirebaseMessagingService, ChatLogActivity::class.java)
                                        intent.putExtra(NOT_USER_KEY, user)

                                        val pendingIntent = TaskStackBuilder.create(this@MyFirebaseMessagingService)
                                            .addNextIntent(intent)
                                            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

                                        val notificationBuilder = NotificationCompat.Builder(this@MyFirebaseMessagingService, channelId)
                                            .setSmallIcon(R.drawable.image_bird)
                                            .setLargeIcon(myBitmap)
                                            .setContentTitle(user!!.username)
                                            .setAutoCancel(true)
                                            .setSound(defaultSoundUri)
                                            .setContentIntent(pendingIntent)

                                        if (chatMessage!!.text.isEmpty() && chatMessage!!.imageUrl != null) {
                                            notificationBuilder.setContentText("${user!!.username} sent an image")
                                        } else if (chatMessage!!.text.isEmpty() && chatMessage!!.fileUrl != null) {
                                            notificationBuilder.setContentText("${user!!.username} sent a file")
                                        } else if (chatMessage!!.text.isNotEmpty()) {
                                            notificationBuilder.setContentText(chatMessage!!.text)
                                        }

                                        val remoteInput = RemoteInput.Builder(NOTIFICATION_REPLY_KEY).setLabel("Reply").build()

                                        val parsedString: String = message.substringBefore("/")

                                        val replyIntent = Intent(this@MyFirebaseMessagingService, ChatLogActivity::class.java)
                                            .putExtra(NOT_USER_KEY, user)
                                            .putExtra(CID, parsedString)
                                        val replyPendingIntent = PendingIntent.getActivity(this@MyFirebaseMessagingService, 0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                                        val action = NotificationCompat.Action.Builder(R.drawable.image_bird, "Reply", replyPendingIntent).addRemoteInput(remoteInput).build()

                                        notificationBuilder.addAction(action)

                                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                        val channel = NotificationChannel(channelId, "Cloud Messaging Service", NotificationManager.IMPORTANCE_DEFAULT)
                                        notificationManager.createNotificationChannel(channel)
                                        NotificationManagerCompat.from(this@MyFirebaseMessagingService).notify(NOTIFICATION_ID, notificationBuilder.build())
                                    }
                                })
                        }
                    })
            }
        })
    }
}