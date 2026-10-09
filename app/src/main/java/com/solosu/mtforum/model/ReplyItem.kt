package com.solosu.mtforum.model








class ReplyItem {
    var pid: String? = null             
    var floorNumber: Int = 0            
    var floorLabel: String? = null      

    var author: String? = null          
    var authorUid: String? = null       
    var authorLevel: String? = null     
    var avatarUrl: String? = null       
    var gender: String? = null          

    var isOP: Boolean = false           
    var contentHtml: String? = null     
    var contentText: String? = null     
    var quotedContentHtml: String? = null 
    var quotedContentText: String? = null 

    var time: String? = null            
    var location: String? = null        
    var imageUrls: ArrayList<String> = ArrayList() 

    
    var quotedPid: String? = null       
    var quotedUid: String? = null       
    var quotedAuthorName: String? = null 

    
    var subReplies: MutableList<ReplyItem> = ArrayList() 
    var isSubReply: Boolean = false                      
    var isSubRepliesExpanded: Boolean = true             
    var inReplyToName: String? = null                    
    var orderIndex: Int = -1                             
}
