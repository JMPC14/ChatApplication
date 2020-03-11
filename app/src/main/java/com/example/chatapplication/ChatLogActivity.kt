package com.example.chatapplication

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.RemoteInput
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
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
import java.time.LocalDateTime
import java.util.*

class ChatLogActivity : AppCompatActivity() {

    private val viewModel by lazy { ViewModelProvider(this)[ChatLogViewModel::class.java] }

    companion object {
        const val TAG = "ChatLog"
        const val OTHER_USER_KEY = "OTHER_USER_KEY"
    }

    val adapter = GroupAdapter<GroupieViewHolder>()
    var toUser: User? = null

    override fun onResume() {
        super.onResume()
        FirebaseManager.ignoreNotification = true
    }

    override fun onPause() {
        super.onPause()
        FirebaseManager.ignoreNotification = false
    }

    override fun onStop() {
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${toUser!!.uid}/${FirebaseManager.user!!.uid}")
        ref.child("typing").setValue(false)
        FirebaseManager.attachedImage = null
        FirebaseManager.attachedFile = null
        FirebaseManager.attachedFileSize = null
        FirebaseManager.attachedFileType = null
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_log)

        val remoteReply = RemoteInput.getResultsFromIntent(intent)

        if (remoteReply != null) {
            val message = remoteReply.getCharSequence(LatestMessagesActivity.NOTIFICATION_REPLY_KEY) as String
            FirebaseManager.notificationTempMessage = message
            toUser = intent.getParcelableExtra(LatestMessagesActivity.NOT_USER_KEY)
            performSendMessage()
            FirebaseManager.notificationTempMessage = null

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(LatestMessagesActivity.NOTIFICATION_ID)
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

        toUser = intent.getParcelableExtra(LatestMessagesActivity.NOT_USER_KEY)

        if (toUser == null) {
            toUser = intent.getParcelableExtra(NewMessageActivity.USER_KEY)
        }
        FirebaseManager.otherUser = toUser

        supportActionBar?.title = toUser?.username
        supportActionBar?.elevation = 0.toFloat()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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

        listenForMessages()

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

    private fun listenForMessages() {
        val fromId = FirebaseAuth.getInstance().uid
        val toId = toUser?.uid
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId")
        ref.addChildEventListener(object : ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                if (p0.key == "typing") {
                    if (p0.value == true) {
                        userTypingIndicator.textSize = 14.toFloat()
                        userTypingIndicator.visibility = View.VISIBLE
                        userTypingIndicator.text = "${toUser!!.username} is typing..."
                    }
                    else if (p0.value != true) {
                        userTypingIndicator.textSize = 0.toFloat()
                        userTypingIndicator.visibility = View.INVISIBLE
                    }
                    return
                }

                if (p0.child("hidden").value == true) {
                    return
                }

                val chatMessage = p0.getValue(ChatMessage::class.java)
                val currentUser = LatestMessagesActivity.currentUser!!
                if (chatMessage != null) {

                    var sequentialFrom = false
                    var sequentialTo = false

                    if (adapter.itemCount != 0) {
                        val test = adapter.getItem(adapter.itemCount - 1)
                        if (test.layout == R.layout.chat_message_from
                            || test.layout == R.layout.chat_message_from_image
                            || test.layout == R.layout.chat_message_from_file
                            || test.layout == R.layout.chat_message_from_sequential) {
                            sequentialFrom = true
                            sequentialTo = false
                        } else if (test.layout == R.layout.chat_message_to
                            || test.layout == R.layout.chat_message_to_image
                            || test.layout == R.layout.chat_message_to_file
                            || test.layout == R.layout.chat_message_to_sequential) {
                            sequentialTo = true
                            sequentialFrom = false
                        }
                    }

                    if (chatMessage.imageUrl == null && chatMessage.fileUrl == null) {
                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                            adapter.add(ChatFromItem(chatMessage.id, chatMessage.text, currentUser, sequentialFrom))
                        } else {
                            adapter.add(ChatToItem(p0.key!!, chatMessage.text, toUser!!, sequentialTo))
                        }
                    }

                    else if (chatMessage.fileUrl != null) {
                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                            adapter.add(ChatFromItemFile(chatMessage.id, chatMessage.text, currentUser, chatMessage.fileUrl!!, chatMessage.fileSize!!, chatMessage.fileType!!, sequentialFrom))
                        } else {
                            adapter.add(ChatToItemFile(p0.key!!, chatMessage.text, toUser!!, chatMessage.fileUrl!!, chatMessage.fileSize!!, chatMessage.fileType!!, sequentialTo))
                        }
                    }

                    else if (chatMessage.imageUrl != null) {
                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                            adapter.add(ChatFromItemImage(chatMessage.id, chatMessage.text, currentUser, chatMessage.imageUrl!!, sequentialFrom))
                        } else {
                            adapter.add(ChatToItemImage(p0.key!!, chatMessage.text, toUser!!, chatMessage.imageUrl!!, sequentialTo))
                        }
                    }
                    recyclerChatLog.scrollToPosition(adapter.itemCount - 1)
                    recyclerChatLog.adapter = adapter
                }
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                if (p0.key!! == "typing") {
                    if (p0.value == true) {
                        userTypingIndicator.textSize = 14.toFloat()
                        userTypingIndicator.visibility = View.VISIBLE
                        userTypingIndicator.text = "${toUser!!.username} is typing..."
                    }
                    else if (p0.value != true) {
                        userTypingIndicator.textSize = 0.toFloat()
                        userTypingIndicator.visibility = View.INVISIBLE
                    }
                }
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }
        })
    }

    class ChatMessage(
        val id: String,
        val text: String,
        val fromId: String,
        val toId: String,
        val timestamp: Long
    ) {
        constructor(): this("", "", "", "", -1)

        var imageUrl: String? = null
        var fileUrl: String? = null
        var fileSize: Double? = null
        var fileType: String? = null

        constructor(id: String, text: String, fromId: String, toId: String, timestamp: Long, imageUrl: String) : this(id, text, fromId, toId, timestamp) {
            this.imageUrl = imageUrl
        }

        constructor(id: String, text: String, fromId: String, toId: String, timestamp: Long, fileUrl: String, fileSize: Double, fileType: String) : this(id, text, fromId, toId, timestamp) {
            this.fileUrl = fileUrl
            this.fileSize = fileSize
            this.fileType = fileType
        }
    }

    private fun performSendMessage() {
        val text = if (FirebaseManager.notificationTempMessage != null) {
            FirebaseManager.notificationTempMessage
        } else {
            enterMessageText.text.toString()
        }

        val fromId = FirebaseAuth.getInstance().uid ?: return
        val toId = toUser!!.uid
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId").push()
        val toRef = FirebaseDatabase.getInstance().getReference("/user-messages/$toId/$fromId").push()
        val chatMessage: ChatMessage?
        val timestamp = System.currentTimeMillis() / 1000
        val year = LocalDateTime.now().year
        val month = LocalDateTime.now().month
        val date = LocalDateTime.now().dayOfMonth
        val hour = LocalDateTime.now().hour
        val minute = LocalDateTime.now().minute

        if (text != null) {

            chatMessage = if (FirebaseManager.attachedImage != null) {
                ChatMessage(
                    ref.key!!,
                    text,
                    fromId,
                    toId,
                    timestamp,
                    FirebaseManager.attachedImage!!
                )
            } else if (FirebaseManager.attachedFile != null) {
                ChatMessage(
                    ref.key!!,
                    text,
                    fromId,
                    toId,
                    timestamp,
                    FirebaseManager.attachedFile!!,
                    FirebaseManager.attachedFileSize!!,
                    FirebaseManager.attachedFileType!!
                )
            } else {
                ChatMessage(ref.key!!, text, fromId, toId, timestamp)
            }

            ref.setValue(chatMessage)
                .addOnSuccessListener {
                    Log.d(TAG, "Saved chat message: ${ref.key}")
                    enterMessageText.text.clear()
                    recyclerChatLog.scrollToPosition(adapter.itemCount)
                }

            toRef.setValue(chatMessage)

            val latestMessageRef =
                FirebaseDatabase.getInstance().getReference("/latest-messages/$fromId/$toId")
            latestMessageRef.setValue(chatMessage)

            val latestMessageToRef =
                FirebaseDatabase.getInstance().getReference("/latest-messages/$toId/$fromId")
            latestMessageToRef.setValue(chatMessage)

            FirebaseManager.attachedImage = null
            FirebaseManager.attachedFile = null
            FirebaseManager.attachedFileSize = null
            imageAttachedLayout.visibility = View.INVISIBLE
            fileAttachedLayout.visibility = View.INVISIBLE
            sendMessageButton.isEnabled = false
        }
    }

    inner class ChatFromItem(val id: String, val text: String, val user: User, val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageFrom.text = text
            if (!sequential) {
                Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageMessageFrom)
            }

            viewHolder.itemView.textMessageFrom.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            viewHolder.itemView.layoutParams.height = 0
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
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

        override fun getLayout(): Int {
            return if (sequential) {
                R.layout.chat_message_from_sequential
            } else {
                R.layout.chat_message_from
            }
        }
    }

    inner class ChatToItem(val id: String, val text: String, val user: User, val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageTo.text = text
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
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
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

        override fun getLayout(): Int {
            return if (sequential) {
                R.layout.chat_message_to_sequential
            } else {
                R.layout.chat_message_to
            }
        }
    }


    inner class ChatFromItemImage(val id: String, val text: String, val user: User, val imageUrl: String, val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return R.layout.chat_message_from_image
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageFromImage.text = text
            val imageMessage = viewHolder.itemView.imageMessageFromImage
            val target = viewHolder.itemView.imageFromImage
            Picasso.get().load(user.profileImageUrl).into(target)
            Picasso.get().load(imageUrl).transform(RoundedCornersTransformation(20, 20)).into(imageMessage)

            if (sequential) {
                viewHolder.itemView.imageFromImage.visibility = View.INVISIBLE
            }

            if (text.isEmpty()) {
                viewHolder.itemView.textMessageFromImage.height = 0
            }

            viewHolder.itemView.imageMessageFromImage.setOnClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_image_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.view_image -> {
                            val builder = CustomTabsIntent.Builder()
                            val customTabsIntent = builder.build()
                            customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(imageUrl))
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
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
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

    inner class ChatToItemImage(val id: String, val text: String, val user: User, val imageUrl: String, val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return R.layout.chat_message_to_image
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageToImage.text = text
            val imageMessage = viewHolder.itemView.imageMessageToImage
            val target = viewHolder.itemView.imageToImage
            Picasso.get().load(user.profileImageUrl).into(target)
            Picasso.get().load(imageUrl).transform(RoundedCornersTransformation(20, 20)).into(imageMessage)

            if (sequential) {
                viewHolder.itemView.imageToImage.visibility = View.INVISIBLE
            }

            if (text.isEmpty()) {
                viewHolder.itemView.textMessageToImage.height = 0
            }

            viewHolder.itemView.imageMessageToImage.setOnClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_image_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.view_image -> {
                            val builder = CustomTabsIntent.Builder()
                            val customTabsIntent = builder.build()
                            customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(imageUrl))
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
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
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

    inner class ChatFromItemFile(val id: String, val text: String, val user: User, val fileUrl: String, val fileSize: Double, val fileType: String, val sequential: Boolean) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return R.layout.chat_message_from_file
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (fileSize > 1000) {
                viewHolder.itemView.fileSizeFromFile.text = "${fileSize/1000}mB"
            } else {
                viewHolder.itemView.fileSizeFromFile.text = "${fileSize}kB"
            }

            viewHolder.itemView.fileTypeFromFile.text = fileType
            viewHolder.itemView.textMessageFromFile.text = text
            Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageFromFile)

            if (sequential) {
                viewHolder.itemView.imageFromFile.visibility = View.INVISIBLE
            }

            if (text.isEmpty()) {
                viewHolder.itemView.textMessageFromFile.height = 0
            }

            viewHolder.itemView.imageMessageFromFile.setOnClickListener {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(fileUrl))
            }

            viewHolder.itemView.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            viewHolder.itemView.layoutParams.height = 0
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
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

//            viewHolder.itemView.imageMessageFromFile.setOnClickListener {
//                val downloadManager = DownloadManager.Request(Uri.parse(fileUrl))
//                downloadManager.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
//            }
        }

    }

    inner class ChatToItemFile(val id: String, val text: String, val user: User, val fileUrl: String, val fileSize: Double, val fileType: String, val sequential: Boolean) : Item<GroupieViewHolder>() {
        override fun getLayout(): Int {
            return R.layout.chat_message_to_file
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            if (fileSize > 1000) {
                viewHolder.itemView.fileSizeToFile.text = "${fileSize/1000}mB"
            } else {
                viewHolder.itemView.fileSizeToFile.text = "${fileSize}kB"
            }
            viewHolder.itemView.fileTypeToFile.text = fileType
            viewHolder.itemView.textMessageToFile.text = text
            Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageToFile)

            if (sequential) {
                viewHolder.itemView.imageToFile.visibility = View.INVISIBLE
            }

            if (text.isEmpty()) {
                viewHolder.itemView.textMessageToFile.height = 0
            }

            viewHolder.itemView.imageMessageToFile.setOnClickListener {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(fileUrl))
            }

            viewHolder.itemView.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            viewHolder.itemView.layoutParams.height = 0
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_log_options, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.unhide_messages -> {
                val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}")
                ref.addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        p0.children.forEach {
                            if (it.child("hidden").exists()) {
                                if (it.child("hidden").value == true)
                                    ref.child(it.key.toString()).child("hidden").removeValue()
                            }
                        }
                        adapter.clear()
                        listenForMessages()
                    }
                })
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.saveState(outState)
    }
}