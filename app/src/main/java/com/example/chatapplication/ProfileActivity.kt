package com.example.chatapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_profile.*

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        supportActionBar?.elevation = 0f

        val otherUser = intent.getParcelableExtra<User?>(ChatLogActivity.OTHER_USER_KEY)

        if (otherUser != null) {
            displayOtherUser(otherUser)
        }
        else {
            displayCurrentUser()
        }
    }

    private fun displayCurrentUser() {
        supportActionBar?.title = "Profile"

        editProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        Picasso.get().load(FirebaseManager.user!!.profileImageUrl).into(userImageProfile)
        usernameTextViewProfile.text = FirebaseManager.user!!.username
        emailTextViewProfile.text = FirebaseManager.user!!.email
    }

    private fun displayOtherUser(otherUser: User) {
        supportActionBar?.title = otherUser.username

        editProfile.isEnabled = false
        editProfile.visibility = View.GONE

        Picasso.get().load(otherUser.profileImageUrl).into(userImageProfile)
        usernameTextViewProfile.text = otherUser.username
        emailTextViewProfile.text = otherUser.email
    }
}
