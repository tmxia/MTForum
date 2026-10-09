package com.solosu.mtforum.model








class Thread {
    var tid: String? = null          
    var title: String? = null        
    var author: String? = null       
    var authorUid: String? = null    
    var authorLevel: String? = null  
    var avatarUrl: String? = null    
    var forumName: String? = null    
    var forumFid: String? = null     
    var summary: String? = null      
    var publishTime: String? = null  
    var views: Int = 0               
    var replies: Int = 0             
    var likes: Int = 0               
    var favorites: Int = 0           

    @get:JvmName("isHasImage")
    @set:JvmName("setHasImage")
    var hasImage: Boolean = false    

    var isSticky: Boolean = false    

    @get:JvmName("isHasHiddenContent")
    @set:JvmName("setHasHiddenContent")
    var hasHiddenContent: Boolean = false 

    var thumbnailUrl: String? = null 
    var imageUrls: MutableList<String> = ArrayList() 

    @get:JvmName("isFollowed")
    @set:JvmName("setFollowed")
    var followed: Boolean = false    

    var favid: String? = null        
}
