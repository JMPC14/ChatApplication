package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_new_message.*
import kotlinx.android.synthetic.main.user_row_newmessage.view.*

class NewMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)

        recyclerNewMessage.layoutManager = LinearLayoutManager(this)

        supportActionBar?.title = "Select User"

        fetchContactsForMessage()
    }

    companion object {
        val USER_KEY = "USER_KEY"
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
            val intent = Intent(view.context, ChatLogActivity::class.java)
            intent.putExtra(USER_KEY, userItem.user)
            startActivity(intent)

            finish()
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