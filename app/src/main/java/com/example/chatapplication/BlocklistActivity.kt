package com.example.chatapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import kotlinx.android.synthetic.main.activity_blocklist.*
import kotlinx.android.synthetic.main.blocked_user_row.view.*

class BlocklistActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        fetchBlocklistForAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocklist)

        supportActionBar?.title = "Blocked Users"

        recyclerBlocklist.layoutManager = LinearLayoutManager(this)
    }

    private fun fetchBlocklistForAdapter() {
        val adapter = GroupAdapter<GroupieViewHolder>()
        FirebaseManager.blocklist?.forEach {
            FirebaseDatabase.getInstance().getReference("/users/$it")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        adapter.add(BlocklistItem(p0.getValue(User::class.java)!!))
                    }
                })
        }
        recyclerBlocklist.adapter = adapter

        if (adapter.itemCount == 0) {
            emptyBlockListText.visibility = View.VISIBLE
        }
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