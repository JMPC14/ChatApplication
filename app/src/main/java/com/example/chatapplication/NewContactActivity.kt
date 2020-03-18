package com.example.chatapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_new_contact.*
import kotlinx.android.synthetic.main.contact_row.view.*

class NewContactActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_contact)

        supportActionBar?.title = "Select User to Add"

        recyclerNewContact.layoutManager = LinearLayoutManager(this)

        fetchUsers()
    }

    private fun fetchUsers() {
        val userRef = FirebaseDatabase.getInstance().getReference("/users")
        userRef.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
            }

            override fun onDataChange(p0: DataSnapshot) {
                val adapter = GroupAdapter<GroupieViewHolder>()

                p0.children.forEach {
                    val user: User? = it.getValue(
                        User::class.java)
                    if (user != null) {
                        val uid: String = user.uid
                        if (user.uid != FirebaseAuth.getInstance().uid && !FirebaseManager.contacts!!.contains(uid)) {
                            adapter.add(NewContactItem(user))
                        }
                    }

                    adapter.setOnItemClickListener { item, view ->
                        val newContactItem = item as NewContactItem
                        val contactRef = FirebaseDatabase.getInstance().getReference("/users/${FirebaseManager.user?.uid}/contacts")
                        contactRef.addListenerForSingleValueEvent(object: ValueEventListener {
                            override fun onCancelled(p0: DatabaseError) {
                            }

                            override fun onDataChange(p0: DataSnapshot) {
                                FirebaseManager.contacts?.add(newContactItem.user.uid)
                                contactRef.setValue(FirebaseManager.contacts)
                                    .addOnSuccessListener {
                                        finish()
                                    }
                            }
                        })
                    }
                }
                recyclerNewContact.adapter = adapter
            }
        })
    }

    inner class NewContactItem(val user: User): Item<GroupieViewHolder>() {

        override fun bind(viewHolder: GroupieViewHolder, position: Int) {
            viewHolder.itemView.usernameContactRow.text = user.username
            Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.userImageContactRow)
        }

        override fun getLayout(): Int {
            return R.layout.contact_row
        }
    }
}