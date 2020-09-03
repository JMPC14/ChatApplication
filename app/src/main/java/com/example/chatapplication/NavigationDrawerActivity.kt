package com.example.chatapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.get
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatapplication.models.ChatMessage
import com.example.chatapplication.models.User
import com.example.chatapplication.objects.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_chat_log.*
import kotlinx.android.synthetic.main.activity_navigation_drawer.*
import kotlinx.android.synthetic.main.app_bar_navigation_drawer.*
import kotlinx.android.synthetic.main.blocked_user_row.view.*
import kotlinx.android.synthetic.main.contact_row.view.*
import kotlinx.android.synthetic.main.content_navigation_drawer.*
import kotlinx.android.synthetic.main.latest_message_row.view.*
import kotlinx.android.synthetic.main.nav_header_navigation_drawer.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NavigationDrawerActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        var LAT_USER_KEY = "LAT_USER_KEY"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration

    private val adapter = GroupAdapter<GroupieViewHolder>()

    /** Sets foreground activity to true so that push notifications don't show.
     * Clears adapter to refresh blocklist and adapter if user has just blocked someone. **/
    override fun onResume() {
        super.onResume()
        FirebaseManager.foreground = true
        adapter.clear()
        fetchContacts()
        fetchBlocklist()
        nav_view.menu[1].isChecked = true
        if (supportActionBar?.title == "Contacts") {
            nav_view.menu[2].isChecked = true
            fetchContactsForAdapter()
        }
        if (FirebaseManager.profileChanged == true) {
            fetchCurrentUser()
            FirebaseManager.profileChanged = false
        }
    }

    /** Sets foreground activity to false so that push notifications show. **/
    override fun onPause() {
        FirebaseManager.foreground = false
        super.onPause()
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_drawer)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_latest_messages, R.id.nav_contacts, R.id.nav_blocklist
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)

        FirebaseMessaging.getInstance().isAutoInitEnabled = true

        recyclerNavigation.layoutManager = LinearLayoutManager(this)

        fetchCurrentUser()
        fetchContacts()

        window.statusBarColor = (Color.parseColor("#4CAF50"))

        GlobalScope.launch {
            navView.menu.getItem(1).isChecked = true
            onNavigationItemSelected(navView.menu.findItem(R.id.nav_latest_messages))
        }
    }

    private fun listenForOnlineIndicators() {
        FirebaseManager.onlineUsers = mutableListOf()
        val ref = FirebaseDatabase.getInstance().getReference("/online-users")
        ref.addChildEventListener(object: ChildEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                recyclerNavigation.adapter!!.notifyDataSetChanged()
                if (p0.value == true) {
                    FirebaseManager.onlineUsers!!.add(p0.key!!)
                } else if (p0.value == false && FirebaseManager.onlineUsers!!.contains(p0.key!!)) {
                    FirebaseManager.onlineUsers!!.remove(p0.key!!)
                }
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                recyclerNavigation.adapter!!.notifyDataSetChanged()
                if (p0.value == true) {
                    FirebaseManager.onlineUsers!!.add(p0.key!!)
                } else if (p0.value == false && FirebaseManager.onlineUsers!!.contains(p0.key!!)) {
                    FirebaseManager.onlineUsers!!.remove(p0.key!!)
                }
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }
        })
    }

    private fun fetchCurrentUser() {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            val intent = Intent(this, LauncherActivity::class.java)
            intent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK).or(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener {
                FirebaseManager.token = it.result?.token
                FirebaseDatabase.getInstance().getReference("/users/$uid").child("token").setValue(
                    FirebaseManager.token)
            }
            val ref = FirebaseDatabase.getInstance().getReference("/users/$uid")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    FirebaseManager.user = p0.getValue(User::class.java)
                    Picasso.get().load(FirebaseManager.user?.profileImageUrl).into(navDrawerImageView)
                    navDrawerUsername.text = FirebaseManager.user?.username
                    navDrawerEmail.text = FirebaseManager.user?.email
                    FirebaseDatabase.getInstance().getReference("/online-users/${FirebaseManager.user?.uid}").setValue(true)
                    listenForLatestMessages()
                }
            })
        }
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

    private fun displayLatestMessages() {
        recyclerNavigation.adapter = adapter
        recyclerNavigation.addItemDecoration(
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
            intent.putExtra(LAT_USER_KEY, row.chatPartnerUser)
            startActivity(intent)
        }

        supportActionBar?.title = "Latest Messages"

        fab_navView.setOnClickListener {
            startActivity(Intent(this, NewMessageActivity::class.java))
        }

        listenForOnlineIndicators()
    }

    private fun displayContacts() {
        supportActionBar?.title = "Contacts"
        fetchContactsForAdapter()

        fab_navView.setOnClickListener {
            startActivity(Intent(this, NewContactActivity::class.java))
        }
    }

    private fun displayBlocklist() {
        supportActionBar?.title = "Blocked Users"
        fetchBlocklistForAdapter()

        fab_navView.isEnabled = false
        fab_navView.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.navigation_drawer, menu)
        return true
    }

    @SuppressLint("RtlHardcoded")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sign_out -> {
                FirebaseDatabase.getInstance().getReference("/online-users/${FirebaseManager.user?.uid}").setValue(false)
                adapter.clear()
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
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        emptyContactsBlocklist.visibility = View.GONE
        fab_navView.isEnabled = true
        fab_navView.visibility = View.VISIBLE
        when (item.itemId) {
            R.id.nav_latest_messages -> {
                displayLatestMessages()
            }
            R.id.nav_contacts -> {
                displayContacts()
            }
            R.id.nav_blocklist -> {
                displayBlocklist()
            }
            R.id.nav_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }
        drawer_layout.closeDrawer(Gravity.LEFT, true)
        return true
    }

    val latestMessageMap = HashMap<String, ChatMessage>()

    private fun refreshRecyclerViewMessages() {
        adapter.clear()
        val sortedMap = latestMessageMap.toList().sortedByDescending { it.second.time }.toMap()
        sortedMap.values.forEach { adapter.add(LatestMessageRow(it)) }
    }

    private fun listenForLatestMessages() {
        val fromId = FirebaseAuth.getInstance().uid
        val ref = FirebaseDatabase.getInstance().getReference("/latest-messages/$fromId")
        ref.addChildEventListener(object: ChildEventListener{
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                val chatMessage = p0.getValue(ChatMessage::class.java) ?: return

                if (FirebaseManager.blocklist != null ) {
                    if (FirebaseManager.blocklist!!.contains(chatMessage.fromId) || FirebaseManager.blocklist!!.contains(chatMessage.toId)) { return }
                }

                latestMessageMap[p0.key!!] = chatMessage
                refreshRecyclerViewMessages()
            }

            override fun onChildChanged(p0: DataSnapshot, p1: String?) {
                val chatMessage = p0.getValue(ChatMessage::class.java) ?: return

                if (FirebaseManager.blocklist != null ) {
                    if (FirebaseManager.blocklist!!.contains(chatMessage.fromId) || FirebaseManager.blocklist!!.contains(chatMessage.toId)) { return }
                }

                latestMessageMap[p0.key!!] = chatMessage
                refreshRecyclerViewMessages()
            }

            override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            }

            override fun onChildRemoved(p0: DataSnapshot) {
            }
        })
    }

    class LatestMessageRow(val chatMessage : ChatMessage) : Item<GroupieViewHolder>() {
        var chatPartnerUser: User? = null

        override fun getLayout(): Int {
            return R.layout.latest_message_row
        }

        @SuppressLint("SetTextI18n")
        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            val chatPartnerId: String?
            if (chatMessage.fromId == FirebaseManager.user!!.uid) {
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

                    Picasso.get().load(chatPartnerUser?.profileImageUrl).into(viewHolder.itemView.userImageLatestMessageRow)
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

    @SuppressLint("SetTextI18n")
    private fun fetchContactsForAdapter() {
        val adapter = GroupAdapter<GroupieViewHolder>()
        val list: MutableList<User>? = mutableListOf()
        FirebaseManager.contacts!!.sortBy { it }
        FirebaseManager.contacts?.forEach {
            FirebaseDatabase.getInstance().getReference("/users/$it")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        adapter.clear()
                        list!!.add(p0.getValue(User::class.java)!!)
                        list.sortBy { it.username}
                        list.forEach { adapter.add(ContactItem(it)) }
                        if (adapter.itemCount == 0) {
                            emptyContactsBlocklist.visibility = View.VISIBLE
                        }
                    }
                })
        }
        if (FirebaseManager.contacts!!.isEmpty()) {
            emptyContactsBlocklist.text = "You have no contacts!"
            emptyContactsBlocklist.visibility = View.VISIBLE
        } else {
            emptyContactsBlocklist.visibility = View.GONE
        }
        recyclerNavigation.adapter = adapter
    }

    inner class ContactItem(private val contact: User) : Item<GroupieViewHolder>() {
        override fun getLayout(): Int {
            return R.layout.contact_row
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.usernameContactRow.text = contact.username
            Picasso.get().load(contact.profileImageUrl)
                .into(viewHolder.itemView.userImageContactRow)

            viewHolder.itemView.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.contact_menu)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.remove_contact -> {
                            FirebaseManager.contacts?.remove(contact.uid)
                            FirebaseDatabase.getInstance()
                                .getReference("/users/${FirebaseManager.user?.uid}/contacts")
                                .setValue(FirebaseManager.contacts)
                            fetchContactsForAdapter()
                        }
                    }
                    true
                }
                pop.show()
                true
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchBlocklistForAdapter() {
        val adapter = GroupAdapter<GroupieViewHolder>()
        FirebaseManager.blocklist?.forEach {
            FirebaseDatabase.getInstance().getReference("/users/$it")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        adapter.add(BlocklistItem(p0.getValue(User::class.java)!!))
                        if (adapter.itemCount == 0) {
                            emptyContactsBlocklist.visibility = View.VISIBLE
                        }
                    }
                })
        }
        if (FirebaseManager.blocklist!!.isEmpty()) {
            emptyContactsBlocklist.text = "You have no blocked users!"
            emptyContactsBlocklist.visibility = View.VISIBLE
        } else {
            emptyContactsBlocklist.visibility = View.GONE
        }
        recyclerNavigation.adapter = adapter
    }

    inner class BlocklistItem(private val blockedUser: User) : Item<GroupieViewHolder>() {
        override fun getLayout(): Int {
            return R.layout.blocked_user_row
        }

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.usernameBlockedRow.text = blockedUser.username
            Picasso.get().load(blockedUser.profileImageUrl)
                .into(viewHolder.itemView.userImageBlockedRow)

            viewHolder.itemView.setOnLongClickListener {
                val pop = PopupMenu(it.context, it)
                pop.inflate(R.menu.blocklist_menu)
                pop.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.unblock_user -> {
                            FirebaseManager.blocklist?.remove(blockedUser.uid)
                            FirebaseDatabase.getInstance()
                                .getReference("/users/${FirebaseManager.user?.uid}/blocklist")
                                .setValue(FirebaseManager.blocklist)
                            fetchBlocklistForAdapter()
                            fetchBlocklist()
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
