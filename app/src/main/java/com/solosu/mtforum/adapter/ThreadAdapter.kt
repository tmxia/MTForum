package com.solosu.mtforum.adapter

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.solosu.mtforum.util.ToastUtil as Toast
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.solosu.mtforum.R
import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.session.FollowStateManager
import com.solosu.mtforum.ui.detail.ImagePreviewActivity
import com.solosu.mtforum.ui.space.UserProfileActivity
import com.solosu.mtforum.ui.widget.FrostedGlassDrawable
import java.util.ArrayList




class ThreadAdapter(private val context: Context) : RecyclerView.Adapter<ThreadAdapter.ViewHolder>() {

    private var threadList: MutableList<Thread> = ArrayList()
    private var headerView: View? = null
    private val VIEW_TYPE_HEADER = -1
    private var listener: OnItemClickListener? = null
    private var userClickListener: OnUserClickListener? = null

    @Volatile
    private var serverFollowingUids: MutableSet<String>? = null

    @Volatile
    private var serverFollowingLoaded: Boolean = false
    private var followStateLoading: Boolean = false
    private val followStateLock = Any()

    interface OnItemClickListener {
        fun onItemClick(thread: Thread?, position: Int)
    }

    interface OnUserClickListener {
        fun onUserClick(thread: Thread?)
    }

    private var lastAnimatedPosition = -1

    fun setThreadList(list: MutableList<Thread>?) {
        
        val source = if (list != null) list else ArrayList()
        val blackUids = com.solosu.mtforum.session.BlacklistManager.uidSet(context)
        val blackNames = com.solosu.mtforum.session.BlacklistManager.nameSet(context)
        this.threadList = if (blackUids.isEmpty() && blackNames.isEmpty()) {
            source
        } else {
            val filtered = ArrayList<Thread>(source.size)
            for (t in source) {
                val hitByUid = !t.authorUid.isNullOrEmpty() && blackUids.contains(t.authorUid)
                val hitByName = !t.author.isNullOrEmpty() && blackNames.contains(t.author)
                if (!hitByUid && !hitByName) filtered.add(t)
            }
            filtered
        }
        serverFollowingUids = null
        serverFollowingLoaded = false
        followStateLoading = false
        lastAnimatedPosition = -1
        notifyDataSetChanged()
    }

    
    fun notifyItemChangedByTid(tid: String?) {
        if (tid == null) return
        for (i in threadList.indices) {
            val t = threadList[i]
            if (t != null && tid == t.tid) {
                notifyItemChanged(i + (if (headerView != null) 1 else 0))
                return
            }
        }
    }

    fun addThreads(list: MutableList<Thread>?) {
        if (list != null) {
            val start = threadList.size
            threadList.addAll(list)
            notifyItemRangeInserted(start, list.size)
        }
    }

    fun setHeaderView(view: View?) {
        this.headerView = view
        notifyItemInserted(0)
    }

    fun getHeaderView(): View? {
        return headerView
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    fun setOnUserClickListener(listener: OnUserClickListener?) {
        this.userClickListener = listener
    }

    fun updateItem(position: Int, thread: Thread) {
        val list = threadList ?: return
        if (position < 0 || position >= list.size) return
        list[position] = thread
        notifyItemChanged(position)
    }

    fun getItem(position: Int): Thread? {
        val offset = if (headerView != null) position - 1 else position
        if (offset >= 0 && offset < threadList.size) return threadList[offset]
        return null
    }

    fun removeItem(position: Int) {
        val dataPos = if (headerView != null) position - 1 else position
        if (dataPos >= 0 && dataPos < threadList.size) {
            threadList.removeAt(dataPos)
            notifyItemRemoved(position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (headerView != null && position == 0) return VIEW_TYPE_HEADER
        return super.getItemViewType(position)
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): ViewHolder {
        if (viewType == VIEW_TYPE_HEADER) {
            return ViewHolder(headerView!!)
        }
        val view = LayoutInflater.from(context).inflate(R.layout.item_thread, parent, false)
        
        return ViewHolder(view)
    }

    override fun onViewAttachedToWindow(@NonNull holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder.cardView != null && holder.cardView!!.getBackground() is FrostedGlassDrawable) {
            holder.cardView!!.getBackground().setVisible(true, false)
        }
    }

    override fun onViewDetachedFromWindow(@NonNull holder: ViewHolder) {
        if (holder.cardView != null && holder.cardView!!.getBackground() is FrostedGlassDrawable) {
            holder.cardView!!.getBackground().setVisible(false, false)
        }
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(@NonNull holder: ViewHolder) {
        if (holder.cardView != null && holder.cardView!!.getBackground() is FrostedGlassDrawable) {
            holder.cardView!!.getBackground().setVisible(false, false)
        }
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(@NonNull holder: ViewHolder, position: Int) {
        if (headerView != null && position == 0) return
        val dataPos = if (headerView != null) position - 1 else position
        val thread = threadList[dataPos]
        holder.tvTitle!!.setText(thread.title)
        holder.tvAuthor!!.setText(thread.author)
        holder.tvTime!!.setText(thread.publishTime)
        val forumName = thread.forumName
        if (forumName != null && !forumName.isEmpty()) {
            holder.tvForum!!.setVisibility(View.VISIBLE)
            holder.tvForum!!.setText(forumName)
        } else {
            holder.tvForum!!.setVisibility(View.GONE)
        }

        
        val summary = cleanThreadSummary(thread.summary)
        if (!summary.isEmpty()) {
            holder.tvSummary!!.setVisibility(View.VISIBLE)
            holder.tvSummary!!.setText(summary)
        } else {
            holder.tvSummary!!.setVisibility(View.GONE)
        }

        
        val level = thread.authorLevel
        if (level != null && !level.isEmpty()) {
            holder.tvLevel!!.setVisibility(View.VISIBLE)
            holder.tvLevel!!.setText(level)
        } else {
            holder.tvLevel!!.setVisibility(View.GONE)
        }

        
        holder.tvViews!!.setText(formatCount(thread.views))
        holder.tvReplies!!.setText(formatCount(thread.replies))
        holder.tvLikes!!.setText(formatCount(thread.likes))

    
         if (!TextUtils.isEmpty(thread.authorUid)) {
             val serverSet = serverFollowingUids
             if (serverSet != null) {
                 thread.followed = serverSet.contains(thread.authorUid!!)
             } else {
                 thread.followed = FollowStateManager.resolve(context, thread.authorUid, thread.followed)
                 loadServerFollowingIfNeeded()
             }
         }
         holder.btnFollow!!.setText(if (thread.followed) "已关注" else "关注")
        holder.btnFollow!!.setOnClickListener { v ->
            if (!FollowStateManager.isLoggedIn(context)) {
                if (context is android.app.Activity) {
                    com.solosu.mtforum.ui.login.LoginBottomSheet.show(
                            context, null)
                } else {
                    Toast.makeText(context, "请先登录后再关注", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            val uid = thread.authorUid
            if (TextUtils.isEmpty(uid)) {
                Toast.makeText(context, "无法获取用户ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val targetState = !thread.followed
            holder.btnFollow!!.setEnabled(false)
        java.lang.Thread({
            val success = FollowStateManager.syncFollow(context, uid, targetState)
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            main.post {
                holder.btnFollow!!.setEnabled(true)
                if (success) {
                    thread.followed = targetState
                    holder.btnFollow!!.setText(if (targetState) "已关注" else "关注")
                    Toast.makeText(context, if (targetState)
                            R.string.action_follow_success
                            else R.string.action_unfollow_success, Toast.LENGTH_SHORT).show()
                    val current = serverFollowingUids
                    if (current != null) {
                        val updated = java.util.HashSet(current)
                        if (targetState) updated.add(uid) else updated.remove(uid)
                        serverFollowingUids = updated
                    }
                } else {
                    Toast.makeText(context, "关注操作失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }).start()
        }

        
        if (thread.isSticky) {
            holder.tvSticky!!.setVisibility(View.VISIBLE)
        } else {
            holder.tvSticky!!.setVisibility(View.GONE)
        }

        
        val avatarUrl = thread.avatarUrl
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_account)
                    .error(R.drawable.ic_account)
                    .circleCrop()
                    .into(holder.ivAvatar!!)
        } else {
            holder.ivAvatar!!.setImageResource(R.drawable.ic_account)
        }
        
        bindThreadImages(holder, thread)


        holder.ivAvatar!!.setOnClickListener { v ->
            if (userClickListener != null && !TextUtils.isEmpty(thread.authorUid)) {
                userClickListener!!.onUserClick(thread)
            }
        }
        holder.tvAuthor!!.setOnClickListener { v ->
            if (userClickListener != null && !TextUtils.isEmpty(thread.authorUid)) {
                userClickListener!!.onUserClick(thread)
            }
        }

        holder.itemView.setOnClickListener { v ->
            v.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(80)
                .withEndAction {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                        .start()
                    if (listener != null) {
                        listener!!.onItemClick(thread, position)
                    }
                }
                .start()
        }

        
        if (position > lastAnimatedPosition) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 24f * context.resources.displayMetrics.density
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
            lastAnimatedPosition = position
        }

        
        holder.itemView.setOnLongClickListener { v ->
            val uid = thread.authorUid
            val name = thread.author
            if (TextUtils.isEmpty(uid)) {
                Toast.makeText(context, "无法拉黑：缺少作者 UID", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }
            val dlg = androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("拉黑作者")
                    .setMessage("将「" + name + "」加入个人小黑屋？\n其发布的帖子和回帖都会隐藏。")
                    .setPositiveButton("拉黑") { d, w ->
                        
                        
                        com.solosu.mtforum.session.BlacklistManager.addPending(context, uid, name)
                        removeThreadsByUid(uid)
                        Toast.makeText(context, "已拉黑「" + name + "」", Toast.LENGTH_SHORT).show()

                        java.lang.Thread {
                            try {
                                val ok = com.solosu.mtforum.session.BlacklistSyncer.addToServer(name)
                                if (ok) {
                                    com.solosu.mtforum.session.BlacklistManager.markSynced(context, uid)
                                } else {
                                    com.solosu.mtforum.session.BlacklistManager.incrementRetry(context, uid)
                                }
                            } catch (_: Exception) {
                                com.solosu.mtforum.session.BlacklistManager.incrementRetry(context, uid)
                            }
                        }.start()
                    }
                    .setNegativeButton("取消", null)
                    .create()
                    dlg.show()
                    com.solosu.mtforum.ui.widget.DialogHelper.applyToAlertDialog(dlg, context)
            true
        }
    }

    
    fun removeThreadsByUid(uid: String?) {
        if (uid == null || uid.isEmpty()) return
        val keep = ArrayList<Thread>()
        for (t in threadList) {
            if (t.authorUid == null || uid != t.authorUid) keep.add(t)
        }
        if (keep.size != threadList.size) {
            threadList.clear()
            threadList.addAll(keep)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        return threadList.size + (if (headerView != null) 1 else 0)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var cardView: View?
        var ivAvatar: ImageView?
        var ivThumbnail: ImageView?
        var llThreadImages: ViewGroup?
        var btnFollow: TextView?
        var tvTitle: TextView?
        var tvAuthor: TextView?
        var tvLevel: TextView?
        var tvTime: TextView?
        var tvForum: TextView?
        var tvSummary: TextView?
        var tvViews: TextView?
        var tvReplies: TextView?
        var tvLikes: TextView?
        var tvSticky: TextView?

        init {
            cardView = itemView.findViewById<View>(R.id.thread_card)
            ivAvatar = itemView.findViewById<ImageView>(R.id.iv_avatar)
            ivThumbnail = itemView.findViewById<ImageView>(R.id.iv_thumbnail)
            llThreadImages = itemView.findViewById(R.id.ll_thread_images)
            btnFollow = itemView.findViewById<TextView>(R.id.btn_thread_follow)
            tvTitle = itemView.findViewById<TextView>(R.id.tv_title)
            tvAuthor = itemView.findViewById<TextView>(R.id.tv_author)
            tvLevel = itemView.findViewById<TextView>(R.id.tv_level)
            tvTime = itemView.findViewById<TextView>(R.id.tv_time)
            tvForum = itemView.findViewById<TextView>(R.id.tv_forum)
            tvSummary = itemView.findViewById<TextView>(R.id.tv_summary)
            tvViews = itemView.findViewById<TextView>(R.id.tv_views)
            tvReplies = itemView.findViewById<TextView>(R.id.tv_replies)
            tvLikes = itemView.findViewById<TextView>(R.id.tv_likes)
            tvSticky = itemView.findViewById<TextView>(R.id.tv_sticky)
        }
    }

    private fun openUserProfile(thread: Thread?) {
        if (thread == null || TextUtils.isEmpty(thread.authorUid)) return
        if (userClickListener != null) {
            userClickListener!!.onUserClick(thread)
            return
        }
        val intent = Intent(context, UserProfileActivity::class.java)
        intent.putExtra("uid", thread.authorUid)
        intent.putExtra("username", thread.author)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun loadServerFollowingIfNeeded() {
        if (serverFollowingUids != null) return
        if (serverFollowingLoaded || followStateLoading
                || !FollowStateManager.isLoggedIn(context)) return
        synchronized(followStateLock) {
            if (serverFollowingUids != null || serverFollowingLoaded || followStateLoading) return
            followStateLoading = true
        }
        java.lang.Thread({
            val result = FollowStateManager.queryServerFollowingUids(context)
            serverFollowingUids = result
            serverFollowingLoaded = true
            followStateLoading = false
            android.os.Handler(android.os.Looper.getMainLooper()).post(this::notifyDataSetChanged)
        }).start()
    }

    private fun dp(value: Int): Int {
        return Math.round(value * context.getResources().getDisplayMetrics().density)
    }

    private fun bindThreadImages(holder: ViewHolder, thread: Thread) {
        holder.ivThumbnail?.visibility = View.GONE
        holder.llThreadImages?.visibility = View.GONE
        holder.llThreadImages?.removeAllViews()
        holder.ivThumbnail?.let { Glide.with(context).clear(it) }

        val rawList = thread.imageUrls
        val list = if (rawList != null && rawList.isNotEmpty()) {
            rawList
        } else if (!TextUtils.isEmpty(thread.thumbnailUrl)) {
            listOf(thread.thumbnailUrl!!)
        } else {
            emptyList()
        }

        if (list.isEmpty()) return

        val totalCount = list.size

        if (totalCount == 1) {
            
            val singleIv = holder.ivThumbnail ?: return
            singleIv.visibility = View.VISIBLE
            val lp = singleIv.layoutParams
            lp.height = dp(155)
            singleIv.layoutParams = lp
            singleIv.clipToOutline = true
            singleIv.setBackgroundResource(R.drawable.bg_thread_single_img)
            singleIv.foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_thread_image_border)

            Glide.with(context)
                .load(list[0])
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .transform(CenterCrop(), RoundedCorners(dp(8)))
                .into(singleIv)

            singleIv.setOnClickListener {
                openImagePreview(list, 0)
            }
            return
        }

        
        val container = holder.llThreadImages as? LinearLayout ?: return
        container.visibility = View.VISIBLE

        when (totalCount) {
            2 -> {
                
                val row = createHorizontalRow(dp(130))
                val gap = dp(5)
                for (i in 0 until 2) {
                    val iv = createGridImageView(dp(6))
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    if (i == 0) lp.rightMargin = gap / 2 else lp.leftMargin = gap / 2
                    iv.layoutParams = lp
                    loadIntoImageView(iv, list[i], dp(6))
                    val pos = i
                    iv.setOnClickListener { openImagePreview(list, pos) }
                    row.addView(iv)
                }
                container.addView(row)
            }
            3 -> {
                
                val row = createHorizontalRow(dp(100))
                val gap = dp(4)
                for (i in 0 until 3) {
                    val iv = createGridImageView(dp(6))
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    if (i > 0) lp.leftMargin = gap / 2
                    if (i < 2) lp.rightMargin = gap / 2
                    iv.layoutParams = lp
                    loadIntoImageView(iv, list[i], dp(6))
                    val pos = i
                    iv.setOnClickListener { openImagePreview(list, pos) }
                    row.addView(iv)
                }
                container.addView(row)
            }
            4 -> {
                
                val gap = dp(4)
                for (rowIdx in 0 until 2) {
                    val row = createHorizontalRow(dp(98))
                    if (rowIdx > 0) {
                        val rowLp = row.layoutParams as LinearLayout.LayoutParams
                        rowLp.topMargin = gap
                        row.layoutParams = rowLp
                    }
                    for (colIdx in 0 until 2) {
                        val i = rowIdx * 2 + colIdx
                        val iv = createGridImageView(dp(6))
                        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        if (colIdx == 0) lp.rightMargin = gap / 2 else lp.leftMargin = gap / 2
                        iv.layoutParams = lp
                        loadIntoImageView(iv, list[i], dp(6))
                        val pos = i
                        iv.setOnClickListener { openImagePreview(list, pos) }
                        row.addView(iv)
                    }
                    container.addView(row)
                }
            }
            else -> {
                
                val row = createHorizontalRow(dp(115))
                val gap = dp(5)
                for (i in 0 until 3) {
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    if (i > 0) lp.leftMargin = gap / 2
                    if (i < 2) lp.rightMargin = gap / 2

                    val pos = i
                    if (i == 2) {
                        
                        val fl = FrameLayout(context)
                        fl.layoutParams = lp
                        val iv = createGridImageView(dp(8))
                        iv.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        loadIntoImageView(iv, list[i], dp(8))
                        fl.addView(iv)

                        
                        val badge = TextView(context)
                        badge.text = "${totalCount}图"
                        badge.textSize = 10f
                        badge.setTextColor(0xFFFFFFFF.toInt())
                        badge.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                        badge.setBackgroundResource(R.drawable.bg_image_count_badge)
                        val badgeLp = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END
                        )
                        badgeLp.topMargin = dp(4)
                        badgeLp.rightMargin = dp(4)
                        badge.layoutParams = badgeLp
                        fl.addView(badge)

                        fl.setOnClickListener { openImagePreview(list, pos) }
                        row.addView(fl)
                    } else {
                        val iv = createGridImageView(dp(8))
                        iv.layoutParams = lp
                        loadIntoImageView(iv, list[i], dp(8))
                        iv.setOnClickListener { openImagePreview(list, pos) }
                        row.addView(iv)
                    }
                }
                container.addView(row)
            }
        }
    }

    private fun createHorizontalRow(heightPx: Int): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            heightPx
        )
        return row
    }

    private fun createGridImageView(radiusDp: Int): ImageView {
        val iv = ImageView(context)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundResource(R.drawable.bg_thread_grid_img)
        iv.foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_thread_grid_border)
        iv.clipToOutline = true
        return iv
    }

    private fun loadIntoImageView(iv: ImageView, url: String, radiusDp: Int) {
        Glide.with(context)
            .load(url)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_error)
            .transform(CenterCrop(), RoundedCorners(radiusDp))
            .into(iv)
    }

    private fun openImagePreview(urls: List<String>, index: Int) {
        val intent = Intent(context, ImagePreviewActivity::class.java)
        intent.putStringArrayListExtra("image_urls", ArrayList(urls))
        intent.putExtra("image_index", index)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun cleanThreadSummary(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        
        var s = raw.replace(Regex("(?i)https?://[a-zA-Z0-9./_?=&%-]+"), "")
        s = s.replace(Regex("(?i)\\[/?(?:quote|hide|attach|url|img|b|color)[^\\]]*\\]"), "")
        s = s.replace(Regex("(?i)本帖隐藏的内容.*?($|\n)"), "")
        s = s.replace(Regex("(?i)查看本帖隐藏内容请回复"), "")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    private fun formatCount(count: Int): String {
        if (count >= 10000) {
            return String.format("%.1fw", count / 10000.0)
        } else if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0)
        }
        return count.toString()
    }
}
