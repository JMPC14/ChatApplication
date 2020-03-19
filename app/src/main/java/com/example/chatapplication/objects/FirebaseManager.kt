package com.example.chatapplication.objects

import com.example.chatapplication.models.User

/** Object containing various consistent variables to used for coordination throughout the application. **/
object FirebaseManager {
    var user: User? = null /** Current user object. **/
    var otherUser: User? = null /** Other user object obtained when a chatlog is opened with a user. **/
    var contacts: MutableList<String>? = null /** List of UID's of a user's contacts. **/
    var blocklist: MutableList<String>? = null /** List of UID's of a user's blocklist. **/
    var attachedImage: String? = null /** Uri for the currently attached image to be uploaded when message sent. **/
    var attachedFile: String? = null /** Uri for the currently attached file to be uploaded when message sent. **/
    var attachedFileSize: Double? = null /** File size of attached file to be displayed in message. **/
    var attachedFileType: String? = null /** File type of attached file to be displayed in message. **/
    var notificationTempMessage: String? = null /** Temporary message text used for when a user replies within a push notification. **/
    var ignoreNotificationUid: String? = null /** UID of user currently in conversation for push notifications to ignore. **/
    var latestMessageSeen: String? = null /** Latest message from the other user that the current user has seen. **/
    var latestMessageOtherUserSeen: String? = null /** Latest message from the current user that the other user has seen. **/
    var conversationId: String? = null /** Conversation ID under which messages are stored. **/
    var token: String? = null /** Current device token for FirebaseMessaging push notifications. **/
    var otherUserToken: String? = null /** Device token of the other user to send push notifications. **/
    var messageKey: String? = null /** Conversation ID & message ID to be sent in push notification. **/
    var foreground: Boolean? = null /** If true then a particular activity is in the foreground. **/
    var profileChanged: Boolean? = null /** If profile has been changed then Navigation Drawer is refreshed. **/
}