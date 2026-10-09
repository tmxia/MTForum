package com.solosu.mtforum.model







class ChatMessage {
    var id: String? = null
    var author: String? = null
    var authorUid: String? = null
    var avatarUrl: String? = null
    var content: String? = null
    var time: String? = null
    var date: String? = null

    @get:JvmName("isOutgoing")
    @set:JvmName("setOutgoing")
    var outgoing: Boolean = false
}
