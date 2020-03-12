package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_contacts.*
import kotlinx.android.synthetic.main.contact_row.view.*

class ContactsActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        fetchContactsForAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        supportActionBar?.title = "Contacts"

        recyclerContacts.layoutManager = LinearLayoutManager(this)

        fab_addContact.setOnClickListener {
            startActivity(Intent(this, NewContactActivity::class.java))
        }
    }

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
                    }
                })
        }
        recyclerContacts.adapter = adapter
    }

    inner class ContactItem(val contact: User) : Item<GroupieViewHolder>() {
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
}