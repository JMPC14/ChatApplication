package com.example.chatapplication

import android.os.Bundle
import androidx.lifecycle.ViewModel

class EditProfileViewModel : ViewModel() {

    var usernameText: String? = null
    var emailText: String? = null
    var profileImageUrl: String? = null

    val usernameTextName = "com.example.chatapplication.EditProfileViewModel.usernameText"
    val emailTextName = "com.exmaple.chatapplication.EditProfileViewModel.emailText"
    val profileImageUrlName = "com.example.chatapplication.EditProfileViewModel.profileImageUrl"

    fun saveState(outState: Bundle) {
        outState.putString(usernameTextName, usernameText)
        outState.putString(emailTextName, emailText)
        outState.putString(profileImageUrlName, profileImageUrl)
    }

    fun restoreState(savedInstanceState: Bundle) {
        usernameText = savedInstanceState.getString(usernameTextName)!!
        emailText = savedInstanceState.getString(emailTextName)!!
        profileImageUrl = savedInstanceState.getString(profileImageUrlName)!!
    }
}