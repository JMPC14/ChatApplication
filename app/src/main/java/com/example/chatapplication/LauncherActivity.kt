package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_launcher.*

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        supportActionBar?.hide()

        loginLauncher.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        createAccountLauncher.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
