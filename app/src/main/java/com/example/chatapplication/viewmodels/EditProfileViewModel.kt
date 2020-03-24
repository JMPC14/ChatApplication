package com.example.chatapplication.viewmodels

import android.os.Bundle
import androidx.lifecycle.ViewModel

class EditProfileViewModel : ViewModel() {

    var usernameText: String? = null
    var emailText: String? = null
    var profileImageUrl: String? = null

    private val usernameTextName = "com.example.chatapplication.viewmodels.EditProfileViewModel.usernameText"
    private val emailTextName = "com.exmaple.chatapplication.EditProfileViewModel.emailText"
    private val profileImageUrlName = "com.example.chatapplication.viewmodels.EditProfileViewModel.profileImageUrl"

    fun saveState(outState: Bundle) {
        outState.putString(usernameTextName, usernameText)
        outState.putString(emailTextName, emailText)
        outState.putString(profileImageUrlName, profileImageUrl)
    }
}