package com.example.chatapplication

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel

class ChatLogViewModel : ViewModel() {

    var photoAttachmentUri: Uri? = null
    var fileAttachmentUri: Uri? = null

    private val photoAttachmentUriName = "com.example.chatapplication.ChatLogViewModel.photoAttachmentUri"
    private val fileAttachmentUriName = "com.example.chatapplication.ChatLogViewModel.fileAttachmentUri"

    fun saveState(outState: Bundle) {
        outState.putString(photoAttachmentUriName, photoAttachmentUri.toString())
        outState.putString(fileAttachmentUriName, fileAttachmentUri.toString())
    }

    fun restoreState(savedInstanceState: Bundle) {
        photoAttachmentUri = savedInstanceState.getString(photoAttachmentUriName)!!.toUri()
        fileAttachmentUri = savedInstanceState.getString(fileAttachmentUriName)!!.toUri()
    }
}