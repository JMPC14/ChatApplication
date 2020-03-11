package com.example.chatapplication

object FirebaseManager {
    var user: User? = null
    var otherUser: User? = null
    var contacts: MutableList<String>? = null
    var blocklist: MutableList<String>? = null
    var attachedImage: String? = null
    var attachedFile: String? = null
    var attachedFileSize: Double? = null
    var attachedFileType: String? = null
    var notificationTempMessage: String? = null
    var ignoreNotificationUid: String? = null
    var latestMessageSeen: String? = null
    var latestMessageOtherUserSeen: String? = null
}