package com.example.chatapplication.viewmodels

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel

class ChatLogViewModel : ViewModel() {

    var photoAttachmentUri: Uri? = null
    var fileAttachmentUri: Uri? = null

    private val photoAttachmentUriName = "com.example.chatapplication.viewmodels.ChatLogViewModel.photoAttachmentUri"
    private val fileAttachmentUriName = "com.example.chatapplication.viewmodels.ChatLogViewModel.fileAttachmentUri"

    fun saveState(outState: Bundle) {
        outState.putString(photoAttachmentUriName, photoAttachmentUri.toString())
        outState.putString(fileAttachmentUriName, fileAttachmentUri.toString())
    }
}