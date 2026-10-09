package com.solosu.mtforum.model












class PostDetail {
    var tid: String? = null
    var title: String? = null
    var forumName: String? = null
    var forumFid: String? = null

    
    var author: String? = null           
    var authorUid: String? = null        
    var authorLevel: String? = null      
    var avatarUrl: String? = null        
    var gender: String? = null           
    var publishTime: String? = null      
    var location: String? = null         
    var isFollowed: Boolean = false      

    
    var contentHtml: String? = null      

    @get:JvmName("isHasHiddenContent")
    @set:JvmName("setHasHiddenContent")
    var hasHiddenContent: Boolean = false 

    var hiddenContentHtml: String? = null 

    
    var replyCount: Int = 0              
    var likeCount: Int = 0               
    var favoriteCount: Int = 0           
    var rewardCount: Int = 0             
    var goodReviewCount: Int = 0         
    var rewardCoins: Int = 0             
    var rewardDetailUrl: String? = null  
    var rewardUserAvatars: MutableList<String>? = null    
    var goodReviewUserAvatars: MutableList<String>? = null 
    var likeUserAvatars: MutableList<String>? = null   
    var likeUserUids: MutableList<String>? = null     
    var likeUserNames: MutableList<String>? = null    

    var isLiked: Boolean = false         

    @get:JvmName("isLikedStateKnown")
    @set:JvmName("setLikedStateKnown")
    var likedStateKnown: Boolean = false 

    var isFavorited: Boolean = false     

    @get:JvmName("isFavoritedStateKnown")
    @set:JvmName("setFavoritedStateKnown")
    var favoritedStateKnown: Boolean = false 

    
    var replies: MutableList<ReplyItem>? = null 

    
    var formhash: String? = null         
    var noticeauthor: String? = null     
    var postPid: String? = null          
    var currentPage: Int = 0             
    var totalPages: Int = 0              
    var nextPageUrl: String? = null      

    
    var imageUrls: MutableList<String>? = null  
}
