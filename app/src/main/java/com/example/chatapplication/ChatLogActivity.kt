package com.example.chatapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.RemoteInput
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatapplication.api.ApiClient.apiService
import com.example.chatapplication.api.MyFirebaseMessagingService
import com.example.chatapplication.models.ChatMessage
import com.example.chatapplication.models.User
import com.example.chatapplication.objects.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.JsonObject
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.android.synthetic.main.activity_chat_log.*
import kotlinx.android.synthetic.main.chat_message_from.view.*
import kotlinx.android.synthetic.main.chat_message_from_file.view.*
import kotlinx.android.synthetic.main.chat_message_from_image.view.*
import kotlinx.android.synthetic.main.chat_message_to.view.*
import kotlinx.android.synthetic.main.chat_message_to_file.view.*
import kotlinx.android.synthetic.main.chat_message_to_image.view.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.*

class ChatLogActivity : AppCompatActivity() {

    private val viewModel by lazy { ViewModelProvider(this)[ChatLogViewModel::class.java] }

    companion object {
        const val OTHER_USER_KEY = "OTHER_USER_KEY"
    }

    val adapter = GroupAdapter<GroupieViewHolder>()
    var toUser: User? = null

    override fun onResume() {
        super.onResume()
        FirebaseManager.ignoreNotificationUid = toUser!!.uid
    }

    override fun onPause() {
        FirebaseManager.ignoreNotificationUid = null
        super.onPause()
    }

    override fun onStop() {
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${toUser!!.uid}/${FirebaseManager.user!!.uid}")
        ref.child("typing").setValue(false)
        FirebaseManager.attachedImage = null
        FirebaseManager.attachedFile = null
        FirebaseManager.attachedFileSize = null
        FirebaseManager.attachedFileType = null
        FirebaseManager.otherUser = null
        FirebaseManager.latestMessageSeen = null
        FirebaseManager.latestMessageOtherUserSeen = null
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_log)

        if (intent.getParcelableExtra<User>(MyFirebaseMessagingService.NOT_USER_KEY) != null) {
            FirebaseManager.conversationId = intent.getStringExtra(MyFirebaseMessagingService.CID)
            val uid = FirebaseAuth.getInstance().uid
            if (uid == null) {
                val intent = Intent(this, LauncherActivity::class.java)
                intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK).or(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        FirebaseManager.user = p0.getValue(User::class.java)
                    }
                })
            }
        }

        toUser = intent.getParcelableExtra(MyFirebaseMessagingService.NOT_USER_KEY) ?:
            intent.getParcelableExtra(LatestMessagesActivity.LAT_USER_KEY) ?:
            intent.getParcelableExtra(NewMessageActivity.USER_KEY)
        FirebaseManager.otherUser = toUser

        val remoteReply = RemoteInput.getResultsFromIntent(intent)

        if (remoteReply != null) {
            val message = remoteReply.getCharSequence(MyFirebaseMessagingService.NOTIFICATION_REPLY_KEY) as String
            FirebaseManager.notificationTempMessage = message
            performSendMessage()
            FirebaseManager.notificationTempMessage = null
            finish()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(MyFirebaseMessagingService.NOTIFICATION_ID)
        }

        if (savedInstanceState != null) {
            if (viewModel.photoAttachmentUri != null) {
                Picasso.get().load(viewModel.photoAttachmentUri).into(imageAttachedImageView)
                imageAttachedLayout.visibility = View.VISIBLE
                sendMessageButton.isEnabled = true
                photoAttachmentUri = viewModel.photoAttachmentUri
            }
            else if (viewModel.fileAttachmentUri != null) {
                fileAttachedLayout.visibility = View.VISIBLE
                sendMessageButton.isEnabled = true
                fileAttachmentUri = viewModel.fileAttachmentUri
            }
        }

        recyclerChatLog.adapter = adapter
        recyclerChatLog.layoutManager = LinearLayoutManager(this)

        supportActionBar?.title = toUser?.username
        supportActionBar?.elevation = 0f

        enterMessageText.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sendMessageButton.isEnabled = enterMessageText.text.isNotEmpty() || photoAttachmentUri != null || fileAttachmentUri != null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                sendMessageButton.isEnabled = enterMessageText.text.isNotEmpty() || photoAttachmentUri != null || fileAttachmentUri != null
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sendMessageButton.isEnabled = enterMessageText.text.isNotEmpty() || photoAttachmentUri != null || fileAttachmentUri != null
                val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${toUser!!.uid}/${FirebaseManager.user!!.uid}")
                if (enterMessageText.text.isNotEmpty()) {
                    ref.child("typing").setValue(true)
                } else {
                    ref.child("typing").setValue(false)
                }
            }
        })

        enterMessageText.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                performSendMessage()
                return@OnKeyListener true
            }
            false
        })

        sendMessageButton.setOnClickListener {
            if (imageAttachedLayout.visibility == View.VISIBLE) {
                uploadImage()
            }
            if (fileAttachedLayout.visibility == View.VISIBLE) {
                uploadFile()
            }
            else if (fileAttachedLayout.visibility == View.INVISIBLE && imageAttachedLayout.visibility == View.INVISIBLE) {
                performSendMessage()
            }
        }

        attachPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 0)
        }

        attachFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            startActivityForResult(intent, 1)
        }

        imageAttachedLayout.setOnLongClickListener {
            val pop = PopupMenu(this, it)
            pop.inflate(R.menu.chat_log_remove_attachment)
            pop.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.remove_attachment -> {
                        imageAttachedLayout.visibility = View.INVISIBLE
                        sendMessageButton.isEnabled = false
                    }
                }
                true
            }
            pop.show()
            true
        }

        fileAttachedLayout.setOnLongClickListener {
            val pop = PopupMenu(this, it)
            pop.inflate(R.menu.chat_log_remove_attachment)
            pop.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.remove_attachment -> {
                        fileAttachedLayout.visibility = View.INVISIBLE
                        sendMessageButton.isEnabled = false
                    }
                }
                true
            }
            pop.show()
            true
        }

        FirebaseDatabase.getInstance().getReference("/users/${toUser!!.uid}").child("token").addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {
                FirebaseManager.otherUserToken = p0.value.toString()
            }

            override fun onCancelled(p0: DatabaseError) {
            }
        })

        listenForMessages()
    }

    private fun buildNotificationPayload(): JsonObject? {
        val payload = JsonObject()
        payload.addProperty("to", FirebaseManager.otherUserToken)
        val data = JsonObject()
        data.addProperty("title", FirebaseAuth.getInstance().uid)
        data.addProperty("message", FirebaseManager.messageKey)
        payload.add("data", data)
        return payload
    }

    private fun updateLatestMessageSeen() {
        if (adapter.itemCount != 0) {
            val test = adapter.getItem(recyclerChatLog.adapter!!.itemCount - 1)
            if (test.layout == R.layout.chat_message_to || test.layout == R.layout.chat_message_to_sequential) {
                val itemTo = adapter.getItem(recyclerChatLog.adapter!!.itemCount - 1) as ChatItem
                FirebaseManager.latestMessageSeen = itemTo.chatMessage.id
            } else if (test.layout == R.layout.chat_message_to_image) {
                val itemToImage =
                    adapter.getItem(recyclerChatLog.adapter!!.itemCount - 1) as ChatItemImage
                FirebaseManager.latestMessageSeen = itemToImage.chatMessage.id
            } else if (test.layout == R.layout.chat_message_to_file) {
                val itemToFile =
                    adapter.getItem(recyclerChatLog.adapter!!.itemCount - 1) as ChatItemFile
                FirebaseManager.latestMessageSeen = itemToFile.chatMessage.id
            }
            if (FirebaseManager.latestMessageSeen != null) {
                val ref = FirebaseDatabase.getInstance()
                    .getReference("/user-messages/${toUser!!.uid}/${FirebaseManager.user!!.uid}")
                ref.child("latestMessageSeen").setValue(FirebaseManager.latestMessageSeen)
            }
        }
    }

    override fun onUserInteraction() {
        updateLatestMessageSeen()
        super.onUserInteraction()
    }

    private var photoAttachmentUri: Uri? = null
    private var fileAttachmentUri: Uri? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == Activity.RESULT_OK && data != null) {
            photoAttachmentUri = data.data
            viewModel.photoAttachmentUri = data.data
            Picasso.get().load(photoAttachmentUri).into(imageAttachedImageView)
            imageAttachedLayout.visibility = View.VISIBLE
            sendMessageButton.isEnabled = true
        }

        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            fileAttachmentUri = data.data
            viewModel.fileAttachmentUri = data.data
            fileAttachedLayout.visibility = View.VISIBLE
            sendMessageButton.isEnabled = true
        }
    }

    private fun uploadFile() {
        if (fileAttachmentUri == null) { return }
        val filename = UUID.randomUUID().toString()
        val ref = FirebaseStorage.getInstance().getReference("/files/$filename")
        ref.putFile(fileAttachmentUri!!).addOnSuccessListener {
            FirebaseManager.attachedFileType = it.metadata?.contentType
            FirebaseManager.attachedFileSize = "%.2f".format(it.metadata!!.sizeBytes.toDouble() / 1000).toDouble()
            ref.downloadUrl.addOnSuccessListener {
                FirebaseManager.attachedFile = it.toString()
                performSendMessage()
            }
        }
    }

    private fun uploadImage() {
        if (photoAttachmentUri == null) { return }
        val filename = UUID.randomUUID().toString()
        val ref = FirebaseStorage.getInstance().getReference("/images/$filename")
        ref.putFile(photoAttachmentUri!!).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener {
                FirebaseManager.attachedImage = it.toString()
                performSendMessage()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun listenForMessages() {
        val fromId = FirebaseAuth.getInstance().uid
        val toId = toUser!!.uid
        var cid: String
        var newRef: DatabaseReference
        if (FirebaseManager.blocklist != null && FirebaseManager.blocklist!!.contains(toUser!!.uid)) { return }
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId")
        ref.addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                ref.addChildEventListener(object: ChildEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onChildMoved(p0: DataSnapshot, p1: String?) {
                    }

                    override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                        if (p0.key!! == "typing") {
                            if (p0.value == true) {
                                userTypingIndicator.textSize = 14f
                                userTypingIndicator.visibility = View.VISIBLE
                                userTypingIndicator.text = "${toUser!!.username} is typing..."
                            }
                            else if (p0.value != true) {
                                userTypingIndicator.textSize = 0f
                                userTypingIndicator.visibility = View.INVISIBLE
                            }
                        }

                        if (p0.key!! == "latestMessageSeen") {
                            FirebaseManager.latestMessageOtherUserSeen = p0.value.toString()
                            adapter.notifyDataSetChanged()
                        }
                    }

                    override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                        if (p0.key!! == "latestMessageSeen") {
                            FirebaseManager.latestMessageOtherUserSeen = p0.value.toString()
                            adapter.notifyDataSetChanged()
                        }

                        if (p0.key!! == "typing") {
                            if (p0.value == true) {
                                userTypingIndicator.textSize = 14f
                                userTypingIndicator.visibility = View.VISIBLE
                                userTypingIndicator.text = "${toUser!!.username} is typing..."
                            }
                            else if (p0.value != true) {
                                userTypingIndicator.textSize = 0f
                                userTypingIndicator.visibility = View.INVISIBLE
                            }
                        }
                    }

                    override fun onChildRemoved(p0: DataSnapshot) {
                    }
                })

                if (p0.hasChild("cid")) {
                    cid = p0.child("cid").value.toString()
                    FirebaseManager.conversationId = cid
                    newRef = FirebaseDatabase.getInstance().getReference("/conversations/$cid")
                    newRef.addChildEventListener(object: ChildEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                        }

                        override fun onChildMoved(p0: DataSnapshot, p1: String?) {
                        }

                        override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                        }

                        override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                            if (p0.child("hidden").value == true) { return }

                            val chatMessage = p0.getValue(ChatMessage::class.java)
                            if (chatMessage != null) {

                                var sequentialFrom = false
                                var sequentialTo = false

                                if (adapter.itemCount != 0) {
                                    val test = adapter.getItem(adapter.itemCount - 1)
                                    when (test.layout) {
                                        R.layout.chat_message_from,
                                        R.layout.chat_message_from_sequential,
                                        R.layout.chat_message_from_image,
                                        R.layout.chat_message_from_file -> {
                                            sequentialFrom = true
                                        }
                                        R.layout.chat_message_to,
                                        R.layout.chat_message_to_sequential,
                                        R.layout.chat_message_to_image,
                                        R.layout.chat_message_to_file -> {
                                            sequentialTo = true
                                        }
                                    }
                                }

                                val user: User?
                                val sequential: Boolean?

                                if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                                    user = FirebaseManager.user
                                    sequential = sequentialFrom
                                } else {
                                    user = toUser
                                    sequential = sequentialTo
                                }

                                if (chatMessage.imageUrl == null && chatMessage.fileUrl == null) {
                                    adapter.add(ChatItem(p0.key!!, chatMessage, user!!, sequential))
                                }

                                else if (chatMessage.fileUrl != null) {
                                    adapter.add(ChatItemFile(p0.key!!, chatMessage, user!!, sequential))
                                }

                                else if (chatMessage.imageUrl != null) {
                                    adapter.add(ChatItemImage(p0.key!!, chatMessage, user!!, sequential))
                                }
                                recyclerChatLog.scrollToPosition(adapter.itemCount - 1)
                                recyclerChatLog.adapter = adapter
                            }
                        }

                        override fun onChildRemoved(p0: DataSnapshot) {
                        }
                    })
                }
            }
        })
    }

    private fun performSendMessage() {
        val text = if (FirebaseManager.notificationTempMessage != null) {
            FirebaseManager.notificationTempMessage
        } else {
            enterMessageText.text.toString()
        }

        val fromId = FirebaseAuth.getInstance().uid ?: return
        val toId = toUser!!.uid
        val ref = FirebaseDatabase.getInstance().getReference("/conversations/${FirebaseManager.conversationId}").push()
        FirebaseManager.messageKey = "${FirebaseManager.conversationId}/${ref.key}"
        val chatMessage: ChatMessage?
        val time = System.currentTimeMillis() / 1000
        val year = LocalDateTime.now().year
        val month = LocalDateTime.now().month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val date = LocalDateTime.now().dayOfMonth
        val hour = LocalDateTime.now().hour
        val minute = LocalDateTime.now().minute
        val newMinute: String?
        newMinute = if (minute < 10) {
            "0$minute"
        } else {
            minute.toString()
        }
        val timestamp = "$date $month, $hour:$newMinute"

        if (text != null) {

            chatMessage = when {
                FirebaseManager.attachedImage != null -> {
                    ChatMessage(
                        ref.key!!,
                        text,
                        fromId,
                        toId,
                        timestamp,
                        time,
                        FirebaseManager.attachedImage!!
                    )
                }
                FirebaseManager.attachedFile != null -> {
                    ChatMessage(
                        ref.key!!,
                        text,
                        fromId,
                        toId,
                        timestamp,
                        time,
                        FirebaseManager.attachedFile!!,
                        FirebaseManager.attachedFileSize!!,
                        FirebaseManager.attachedFileType!!
                    )
                }
                else -> {
                    ChatMessage(
                        ref.key!!,
                        text,
                        fromId,
                        toId,
                        timestamp,
                        time
                    )
                }
            }

            ref.setValue(chatMessage)
                .addOnSuccessListener {
                    enterMessageText.text.clear()
                    recyclerChatLog.scrollToPosition(adapter.itemCount)
                }

            val latestMessageRef =
                FirebaseDatabase.getInstance().getReference("/latest-messages/$fromId/$toId")
            latestMessageRef.setValue(chatMessage)

            val latestMessageToRef =
                FirebaseDatabase.getInstance().getReference("/latest-messages/$toId/$fromId")
            latestMessageToRef.setValue(chatMessage)

            val payload = buildNotificationPayload()
            apiService.sendNotification(payload)!!.enqueue(
                object : Callback<JsonObject?> {
                    override fun onResponse(
                        call: Call<JsonObject?>?,
                        response: Response<JsonObject?>
                    ) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@ChatLogActivity, "Notification send successful", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFailure(
                        call: Call<JsonObject?>?,
                        t: Throwable?
                    ) {}
                })

            FirebaseManager.attachedImage = null
            FirebaseManager.attachedFile = null
            FirebaseManager.attachedFileSize = null
            FirebaseManager.attachedFileType = null
            imageAttachedLayout.visibility = View.INVISIBLE
            fileAttachedLayout.visibility = View.INVISIBLE
            sendMessageButton.isEnabled = false
        }
    }

    inner class ChatItem(val id: String, val chatMessage: ChatMessage, val user: User, private val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            if (user.uid == FirebaseManager.user!!.uid) {
                return if (sequential) {
                    R.layout.chat_message_from_sequential
                } else {
                    R.layout.chat_message_from
                }
            }
            return if (sequential) {
                R.layout.chat_message_to_sequential
            } else {
                R.layout.chat_message_to
            }
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (layout == R.layout.chat_message_from || layout == R.layout.chat_message_from_sequential) {
                if (chatMessage.id == FirebaseManager.latestMessageOtherUserSeen) {
                    viewHolder.itemView.messageSeen.visibility = View.VISIBLE
                } else {
                    viewHolder.itemView.messageSeen.visibility = View.GONE
                }

                if (!sequential) {
                    Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageMessageFrom)
                }

                viewHolder.itemView.textMessageFrom.text = chatMessage.text
                viewHolder.itemView.timestampMessageFrom.text = chatMessage.timestamp

                viewHolder.itemView.textMessageFrom.setOnLongClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.hide_message -> {
                                viewHolder.itemView.layoutParams.height = 0
                                val ref = FirebaseDatabase.getInstance()
                                    .getReference("/conversations/${FirebaseManager.conversationId}/${chatMessage.id}")
                                ref.child("hidden").setValue(true)
                                adapter.clear()
                                listenForMessages()
                            }
                        }
                        true
                    }
                    pop.show()
                    true
                }
            } else if (layout == R.layout.chat_message_to || layout == R.layout.chat_message_to_sequential) {
                viewHolder.itemView.textMessageTo.text = chatMessage.text
                viewHolder.itemView.timestampMessageTo.text = chatMessage.timestamp
                if (!sequential) {
                    Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageMessageTo)

                    viewHolder.itemView.imageMessageTo.setOnClickListener {
                        val pop = PopupMenu(it.context, it)
                        pop.inflate(R.menu.chat_log_image_tap_menu)
                        pop.setOnMenuItemClickListener {
                            when (it.itemId) {
                                R.id.view_profile -> {
                                    val intent =
                                        Intent(viewHolder.itemView.context, ProfileActivity::class.java)
                                    intent.putExtra(OTHER_USER_KEY, user)
                                    viewHolder.itemView.context.startActivity(intent)
                                }
                                R.id.block_user -> {
                                    if (FirebaseManager.blocklist != null) {
                                        FirebaseManager.blocklist!!.add(user.uid)
                                        val ref = FirebaseDatabase.getInstance().getReference("/users/${FirebaseManager.user!!.uid}/blocklist")
                                        ref.setValue(FirebaseManager.blocklist)
                                        startActivity(Intent(viewHolder.itemView.context, LatestMessagesActivity::class.java))
                                        finish()
                                    }
                                }
                            }
                            true
                        }
                        pop.show()
                    }
                }

                viewHolder.itemView.textMessageTo.setOnLongClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.hide_message -> {
                                viewHolder.itemView.layoutParams.height = 0
                                val ref = FirebaseDatabase.getInstance().getReference("/conversations/${FirebaseManager.conversationId}/$id")
                                ref.child("hidden").setValue(true)
                                adapter.clear()
                                listenForMessages()
                            }
                        }
                        true
                    }
                    pop.show()
                    true
                }
            }
        }
    }

    inner class ChatItemImage(val id: String, val chatMessage: ChatMessage, val user: User, private val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return if (user.uid == FirebaseManager.user!!.uid) {
                R.layout.chat_message_from_image
            } else {
                R.layout.chat_message_to_image
            }
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (layout == R.layout.chat_message_from_image) {
                if (chatMessage.id == FirebaseManager.latestMessageOtherUserSeen) {
                    viewHolder.itemView.messageSeenImage.visibility = View.VISIBLE
                } else {
                    viewHolder.itemView.messageSeenImage.visibility = View.GONE
                }

                if (chatMessage.text.isNotEmpty()) {
                    viewHolder.itemView.textMessageFromImage.text = chatMessage.text
                } else {
                    viewHolder.itemView.textMessageFromImage.height = 0
                }

                if (!sequential) {
                    Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageFromImage)
                } else {
                    viewHolder.itemView.imageFromImage.visibility = View.INVISIBLE
                }

                viewHolder.itemView.timestampMessageFromImage.text = chatMessage.timestamp
                Picasso.get().load(chatMessage.imageUrl).transform(RoundedCornersTransformation(20, 20)).into(viewHolder.itemView.imageMessageFromImage)

                viewHolder.itemView.imageMessageFromImage.setOnClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_image_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.view_image -> {
                                val builder = CustomTabsIntent.Builder()
                                val customTabsIntent = builder.build()
                                customTabsIntent.launchUrl(
                                    viewHolder.itemView.context,
                                    Uri.parse(chatMessage.imageUrl)
                                )
                            }
                        }
                        true
                    }
                    pop.show()
                }

                viewHolder.itemView.setOnLongClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.hide_message -> {
                                viewHolder.itemView.layoutParams.height = 0
                                val ref = FirebaseDatabase.getInstance()
                                    .getReference("/conversations/${FirebaseManager.conversationId}/${chatMessage.id}")
                                ref.child("hidden").setValue(true)
                                adapter.clear()
                                listenForMessages()
                            }
                        }
                        true
                    }
                    pop.show()
                    true
                }
                recyclerChatLog.scrollToPosition(adapter.itemCount - 1)
            } else if (layout == R.layout.chat_message_to_image) {
                if (chatMessage.text.isNotEmpty()) {
                    viewHolder.itemView.textMessageToImage.text = chatMessage.text
                } else {
                    viewHolder.itemView.textMessageToImage.height = 0
                }

                if (!sequential) {
                    Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageToImage)
                } else {
                    viewHolder.itemView.imageToImage.visibility = View.INVISIBLE
                }

                viewHolder.itemView.timestampMessageToImage.text = chatMessage.timestamp
                Picasso.get().load(chatMessage.imageUrl).transform(RoundedCornersTransformation(20, 20)).into(viewHolder.itemView.imageMessageToImage)

                viewHolder.itemView.imageToImage.setOnClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_image_tap_menu)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.view_profile -> {
                                val intent = Intent(viewHolder.itemView.context, ProfileActivity::class.java)
                                intent.putExtra(OTHER_USER_KEY, user)
                                viewHolder.itemView.context.startActivity(intent)
                            }
                            R.id.block_user -> {
                                if (FirebaseManager.blocklist != null) {
                                    FirebaseManager.blocklist!!.add(user.uid)
                                    val ref = FirebaseDatabase.getInstance().getReference("/users/${FirebaseManager.user!!.uid}/blocklist")
                                    ref.setValue(FirebaseManager.blocklist)
                                    startActivity(Intent(viewHolder.itemView.context, LatestMessagesActivity::class.java))
                                    finish()
                                }
                            }
                        }
                        true
                    }
                    pop.show()
                }

                viewHolder.itemView.imageMessageToImage.setOnClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_image_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.view_image -> {
                                val builder = CustomTabsIntent.Builder()
                                val customTabsIntent = builder.build()
                                customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(chatMessage.imageUrl))
                            }
                        }
                        true
                    }
                    pop.show()
                }

                viewHolder.itemView.setOnLongClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.hide_message -> {
                                viewHolder.itemView.layoutParams.height = 0
                                val ref = FirebaseDatabase.getInstance().getReference("/conversations/${FirebaseManager.conversationId}/$id")
                                ref.child("hidden").setValue(true)
                                adapter.clear()
                                listenForMessages()
                            }
                        }
                        true
                    }
                    pop.show()
                    true
                }
                recyclerChatLog.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    inner class ChatItemFile(val id: String, val chatMessage: ChatMessage, val user: User, private val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return if (user.uid == FirebaseManager.user!!.uid) {
                R.layout.chat_message_from_file
            } else {
                R.layout.chat_message_to_file
            }
        }

        @SuppressLint("SetTextI18n")
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (layout == R.layout.chat_message_from_file) {
                if (chatMessage.id == FirebaseManager.latestMessageOtherUserSeen) {
                    viewHolder.itemView.messageSeenFile.visibility = View.VISIBLE
                } else {
                    viewHolder.itemView.messageSeenFile.visibility = View.GONE
                }

                if (chatMessage.fileSize!! > 1000) {
                    viewHolder.itemView.fileSizeFromFile.text =
                        "${chatMessage.fileSize?.div(1000)}mB"
                } else {
                    viewHolder.itemView.fileSizeFromFile.text = "${chatMessage.fileSize}kB"
                }

                if (chatMessage.text.isNotEmpty()) {
                    viewHolder.itemView.textMessageFromFile.text = chatMessage.text
                } else {
                    viewHolder.itemView.textMessageFromFile.height = 0
                }

                if (!sequential) {
                    Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageFromFile)
                } else {
                    viewHolder.itemView.imageFromFile.visibility = View.INVISIBLE
                }

                viewHolder.itemView.fileTypeFromFile.text = chatMessage.fileType
                viewHolder.itemView.timestampMessageFromFile.text = chatMessage.timestamp

                viewHolder.itemView.imageMessageFromFile.setOnClickListener {
                    val builder = CustomTabsIntent.Builder()
                    val customTabsIntent = builder.build()
                    customTabsIntent.launchUrl(
                        viewHolder.itemView.context,
                        Uri.parse(chatMessage.fileUrl)
                    )
                }

                viewHolder.itemView.setOnLongClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.hide_message -> {
                                viewHolder.itemView.layoutParams.height = 0
                                val ref = FirebaseDatabase.getInstance()
                                    .getReference("/conversations/${FirebaseManager.conversationId}/${chatMessage.id}")
                                ref.child("hidden").setValue(true)
                                adapter.clear()
                                listenForMessages()
                            }
                        }
                        true
                    }
                    pop.show()
                    true
                }
            } else if (layout == R.layout.chat_message_to_file) {
                if (chatMessage.fileSize!! > 1000) {
                    viewHolder.itemView.fileSizeToFile.text = "${chatMessage.fileSize?.div(1000)}mB"
                } else {
                    viewHolder.itemView.fileSizeToFile.text = "${chatMessage.fileSize}kB"
                }

                if (chatMessage.text.isNotEmpty()) {
                    viewHolder.itemView.textMessageToFile.text = chatMessage.text
                } else {
                    viewHolder.itemView.textMessageToFile.height = 0
                }

                if (!sequential) {
                    Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageToFile)
                } else {
                    viewHolder.itemView.imageToFile.visibility = View.INVISIBLE
                }

                viewHolder.itemView.fileTypeToFile.text = chatMessage.fileType
                viewHolder.itemView.timestampMessageToFile.text = chatMessage.timestamp

                viewHolder.itemView.imageToFile.setOnClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_image_tap_menu)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.view_profile -> {
                                val intent =
                                    Intent(viewHolder.itemView.context, ProfileActivity::class.java)
                                intent.putExtra(OTHER_USER_KEY, user)
                                viewHolder.itemView.context.startActivity(intent)
                            }
                            R.id.block_user -> {
                                if (FirebaseManager.blocklist != null) {
                                    FirebaseManager.blocklist!!.add(user.uid)
                                    val ref = FirebaseDatabase.getInstance().getReference("/users/${FirebaseManager.user!!.uid}/blocklist")
                                    ref.setValue(FirebaseManager.blocklist)
                                    startActivity(Intent(viewHolder.itemView.context, LatestMessagesActivity::class.java))
                                    finish()
                                }
                            }
                        }
                        true
                    }
                    pop.show()
                }

                viewHolder.itemView.imageMessageToFile.setOnClickListener {
                    val builder = CustomTabsIntent.Builder()
                    val customTabsIntent = builder.build()
                    customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(chatMessage.fileUrl))
                }

                viewHolder.itemView.setOnLongClickListener {
                    val pop = PopupMenu(it.context, it)
                    pop.inflate(R.menu.chat_log_message_tap)
                    pop.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.hide_message -> {
                                viewHolder.itemView.layoutParams.height = 0
                                val ref = FirebaseDatabase.getInstance().getReference("/conversations/${FirebaseManager.conversationId}/$id")
                                ref.child("hidden").setValue(true)
                                adapter.clear()
                                listenForMessages()
                            }
                        }
                        true
                    }
                    pop.show()
                    true
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_log_options, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.unhide_messages -> {
                val ref = FirebaseDatabase.getInstance().getReference("/conversations/${FirebaseManager.conversationId}")
                ref.addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        var hideComplete = false
                        p0.children.forEach {
                            if (it.child("hidden").exists()) {
                                if (it.child("hidden").value == true)
                                    ref.child(it.key.toString()).child("hidden").removeValue()
                                hideComplete = true
                            }
                        }
                        if (hideComplete) {
                            adapter.clear()
                            listenForMessages()
                        }
                    }
                })
            }
            android.R.id.home -> {
                if (toUser == intent.getParcelableExtra(MyFirebaseMessagingService.NOT_USER_KEY)) {
                    startActivity(Intent(this, LatestMessagesActivity::class.java))
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.saveState(outState)
    }
}