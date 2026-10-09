package com.solosu.mtforum.model









class UserProfile {
    var uid: String? = null            
    var username: String? = null       
    var avatarUrl: String? = null      
    var level: String? = null          
    var groupName: String? = null      
    var credits: Int = 0               
    var gold: Int = 0                  
    var threads: Int = 0               
    var posts: Int = 0                 
    var friends: Int = 0               
    var followers: Int = 0             
    var following: Int = 0             
    var views: Int = 0                 
    var regDate: String? = null        
    var lastVisit: String? = null      
    var signature: String? = null      
    var onlineTime: String? = null     
    var isOnline: Boolean = false      
    var gender: String? = null         

    @get:JvmName("isFollowed")
    @set:JvmName("setFollowed")
    var followed: Boolean = false      

    @get:JvmName("isFollowStateKnown")
    @set:JvmName("setFollowStateKnown")
    var followStateKnown: Boolean = false 
}
