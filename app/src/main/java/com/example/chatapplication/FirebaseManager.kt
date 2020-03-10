package com.example.chatapplication

object FirebaseManager {
    var user: User? = null
    var otherUser: User? = null
    var contacts: MutableList<String>? = null
    var attachedImage: String? = null
    var attachedFile: String? = null
    var attachedFileSize: Double? = null
    var attachedFileType: String? = null
    var hiddenPosition: Int? = null
    var notificationTempMessage: String? = null
    var ignoreNotification: Boolean? = null
    var latestMessageSeen: String? = null
}