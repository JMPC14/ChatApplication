package com.example.chatapplication

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_latest_messages.*
import kotlinx.android.synthetic.main.latest_message_row.view.*
import java.lang.Exception

class LatestMessagesActivity : AppCompatActivity() {

    companion object {
        var currentUser: User? = null
        var NOT_USER_KEY = "NOT_USER_KEY"
        var NOTIFICATION_REPLY_KEY = "Text"
        var NOTIFICATION_ID = 1
    }

    override fun onResume() {
        super.onResume()
        adapter.clear()
        fetchBlocklist()
        refreshRecyclerViewMessages()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_latest_messages)

        supportActionBar?.title = "Latest Messages"

        recyclerLatestMessages.layoutManager = LinearLayoutManager(this)
        recyclerLatestMessages.adapter = adapter
        recyclerLatestMessages.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )

        adapter.setOnItemClickListener { item, view ->
            val row = item as LatestMessageRow
            if (row.chatPartnerUser == null) {
                return@setOnItemClickListener
            }
            val intent = Intent(this, ChatLogActivity::class.java)
            intent.putExtra(NewMessageActivity.USER_KEY, row.chatPartnerUser)
            startActivity(intent)
        }

        fab_NewMessage.setOnClickListener {
            startActivity(Intent(this, NewMessageActivity::class.java))
        }

        profileLayout.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        fetchCurrentUser()

        verifyUserLoggedIn()

        fetchContacts()

        listenForOnlineIndicators()
    }

    private val CHANNEL_ID = "chat_notifications"
    private val CHANNEL_NAME = "Chat Channel"

    fun displayNotification(chatMessage: ChatLogActivity.ChatMessage, chatUser: User) {
        if (chatMessage.fromId == FirebaseAuth.getInstance().uid || FirebaseManager.ignoreNotificationUid == chatUser.uid) {
            return
        }
        createNotificationChannel()

        val notIntent = Intent(this, ChatLogActivity::class.java)
            .putExtra(NOT_USER_KEY, chatUser)

        val pendingIntent = TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(notIntent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        var myBitmap: Bitmap? = null

        Picasso.get().load(chatUser.profileImageUrl).into(object: com.squareup.picasso.Target {
            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
            }

            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                val newBitmap = ThumbnailUtils.extractThumbnail(bitmap, 200, 200)
                myBitmap = RoundedBitmapDrawableFactory.create(resources, newBitmap).apply { isCircular = true }.toBitmap()
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
            }
        })

        var text: String? = null

        if (chatMessage.text.isEmpty() && chatMessage.imageUrl != null) {
            text = "${chatUser.username} sent an image"
        } else if (chatMessage.text.isEmpty() && chatMessage.fileUrl != null) {
            text = "${chatUser.username} sent a file"
        } else if (chatMessage.text.isNotEmpty()) {
            text = chatMessage.text
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.image_bird)
            .setContentTitle(chatUser.username)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setLargeIcon(myBitmap)

        val remoteInput = RemoteInput.Builder(NOTIFICATION_REPLY_KEY).setLabel("Reply").build()

        val replyIntent = Intent(this, ChatLogActivity::class.java)
            .putExtra(NOT_USER_KEY, chatUser)
        val replyPendingIntent = PendingIntent.getActivity(this, 0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val action = NotificationCompat.Action.Builder(R.drawable.image_bird, "Reply", replyPendingIntent).addRemoteInput(remoteInput).build()

        builder.addAction(action)

        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        val name = CHANNEL_NAME
        val descriptionText = "Description of channel"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
            .apply { description = descriptionText }

        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun fetchContacts() {
        val uid = FirebaseAuth.getInstance().uid
        FirebaseDatabase.getInstance().getReference("/users/$uid/contacts").addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                FirebaseManager.contacts = mutableListOf()
                p0.children.forEach {
                    FirebaseManager.contacts?.add(it.value.toString())
                }
            }
        })
    }

    private fun fetchBlocklist() {
        val uid = FirebaseAuth.getInstance().uid
        FirebaseDatabase.getInstance().getReference("/users/$uid/blocklist").addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                FirebaseManager.blocklist = mutableListOf()
                p0.children.forEach {
                    FirebaseManager.blocklist?.add(it.value.toString())
                }
                listenForLatestMessages()
            }
        })
    }

    val latestMessageMap = HashMap<String, ChatLogActivity.ChatMessage>()

    private fun refreshRecyclerViewMessages() {
        adapter.clear()
        val sorted = latestMessageMap.toList().sortedByDescending { it.second.time }.toMap()
        sorted.values.forEach { adapter.add(LatestMessageRow(it)) }
    }

    private fun listenForLatestMessages() {
        val fromId = FirebaseAuth.getInstance().uid
        val ref = FirebaseDatabase.getInstance().getReference("/latest-messages/$fromId")
        ref.addChildEventListener(object: ChildEventListener{
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                val chatMessage = p0.getValue(ChatLogActivity.ChatMessage::class.java) ?: return

                if (FirebaseManager.blocklist != null ) {
                    if (FirebaseManager.blocklist!!.contains(chatMessage.fromId) || FirebaseManager.blocklist!!.contains(chatMessage.toId)) {
                        return
                    }
                }

                latestMessageMap[p0.key!!] = chatMessage
                refreshRecyclerViewMessages()
                val key = p0.key!!
                val keyValue = p0.child("displayed").value
                val notRef = FirebaseDatabase.getInstance().getReference("/users/${chatMessage.fromId}")
                notRef.addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        val chatUser = p0.getValue(User::class.java)
                        if (keyValue != true) {
                            displayNotification(chatMessage, chatUser!!)
                        }
                        ref.child(key).child("displayed").setValue(true)
                    }
                })
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                val chatMessage = p0.getValue(ChatLogActivity.ChatMessage::class.java) ?: return

                latestMessageMap[p0.key!!] = chatMessage
                refreshRecyclerViewMessages()
                val key = p0.key!!
                val keyValue = p0.child("displayed").value
                val notRef2 = FirebaseDatabase.getInstance().getReference("/users/${chatMessage.fromId}")
                notRef2.addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        val chatUser = p0.getValue(User::class.java)
                        if (keyValue != true) {
                            displayNotification(chatMessage, chatUser!!)
                        }
                        ref.child(key).child("displayed").setValue(true)
                    }
                })
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }
        })
    }

    class LatestMessageRow(val chatMessage : ChatLogActivity.ChatMessage) : Item<GroupieViewHolder>() {
        var chatPartnerUser: User? = null

        override fun getLayout(): Int {
            return R.layout.latest_message_row
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            val chatPartnerId: String?
            if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                chatPartnerId = chatMessage.toId
                if (chatMessage.imageUrl != null || chatMessage.fileUrl != null) {
                    viewHolder.itemView.textLatestMessageRow.text = "You sent a file"
                } else {
                    viewHolder.itemView.textLatestMessageRow.text = "You: ${chatMessage.text}"
                }
            } else if (chatMessage.fileUrl == null && chatMessage.imageUrl == null) {
                chatPartnerId = chatMessage.fromId
                viewHolder.itemView.textLatestMessageRow.text = "Them: ${chatMessage.text}"
            } else {
                chatPartnerId = chatMessage.fromId
            }

            val ref = FirebaseDatabase.getInstance().getReference("/users/$chatPartnerId")
            ref.addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    chatPartnerUser = p0.getValue(User::class.java)
                    if ((chatMessage.imageUrl != null || chatMessage.fileUrl != null) && chatMessage.fromId != FirebaseAuth.getInstance().uid) {
                        viewHolder.itemView.textLatestMessageRow.text = "${chatPartnerUser?.username} sent a file"
                    }
                    viewHolder.itemView.usernameLatestMessageRow.text = chatPartnerUser?.username

                    val targetItemView = viewHolder.itemView.userImageLatestMessageRow
                    Picasso.get().load(chatPartnerUser?.profileImageUrl).into(targetItemView)
                }
            })

            val onlineRef = FirebaseDatabase.getInstance().getReference("/online-users/$chatPartnerId")
            onlineRef.addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    if (p0.value == true) {
                        viewHolder.itemView.onlineIndicatorLatestMessageRow.visibility = View.VISIBLE
                    }
                    else if (p0.value == false) {
                        viewHolder.itemView.onlineIndicatorLatestMessageRow.visibility = View.INVISIBLE
                    }
                }
            })
        }
    }

    private fun listenForOnlineIndicators() {
        val ref = FirebaseDatabase.getInstance().getReference("/online-users")
        ref.addChildEventListener(object: ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                if (FirebaseManager.contacts == null) {
                    return
                }
                else if (FirebaseManager.contacts!!.contains(p0.key!!)) {
                    refreshRecyclerViewMessages()
                }
//                if (p0.key!! == aUsername) {
//                    if (p0.value!! == true) {
//                        show indicator
//                    }
//                    if (p0.value!! == false) {
//                        hide indicator
//                    }
//                }
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }
        })
    }

    private val adapter = GroupAdapter<GroupieViewHolder>()

    private fun fetchCurrentUser() {
        val token: String? = null
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener {
            val token = it.result?.token
        }
        val uid = FirebaseAuth.getInstance().uid
        if (uid != null) {
            val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    currentUser = p0.getValue(User::class.java)
                    FirebaseManager.user = currentUser
                    Picasso.get().load(currentUser?.profileImageUrl).into(userImageLatestMessages)
                    usernameLatestMessages.text = currentUser?.username
                    Log.d("LatestMessages", "Current user is ${currentUser?.username}")
                    val onlineRef = FirebaseDatabase.getInstance().getReference("/online-users/${currentUser?.uid}")
                    onlineRef.setValue(true)
                }
            })
        }
    }

    private fun verifyUserLoggedIn() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            val intent = Intent(this, LauncherActivity::class.java)
            intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK).or(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.user_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.sign_out -> {
                val onlineRef = FirebaseDatabase.getInstance().getReference("/online-users/${currentUser?.uid}")
                onlineRef.setValue(false)
                FirebaseAuth.getInstance().signOut()
                FirebaseManager.attachedFile = null
                FirebaseManager.attachedFileSize = null
                FirebaseManager.attachedFileType = null
                FirebaseManager.attachedImage = null
                FirebaseManager.contacts = null
                FirebaseManager.otherUser = null
                FirebaseManager.user = null
                val intent = Intent(this, LauncherActivity::class.java)
                intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK) or (Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            R.id.user_contacts -> {
                startActivity(Intent(this, ContactsActivity::class.java))
            }
            R.id.user_blocklist -> {
                startActivity(Intent(this, BlocklistActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.latest_messages_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
}
