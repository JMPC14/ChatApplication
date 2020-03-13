package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_new_message.*
import kotlinx.android.synthetic.main.user_row_newmessage.view.*
import java.util.*

class NewMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)

        recyclerNewMessage.layoutManager = LinearLayoutManager(this)

        supportActionBar?.title = "Select User"

        fetchContactsForMessage()
    }

    companion object {
        const val USER_KEY = "USER_KEY"
    }

    private fun fetchContactsForMessage() {
        val adapter = GroupAdapter<GroupieViewHolder>()
        FirebaseManager.contacts?.forEach {
            FirebaseDatabase.getInstance().getReference("/users/$it").addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    adapter.add(UserItem(p0.getValue(User::class.java)!!))
                }
            })
        }

        adapter.setOnItemClickListener { item, view ->

            val userItem = item as UserItem
            val cid = UUID.randomUUID().toString()
            val ref = FirebaseDatabase.getInstance().getReference("/user-messages/${FirebaseAuth.getInstance().uid}/${userItem.user.uid}")
            ref.addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    p0.children.forEach {
                        if (it.key == "cid") {
                            val intent = Intent(view.context, ChatLogActivity::class.java)
                            intent.putExtra(USER_KEY, userItem.user)
                            startActivity(intent)

                            finish()
                            return
                        }
                    }
                    ref.child("cid").setValue(cid)
                    val intent = Intent(view.context, ChatLogActivity::class.java)
                    intent.putExtra(USER_KEY, userItem.user)
                    startActivity(intent)

                    finish()
                }
            })

            val toRef = FirebaseDatabase.getInstance().getReference("/user-messages/${userItem.user.uid}/${FirebaseAuth.getInstance().uid}")
            toRef.addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                }

                override fun onDataChange(p0: DataSnapshot) {
                    p0.children.forEach {
                        if (it.key == "cid") {
                            val intent = Intent(view.context, ChatLogActivity::class.java)
                            intent.putExtra(USER_KEY, userItem.user)
                            startActivity(intent)

                            finish()
                            return
                        }
                    }
                    toRef.child("cid").setValue(cid)
                    val intent = Intent(view.context, ChatLogActivity::class.java)
                    intent.putExtra(USER_KEY, userItem.user)
                    startActivity(intent)

                    finish()
                }
            })
        }
        recyclerNewMessage.adapter = adapter
    }
}

class UserItem(val user: User): Item<GroupieViewHolder>() {

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.itemView.usernameNewMessage.text = user.username
        Picasso.get().load(user.profileImageUrl).into(viewHolder.itemView.imageNewMessage)
    }

    override fun getLayout(): Int {
        return R.layout.user_row_newmessage
    }
}