package com.example.chatapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.constraintlayout.widget.Constraints
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
import java.io.File
import java.util.*

class ChatLogActivity : AppCompatActivity() {

    companion object {
        const val TAG = "ChatLog"
        const val OTHER_USER_KEY = "OTHER_USER_KEY"
    }

    val adapter = GroupAdapter<GroupieViewHolder>()
    var toUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_log)

        recyclerChatLog.adapter = adapter
        recyclerChatLog.layoutManager = LinearLayoutManager(this)

        toUser = intent.getParcelableExtra(NewMessageActivity.USER_KEY)
        FirebaseManager.otherUser = toUser

        supportActionBar?.title = toUser?.username
        supportActionBar?.elevation = 0.toFloat()

        enterMessageText.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sendMessageButton.isEnabled = enterMessageText.text.isNotEmpty() || FirebaseManager.attachedImage != null || FirebaseManager.attachedFile != null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                sendMessageButton.isEnabled = enterMessageText.text.isNotEmpty() || FirebaseManager.attachedImage != null || FirebaseManager.attachedFile != null
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sendMessageButton.isEnabled = enterMessageText.text.isNotEmpty() || FirebaseManager.attachedImage != null || FirebaseManager.attachedFile != null
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
            Log.d(TAG, "Attempt to send message")
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

    private var bitmap: Bitmap? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == Activity.RESULT_OK && data != null) {
            photoAttachmentUri = data.data
            bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoAttachmentUri)
            Picasso.get().load(photoAttachmentUri).into(imageAttachedImageView)
            imageAttachedLayout.visibility = View.VISIBLE
            sendMessageButton.isEnabled = true
        }

        if (requestCode == 1 && resultCode == Activity.RESULT_OK && data != null) {
            fileAttachmentUri = data.data
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
                    if (chatMessage.imageUrl == null && chatMessage.fileUrl == null) {
                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                            adapter.add(ChatFromItem(chatMessage.id, chatMessage.text, currentUser))
                        } else {
                            adapter.add(ChatToItem(p0.key!!, chatMessage.text, toUser!!))
                        }
                    }

                    else if (chatMessage.fileUrl != null) {
                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                            adapter.add(ChatFromItemFile(chatMessage.id, chatMessage.text, currentUser, chatMessage.fileUrl!!, chatMessage.fileSize!!, chatMessage.fileType!!))
                        } else {
                            adapter.add(ChatToItemFile(p0.key!!, chatMessage.text, toUser!!, chatMessage.fileUrl!!, chatMessage.fileSize!!, chatMessage.fileType!!))
                        }
                    }

                    else {
                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid) {
                            adapter.add(ChatFromItemImage(chatMessage.id, chatMessage.text, currentUser, chatMessage.imageUrl!!))
                        } else {
                            adapter.add(ChatToItemImage(p0.key!!, chatMessage.text, toUser!!, chatMessage.imageUrl!!))
                        }
                    }
                    recyclerChatLog.scrollToPosition(adapter.itemCount - 1)
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
                if (p0.child("hidden").exists()) {
                    if (FirebaseManager.hiddenPosition != null) {
                        adapter.removeGroupAtAdapterPosition(FirebaseManager.hiddenPosition!!)
                        FirebaseManager.hiddenPosition = null
                    }
                } else if (p0.key != "typing") {
                    listenForMessages()
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
        val text = enterMessageText.text.toString()

        val fromId = FirebaseAuth.getInstance().uid ?: return

        val toId = intent.getParcelableExtra<User>(NewMessageActivity.USER_KEY).uid

        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId").push()

        val toRef =
            FirebaseDatabase.getInstance().getReference("/user-messages/$toId/$fromId").push()

        val chatMessage: ChatMessage?

        val time = System.currentTimeMillis() / 1000

        chatMessage = if (FirebaseManager.attachedImage != null) {
            ChatMessage(ref.key!!, text, fromId, toId, time, FirebaseManager.attachedImage!!)
        } else if (FirebaseManager.attachedFile != null) {
            ChatMessage(ref.key!!, text, fromId, toId, time, FirebaseManager.attachedFile!!, FirebaseManager.attachedFileSize!!, FirebaseManager.attachedFileType!!)
        } else {
            ChatMessage(ref.key!!, text, fromId, toId, time)
        }

            ref.setValue(chatMessage)
                .addOnSuccessListener {
                    Log.d(TAG, "Saved chat message: ${ref.key}")
                    enterMessageText.text.clear()
                    recyclerChatLog.scrollToPosition(adapter.itemCount - 1)
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

    class ChatFromItem(val id: String, val text: String, val user: User) : Item<GroupieViewHolder>() {

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageFrom.text = text
            val target = viewHolder.itemView.imageMessageFrom
            Picasso.get().load(user.profileImageUrl).into(target)

            viewHolder.itemView.textMessageFrom.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
                            ref.child("hidden").setValue(true)
                            FirebaseManager.hiddenPosition = position
                        }
                    }
                    true
                }
                pop.show()
                true
            }
        }

        override fun getLayout(): Int {
            return R.layout.chat_message_from
        }
    }

    class ChatToItem(val id: String, val text: String, val user: User) : Item<GroupieViewHolder>() {

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageTo.text = text
            val target = viewHolder.itemView.imageMessageTo
            Picasso.get().load(user.profileImageUrl).into(target)

            viewHolder.itemView.imageMessageTo.setOnClickListener {
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
                        }
                    }
                    true
                }
                pop.show()
            }

            viewHolder.itemView.textMessageTo.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
                            ref.child("hidden").setValue(true)
                            FirebaseManager.hiddenPosition = position
                        }
                    }
                    true
                }
                pop.show()
                true
            }
        }

        override fun getLayout(): Int {
            return R.layout.chat_message_to
        }
    }


    class ChatFromItemImage(val id: String, val text: String, val user: User, val imageUrl: String) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return R.layout.chat_message_from_image
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageFromImage.text = text
            val imageMessage = viewHolder.itemView.imageMessageFromImage
            val target = viewHolder.itemView.imageFromImage
            Picasso.get().load(user.profileImageUrl).into(target)
            Picasso.get().load(imageUrl).transform(RoundedCornersTransformation(20, 20)).into(imageMessage)

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
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
                            ref.child("hidden").setValue(true)
                            FirebaseManager.hiddenPosition = position
                        }
                    }
                    true
                }
                pop.show()
                true
            }
        }
    }

    class ChatToItemImage(val id: String, val text: String, val user: User, val imageUrl: String) : Item<GroupieViewHolder>() {

        override fun getLayout(): Int {
            return R.layout.chat_message_to_image
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.textMessageToImage.text = text
            val imageMessage = viewHolder.itemView.imageMessageToImage
            val target = viewHolder.itemView.imageToImage
            Picasso.get().load(user.profileImageUrl).into(target)
            Picasso.get().load(imageUrl).transform(RoundedCornersTransformation(20, 20)).into(imageMessage)

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
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
                            ref.child("hidden").setValue(true)
                            FirebaseManager.hiddenPosition = position
                        }
                    }
                    true
                }
                pop.show()
                true
            }
        }
    }

    class ChatFromItemFile(val id: String, val text: String, val user: User, val fileUrl: String, val fileSize: Double, val fileType: String) : Item<GroupieViewHolder>() {
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

            if (text.isEmpty()) {
                viewHolder.itemView.textMessageFromFile.height = 0
            }

            viewHolder.itemView.imageMessageFromFile.setOnClickListener {
                val builder = CustomTabsIntent.Builder()

                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(fileUrl))
            }

            viewHolder.itemView.textMessageFromFile.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
                            ref.child("hidden").setValue(true)
                            FirebaseManager.hiddenPosition = position
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

    class ChatToItemFile(val id: String, val text: String, val user: User, val fileUrl: String, val fileSize: Double, val fileType: String) : Item<GroupieViewHolder>() {
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

            if (text.isEmpty()) {
                viewHolder.itemView.textMessageToFile.height = 0
            }

            viewHolder.itemView.imageMessageToFile.setOnClickListener {
                val builder = CustomTabsIntent.Builder()

                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(viewHolder.itemView.context, Uri.parse(fileUrl))
            }

            viewHolder.itemView.textMessageToFile.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.chat_log_message_tap)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.hide_message -> {
                            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseManager.user!!.uid}/${FirebaseManager.otherUser!!.uid}/$id")
                            ref.child("hidden").setValue(true)
                            FirebaseManager.hiddenPosition = position
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
                        TODO("Not yet implemented")
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        p0.children.forEach {
                            if (it.child("hidden").exists()) {
                                if (it.child("hidden").value == true)
                                    ref.child(it.key.toString()).child("hidden").removeValue()
                            }
                        }
                    }
                })
            }
        }
        return super.onOptionsItemSelected(item)
    }
}