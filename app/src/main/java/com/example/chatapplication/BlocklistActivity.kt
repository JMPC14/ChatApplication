package com.example.chatapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_blocklist.*

class BlocklistActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocklist)

        supportActionBar?.title = "Blocked Users"

        recyclerBlocklist.layoutManager = LinearLayoutManager(this)
    }
}
