package com.solosu.mtforum.model




class ForumCategory {

    var name: String? = null            
    var forums: MutableList<Forum>? = null 

    constructor()

    constructor(name: String?, forums: MutableList<Forum>?) {
        this.name = name
        this.forums = forums
    }

    


    class Forum {

        var fid: String? = null          
        var name: String? = null         
        var description: String? = null  
        var todayPosts: Int = 0          
        var totalPosts: Int = 0          
        var totalThreads: Int = 0        
        var iconUrl: String? = null      

        constructor()

        constructor(fid: String?, name: String?) {
            this.fid = fid
            this.name = name
        }
    }
}
