package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_latest_messages.*
import kotlinx.android.synthetic.main.latest_message_row.view.*

class LatestMessagesActivity : AppCompatActivity() {

    companion object {
        var currentUser: User? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_latest_messages)

//        val uid = FirebaseAuth.getInstance().uid
//        val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
//        ref.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onCancelled(p0: DatabaseError) {
//            }
//
//            override fun onDataChange(p0: DataSnapshot) {
//                val user = p0.getValue(User::class.java)
//                Picasso.get().load(user?.profileImageUrl).into(userProfileImage)
//            }
//        })

        supportActionBar?.title = "Latest Messages"

        recyclerLatestMessages.layoutManager = LinearLayoutManager(this)
        recyclerLatestMessages.adapter = adapter
        recyclerLatestMessages.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        adapter.setOnItemClickListener { item, view ->
            Log.d("LatestMessages", "123")
            val intent = Intent(this, ChatLogActivity::class.java)
            val row = item as LatestMessageRow
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

        listenForLatestMessages()

        listenForOnlineIndicators()

        fetchContacts()
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

//    private fun fetchContactSize() {
//        var contacts: List<String> = listOf()
//
//        val uid = FirebaseAuth.getInstance().uid
//        FirebaseDatabase.getInstance().getReference("/users/$uid/contacts").addListenerForSingleValueEvent(object: ValueEventListener {
//            override fun onCancelled(p0: DatabaseError) {
//            }
//
//            override fun onDataChange(p0: DataSnapshot) {
//                p0.children.forEach {
//                    Log.d("Contacts", it.toString())
//                    contacts = contacts.plus(it.value.toString())
//                }
//            }
//        })
//    }

    val latestMessageMap = HashMap<String, ChatLogActivity.ChatMessage>()

    private fun refreshRecyclerViewMessages() {
        adapter.clear()
        latestMessageMap.values.forEach { adapter.add(LatestMessageRow(it)) }
    }

    private fun listenForLatestMessages() {
        val fromId = FirebaseAuth.getInstance().uid
        val ref = FirebaseDatabase.getInstance().getReference("/latest-messages/$fromId")
        ref.addChildEventListener(object: ChildEventListener{
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                val chatMessage = p0.getValue(ChatLogActivity.ChatMessage::class.java) ?: return

                latestMessageMap[p0.key!!] = chatMessage
                refreshRecyclerViewMessages()
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                val chatMessage = p0.getValue(ChatLogActivity.ChatMessage::class.java) ?: return

                latestMessageMap[p0.key!!] = chatMessage
                refreshRecyclerViewMessages()
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
            var chatPartnerId: String?
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
            }
            else {
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
                    usernameLatestMessages.text = "${currentUser?.username}"
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
                intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK).or(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            R.id.user_contacts -> {
                startActivity(Intent(this, ContactsActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.latest_messages_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
}
