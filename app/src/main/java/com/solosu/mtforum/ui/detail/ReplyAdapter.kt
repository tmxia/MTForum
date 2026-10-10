package com.solosu.mtforum.ui.detail

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.solosu.mtforum.R
import com.solosu.mtforum.model.ReplyItem
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.ui.space.UserProfileActivity
import com.solosu.mtforum.util.BBCodeUtil
import com.solosu.mtforum.util.NavigationHelper
import com.solosu.mtforum.util.UrlDrawable
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern





class ReplyAdapter(rawReplies: List<ReplyItem>?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    
    interface HeaderProvider {
        fun onCreateHeaderView(parent: ViewGroup): View
        fun onBindHeaderView(view: View)
    }

    private var headerProvider: HeaderProvider? = null

    data class DisplayRow(
        val item: ReplyItem,
        val foldedCount: Int = 0,
        val isExpanded: Boolean = false,
        val groupKey: String = "",
        
        val groupLabel: String? = null,
        
        val identity: String = "r:" + (item.pid ?: System.identityHashCode(item).toString())
    )

    private var rawReplyList: List<ReplyItem> = rawReplies ?: ArrayList()

    
    
    var isFoldExpanded: Boolean = false
        private set

    
    var foldedCount: Int = 0
        private set

    
    var onFoldStateChanged: ((count: Int, isExpanded: Boolean) -> Unit)? = null

    
    private var attachedRecycler: RecyclerView? = null
    private var displayList: MutableList<DisplayRow> = ArrayList()

    private var replyClickListener: OnReplyClickListener? = null
    private var userClickListener: OnUserClickListener? = null
    private var replyLongClickListener: OnReplyLongClickListener? = null

    init {
        organizeReplies()
        rebuildDisplayList()
    }

    private fun organizeReplies() {
        val all = rawReplyList
        if (all.isEmpty()) return

        
        
        val pidMap = HashMap<String, ReplyItem>()
        val nameMap = HashMap<String, MutableList<ReplyItem>>()
        for ((index, item) in all.withIndex()) {
            item.subReplies.clear()
            item.isSubReply = false
            item.inReplyToName = null
            item.orderIndex = index
            item.pid?.let { pidMap[it] = item }
            val author = item.author
            if (!author.isNullOrBlank()) {
                nameMap.getOrPut(author) { ArrayList() }.add(item)
            }
        }

        for (item in all) {
            
            var target: ReplyItem? = null

            
            
            
            val quotedPid = item.quotedPid
            if (!quotedPid.isNullOrEmpty()) {
                val cand = pidMap[quotedPid]
                if (cand != null && cand !== item) target = cand
            }
            if (target == null) {
                
                
                
                
                target = matchByQuotedContent(item, all)
                if (target == null) {
                    val uid = item.quotedUid
                    if (!uid.isNullOrEmpty()) {
                        target = all.lastOrNull {
                            it !== item && it.authorUid == uid && it.orderIndex < item.orderIndex
                        }
                    }
                }
                if (target == null) {
                    val name = item.quotedAuthorName ?: extractQuotedNameFromQuote(item)
                    if (!name.isNullOrEmpty()) {
                        
                        
                        target = nameMap[name]?.lastOrNull {
                            it !== item && !it.isSubReply && it.orderIndex < item.orderIndex
                        }
                    }
                }
            }

            if (target == null || target === item) continue

            
            
            val root = if (target.isSubReply) topLevelOf(target, all) else target
            if (root == null || root === item) continue
            
            
            if (root.orderIndex >= item.orderIndex) continue

            item.isSubReply = true
            
            if (root !== target) {
                item.inReplyToName = target.author
            }
            root.subReplies.add(item)
        }
    }

    






    private fun matchByQuotedContent(item: ReplyItem, all: List<ReplyItem>): ReplyItem? {
        val quoted = normalizeForMatch(item.quotedContentText ?: return null)
        
        if (quoted.length < MATCH_MIN_CHARS) return null
        val qa = quoted.toCharArray()
        var best: ReplyItem? = null
        var bestScore = 0
        for (cand in all) {
            if (cand === item || cand.orderIndex >= item.orderIndex) continue
            val body = normalizeForMatch(cand.contentText)
            if (body.isEmpty()) continue
            val score = longestCommonSubstring(qa, body.toCharArray())
            if (score > bestScore) {
                bestScore = score
                best = cand
            }
        }
        if (best == null) return null
        
        
        return if (bestScore >= MATCH_MIN_CHARS && bestScore >= quoted.length / 3) best else null
    }

    
    private fun normalizeForMatch(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var s = Regex("(?s)<[^>]+>").replace(raw, "")
        s = Regex("(?i)\\[/?(?:quote|free|hide|code|color|url|size|b|i|u|font|align)[^\\]]*\\]").replace(s, "")
        return Regex("[\\s\\u00A0]").replace(s, "")
    }

    
    private fun longestCommonSubstring(a: CharArray, b: CharArray): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var prev = IntArray(b.size + 1)
        var cur = IntArray(b.size + 1)
        var best = 0
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else 0
                if (cur[j] > best) best = cur[j]
            }
            val swap = prev
            prev = cur
            cur = swap
            java.util.Arrays.fill(cur, 0)
        }
        return best
    }

    
    private fun topLevelOf(child: ReplyItem, all: List<ReplyItem>): ReplyItem? {
        return all.firstOrNull { !it.isSubReply && it.subReplies.contains(child) }
    }

    









    private fun extractQuotedNameFromQuote(item: ReplyItem): String? {
        val quote = item.quotedContentText ?: return null
        
        Regex("(?:回复\\s*)?([^\\n]{1,24}?)\\s*发表于").find(quote)?.let {
            val name = it.groupValues[1].trim()
                .removePrefix("回复").trim()
                .removePrefix("@").trim()
            if (name.isNotEmpty()) return name
        }
        
        Regex("回复\\s*@?([^\\s，。,:：]{1,24})").find(quote)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotEmpty()) return name
        }
        return null
    }

    private fun isSequentialOrRepeatedDigits(s: String): Boolean {
        if (s.length < 2 || !s.all { it.isDigit() }) return false
        
        if (s.all { it == s[0] }) return true
        
        if (s.length >= 3) {
            var isInc = true
            for (i in 0 until s.length - 1) {
                if (s[i + 1] - s[i] != 1) {
                    isInc = false
                    break
                }
            }
            if (isInc) return true

            
            var isDec = true
            for (i in 0 until s.length - 1) {
                if (s[i] - s[i + 1] != 1) {
                    isDec = false
                    break
                }
            }
            if (isDec) return true
        }
        return false
    }

    private fun isWaterReply(item: ReplyItem): Boolean {
        val rawHtml = item.contentHtml ?: item.contentText ?: ""
        
        val textOnly = org.jsoup.Jsoup.parse(rawHtml).text().trim()

        
        var clean = textOnly.replace(Regex("\\{:\\d+_\\d+:\\}"), "")
            .replace(Regex("\\[em:\\d+\\]"), "")
            .replace(Regex("[\\s\\u00A0\\u3000]"), "")

        
        val emojiRegex = Regex("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2300-\u23FF\u2B50\u2B55\u200D\uFE0F]")
        val textWithoutEmoji = clean.replace(emojiRegex, "")

        
        val textWithoutPunct = textWithoutEmoji.replace(Regex("[\\p{P}\\p{S}，。！？!?,.~～、_—\\-+=\\[\\](){};；:：“”\"'/\\\\`]"), "")

        
        val containKeywords = arrayOf(
            "感谢", "看看", "隐藏", "分享",
            "学习学习", "学习一下", "论坛有你更精彩", "支持一下", "66666"
        )
        for (kw in containKeywords) {
            if (textOnly.contains(kw, ignoreCase = true)) return true
        }

        
        if (clean.length <= 2) {
            return true
        }

        
        if (textWithoutPunct.isEmpty()) {
            return true
        }

        
        val exactMatchPhrases = hashSetOf(
            "拿走试试", "大佬牛逼", "拿走了", "查看内容", "这么牛逼啊",
            "大佬厉害了", "牛逼啊大佬", "我来了", "谢谢大佬", "我来试一试",
            "哇哇哇", "啊啊啊", "膜拜大佬", "严肃学习", "大佬牛批",
            "谢谢啦", "谢谢了", "来了来了", "回复一下表示支持",
            "谢谢楼主", "可以可以", "小飞机来了", "小飞机来喽", "小飞机来咯"
        )
        if (exactMatchPhrases.contains(textWithoutPunct) || exactMatchPhrases.contains(clean)) {
            return true
        }

        
        if (textWithoutPunct.isNotEmpty() && textWithoutPunct.all { it.isDigit() }) {
            if (isSequentialOrRepeatedDigits(textWithoutPunct) || textWithoutPunct.length >= 3) {
                return true
            }
        }

        return false
    }

    private fun rebuildDisplayList() {
        displayList.clear()
        val n = rawReplyList.size
        if (n == 0) {
            foldedCount = 0
            onFoldStateChanged?.invoke(0, isFoldExpanded)
            return
        }

        var count = 0
        for (item in rawReplyList) {
            if (item.isSubReply) {
                
                continue
            }
            val isWater = isWaterReply(item)
            if (isWater) {
                count++
                if (isFoldExpanded) {
                    displayList.add(DisplayRow(item = item))
                }
            } else {
                displayList.add(DisplayRow(item = item))
            }
        }
        foldedCount = count
        onFoldStateChanged?.invoke(count, isFoldExpanded)
    }

    
    fun toggleFoldExpanded() {
        isFoldExpanded = !isFoldExpanded
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecycler = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecycler === recyclerView) attachedRecycler = null
    }

    interface OnReplyClickListener {
        fun onReplyClick(item: ReplyItem?, position: Int)
    }

    interface OnUserClickListener {
        fun onUserClick(item: ReplyItem?, position: Int)
    }

    
    interface OnReplyLongClickListener {
        fun onReplyLongClick(item: ReplyItem?, position: Int)
    }

    fun setOnReplyLongClickListener(listener: OnReplyLongClickListener?) {
        this.replyLongClickListener = listener
    }

    fun setOnReplyClickListener(listener: OnReplyClickListener?) {
        this.replyClickListener = listener
    }

    fun setOnUserClickListener(listener: OnUserClickListener?) {
        this.userClickListener = listener
    }

    private fun hasHeader(): Boolean = headerProvider != null

    
    enum class FooterState { LOADING, RETRY, END }

    interface FooterActionListener {
        fun onRetry()
    }

    var footerState: FooterState? = null
        private set
    private var footerActionListener: FooterActionListener? = null

    fun setFooterActionListener(listener: FooterActionListener?) {
        footerActionListener = listener
    }

    
    fun setFooterState(state: FooterState?) {
        if (footerState == state) return
        val had = footerState != null
        val has = state != null
        footerState = state
        val tail = displayList.size + (if (hasHeader()) 1 else 0)
        when {
            !had && has -> notifyItemInserted(tail)
            had && !has -> notifyItemRemoved(tail)
            else -> notifyItemChanged(tail)
        }
    }

    fun setHeaderProvider(provider: HeaderProvider?) {
        headerProvider = provider
        if (provider != null) notifyItemInserted(0) else if (itemCount > 0) notifyItemRemoved(0)
    }

    fun updateData(newList: List<ReplyItem>?) {
        this.rawReplyList = newList ?: ArrayList()
        organizeReplies()
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        if (hasHeader() && position == 0) return TYPE_HEADER
        if (footerState != null && position == itemCount - 1) return TYPE_FOOTER
        return TYPE_REPLY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_HEADER) {
            val v = headerProvider!!.onCreateHeaderView(parent)
            return HeaderViewHolder(v)
        }
        if (viewType == TYPE_FOOTER) {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reply_footer, parent, false)
            return FooterViewHolder(v)
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reply, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            headerProvider?.onBindHeaderView(holder.itemView)
            return
        }
        if (holder is FooterViewHolder) {
            holder.bind(footerState ?: FooterState.END)
            return
        }
        
        
        (holder as ViewHolder).bind(displayList[replyIndex(position)])
    }

    
    private fun replyIndex(position: Int): Int {
        return if (hasHeader()) position - 1 else position
    }

    override fun getItemCount(): Int {
        return displayList.size + (if (hasHeader()) 1 else 0) + (if (footerState != null) 1 else 0)
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progress: View = itemView.findViewById(R.id.footer_progress)
        private val text: TextView = itemView.findViewById(R.id.footer_text)

        fun bind(state: FooterState) {
            when (state) {
                FooterState.LOADING -> {
                    progress.visibility = View.VISIBLE
                    text.text = "加载中…"
                    itemView.isClickable = false
                    itemView.setOnClickListener(null)
                }
                FooterState.RETRY -> {
                    progress.visibility = View.GONE
                    text.text = "加载失败，点击重试"
                    itemView.isClickable = true
                    itemView.setOnClickListener { footerActionListener?.onRetry() }
                }
                FooterState.END -> {
                    progress.visibility = View.GONE
                    text.text = "已经到底了"
                    itemView.isClickable = false
                    itemView.setOnClickListener(null)
                }
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val ivAvatar: ImageView = itemView.findViewById(R.id.iv_reply_avatar)
        private val tvFloorLabel: TextView = itemView.findViewById(R.id.tv_floor_label)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_reply_author)
        private val tvOpBadge: TextView = itemView.findViewById(R.id.tv_op_badge)
        private val tvLevel: TextView = itemView.findViewById(R.id.tv_reply_level)
        private val tvReplyEditTime: TextView? = itemView.findViewById(R.id.tv_reply_edit_time)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_reply_content)
        private val layoutReplyQuote: LinearLayout = itemView.findViewById(R.id.layout_reply_quote)
        private val tvReplyQuoteTitle: TextView? = itemView.findViewById(R.id.tv_reply_quote_title)
        private val tvReplyQuote: TextView = itemView.findViewById(R.id.tv_reply_quote)
        private val llReplyImages: LinearLayout = itemView.findViewById(R.id.ll_reply_images)
        private val layoutCollapsedHint: View? = itemView.findViewById(R.id.layout_collapsed_hint)
        private val ivCollapsedIcon: ImageView? = itemView.findViewById(R.id.iv_collapsed_icon)
        private val tvCollapsedText: TextView? = itemView.findViewById(R.id.tv_collapsed_text)

        
        private val tvReplyBottomTime: TextView? = itemView.findViewById(R.id.tv_reply_bottom_time)
        private val btnToggleSubReplies: View? = itemView.findViewById(R.id.btn_toggle_sub_replies)
        private val ivToggleArrow: ImageView? = itemView.findViewById(R.id.iv_toggle_arrow)
        private val tvToggleText: TextView? = itemView.findViewById(R.id.tv_toggle_text)
        private val btnReplyText: ImageView? = itemView.findViewById(R.id.btn_reply_text)
        private val layoutSubRepliesContainer: LinearLayout? = itemView.findViewById(R.id.layout_sub_replies_container)
        private val llSubRepliesList: LinearLayout? = itemView.findViewById(R.id.ll_sub_replies_list)

        fun bind(row: DisplayRow) {
            
            layoutCollapsedHint?.visibility = View.GONE
            itemView.setOnClickListener(null)
            ivAvatar.visibility = View.VISIBLE
            val item = row.item
            
            val avatarUrl = item.avatarUrl
            if (!TextUtils.isEmpty(avatarUrl)) {
                Glide.with(ivAvatar.context)
                    .load(avatarUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_account)
                    .error(R.drawable.ic_account)
                    .into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_account)
            }

            
            val pos = bindingAdapterPosition
            val currentItem = item
            ivAvatar.setOnClickListener {
                if (userClickListener != null && !TextUtils.isEmpty(currentItem.authorUid)) {
                    userClickListener!!.onUserClick(currentItem, pos)
                }
            }
            tvAuthor.setOnClickListener {
                if (userClickListener != null && !TextUtils.isEmpty(currentItem.authorUid)) {
                    userClickListener!!.onUserClick(currentItem, pos)
                }
            }


            
            val floorLabel = item.floorLabel
            if (!TextUtils.isEmpty(floorLabel)) {
                tvFloorLabel.visibility = View.VISIBLE
                tvFloorLabel.text = floorLabel
            } else {
                tvFloorLabel.visibility = View.GONE
            }

            
            tvAuthor.text = if (!TextUtils.isEmpty(item.author)) item.author else "匿名"

            
            if (item.isOP) {
                tvOpBadge.visibility = View.VISIBLE
            } else {
                tvOpBadge.visibility = View.GONE
            }

            
            tvLevel.visibility = View.GONE

            

            
            val quotedText = item.quotedContentText
            if (!TextUtils.isEmpty(quotedText)) {
                layoutReplyQuote.visibility = View.VISIBLE
                val rawQuote = quotedText!!.trim()
                var meta: String? = null
                var body: String = rawQuote

                val m1 = P_QUOTE_META.matcher(rawQuote)
                if (m1.find()) {
                    meta = m1.group(1)?.trim()
                    body = m1.group(2)?.trim() ?: ""
                } else {
                    val m2 = P_QUOTE_META_FALLBACK.matcher(rawQuote)
                    if (m2.find()) {
                        meta = m2.group(1)?.trim()
                        body = m2.group(2)?.trim() ?: ""
                    }
                }

                if (!meta.isNullOrEmpty()) {
                    tvReplyQuoteTitle?.visibility = View.VISIBLE
                    tvReplyQuoteTitle?.text = meta
                } else {
                    tvReplyQuoteTitle?.visibility = View.GONE
                }

                val cleanBody = stripLeadingHtmlBreak(BBCodeUtil.stripHtmlColors(body))
                val spannedQuote = Html.fromHtml(
                    cleanBody, Html.FROM_HTML_MODE_COMPACT,
                    createInlineImageGetter(tvReplyQuote),
                    BBCodeUtil.createTagHandler(itemView.context)
                )
                val processedQuote = BBCodeUtil.stripForegroundColorSpans(spannedQuote)
                val trimmedBody = trimSpanned(processedQuote ?: spannedQuote)

                val primaryColor = com.solosu.mtforum.util.ThemeManager.getThemeColor(itemView.context)

                val label = "原文"
                val prefixSpan = SpannableString(if (trimmedBody.isNotEmpty()) "$label " else label)
                prefixSpan.setSpan(
                    android.text.style.ForegroundColorSpan(primaryColor),
                    0, label.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                prefixSpan.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, label.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val fullQuote = android.text.SpannableStringBuilder()
                fullQuote.append(prefixSpan)
                fullQuote.append(trimmedBody)

                tvReplyQuote.text = fullQuote
            } else {
                layoutReplyQuote.visibility = View.GONE
                tvReplyQuote.text = ""
                tvReplyQuoteTitle?.visibility = View.GONE
            }

            val htmlContent = item.contentHtml
            val contentText = item.contentText

            val replyImageUrls = ArrayList<String>()
            val sourceHtml = if (!TextUtils.isEmpty(htmlContent)) {
                extractImagesFromHtml(htmlContent, replyImageUrls)
            } else if (!TextUtils.isEmpty(contentText)) {
                contentText!!
            } else {
                ""
            }

            if (item.imageUrls.isNotEmpty()) {
                for (u in item.imageUrls) {
                    if (isPostImageUrl(u) && replyImageUrls.none { isSameImage(it, u) }) {
                        replyImageUrls.add(u)
                    }
                }
            }

            
            val (remainingSource, editTime) = splitEditMarker(sourceHtml)
            if (!editTime.isNullOrEmpty()) {
                tvReplyEditTime?.visibility = View.VISIBLE
                tvReplyEditTime?.text = "$editTime 编辑"
            } else {
                tvReplyEditTime?.visibility = View.GONE
            }

            if (remainingSource.isNotEmpty()) {
                tvContent.visibility = View.VISIBLE
                
                
                val collapsedSource = collapseReplyHtml(remainingSource)
                
                val cleanSource = BBCodeUtil.stripHtmlColors(collapsedSource)
                val spannedSource = Html.fromHtml(
                    cleanSource, Html.FROM_HTML_MODE_COMPACT,
                    createInlineImageGetter(tvContent),
                    BBCodeUtil.createTagHandler(itemView.context)
                )
                val uncolored = BBCodeUtil.stripForegroundColorSpans(spannedSource)
                tvContent.text = trimSpanned(uncolored ?: spannedSource)
                setupClickableLinks(tvContent)
            } else {
                tvContent.visibility = View.GONE
            }

            
            if (replyImageUrls.isNotEmpty()) {
                llReplyImages.removeAllViews()
                llReplyImages.visibility = View.VISIBLE
                val cardWidth = dpToPx(itemView.context, 160)
                for (imgUrl in replyImageUrls) {
                    val imageView = ImageView(itemView.context)
                    val lp = LinearLayout.LayoutParams(
                        cardWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(itemView.context, 5)
                        bottomMargin = dpToPx(itemView.context, 5)
                    }
                    imageView.layoutParams = lp
                    imageView.adjustViewBounds = true
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    imageView.maxHeight = (cardWidth * 1.6f).toInt()
                    imageView.setBackgroundResource(R.drawable.bg_post_image_rounded)
                    imageView.clipToOutline = true
                    imageView.isClickable = true
                    imageView.isFocusable = true
                    imageView.isLongClickable = true
                    imageView.setOnLongClickListener {
                        replyLongClickListener?.onReplyLongClick(item, bindingAdapterPosition)
                        true
                    }
                    setImageClick(itemView.context, imageView, imgUrl)
                    Glide.with(itemView.context)
                        .load(imgUrl)
                        .placeholder(ColorDrawable(itemView.context.getColor(R.color.background_secondary)))
                        .error(ColorDrawable(itemView.context.getColor(R.color.divider)))
                        .into(imageView)
                    llReplyImages.addView(imageView)
                }
            } else {
                llReplyImages.removeAllViews()
                llReplyImages.visibility = View.GONE
            }

            

            
            val timeStr = item.time ?: ""
            val locStr = item.location ?: ""
            val fullTimeLoc = when {
                timeStr.isNotEmpty() && locStr.isNotEmpty() -> "$timeStr  $locStr"
                timeStr.isNotEmpty() -> timeStr
                else -> locStr
            }
            tvReplyBottomTime?.text = fullTimeLoc
            tvReplyBottomTime?.visibility = if (fullTimeLoc.isNotEmpty()) View.VISIBLE else View.GONE

            
            val author = item.author
            if (!TextUtils.isEmpty(author)) {
                btnReplyText?.visibility = View.VISIBLE
                btnReplyText?.setOnClickListener {
                    replyClickListener?.onReplyClick(item, bindingAdapterPosition)
                }
            } else {
                btnReplyText?.visibility = View.GONE
            }

            
            val subCount = item.subReplies.size
            if (subCount > 0) {
                btnToggleSubReplies?.visibility = View.VISIBLE
                updateToggleArrow(item.isSubRepliesExpanded, subCount)
                btnToggleSubReplies?.setOnClickListener {
                    item.isSubRepliesExpanded = !item.isSubRepliesExpanded
                    updateToggleArrow(item.isSubRepliesExpanded, subCount)
                    bindSubReplies(item)
                }
            } else {
                btnToggleSubReplies?.visibility = View.GONE
            }

            
            bindSubReplies(item)

            
            layoutCollapsedHint?.visibility = View.GONE

            
            
            
            val longPress = View.OnLongClickListener {
                replyLongClickListener?.onReplyLongClick(currentItem, bindingAdapterPosition)
                
                false
            }
            tvAuthor.setOnLongClickListener(longPress)
            ivAvatar.setOnLongClickListener(longPress)
            itemView.isLongClickable = false
            itemView.setOnLongClickListener(null)
            
            tvContent.isLongClickable = false
            tvContent.setOnLongClickListener(null)
            if (layoutReplyQuote.visibility == View.VISIBLE) {
                
                tvReplyQuote.isLongClickable = false
                tvReplyQuote.setOnLongClickListener(null)
            }
        }

        

        private fun updateToggleArrow(expanded: Boolean, subCount: Int) {
            ivToggleArrow?.rotation = if (expanded) 270f else 90f
            tvToggleText?.text = if (expanded) "收起回复" else "$subCount 条回复"
            btnToggleSubReplies?.contentDescription =
                if (expanded) "收起回复" else "展开 $subCount 条回复"
        }

        private fun bindSubReplies(item: ReplyItem) {
            val container = layoutSubRepliesContainer ?: return
            val listLayout = llSubRepliesList ?: return
            listLayout.removeAllViews()

            if (item.subReplies.isEmpty() || !item.isSubRepliesExpanded) {
                container.visibility = View.GONE
                return
            }

            container.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(itemView.context)

            for (subItem in item.subReplies) {
                val subView = inflater.inflate(R.layout.item_sub_reply, listLayout, false)
                val ivSubAvatar = subView.findViewById<ImageView>(R.id.iv_sub_avatar)
                val tvSubAuthor = subView.findViewById<TextView>(R.id.tv_sub_author)
                val tvSubOpBadge = subView.findViewById<TextView>(R.id.tv_sub_op_badge)
                val tvSubContent = subView.findViewById<TextView>(R.id.tv_sub_content)
                val tvSubEditTime = subView.findViewById<TextView>(R.id.tv_sub_edit_time)
                val tvSubTimeLoc = subView.findViewById<TextView>(R.id.tv_sub_time_location)
                val btnSubReply = subView.findViewById<android.view.View>(R.id.btn_sub_reply)

                if (!TextUtils.isEmpty(subItem.avatarUrl)) {
                    Glide.with(ivSubAvatar.context)
                        .load(subItem.avatarUrl)
                        .transform(CircleCrop())
                        .placeholder(R.drawable.ic_account)
                        .error(R.drawable.ic_account)
                        .into(ivSubAvatar)
                } else {
                    ivSubAvatar.setImageResource(R.drawable.ic_account)
                }

                ivSubAvatar.setOnClickListener {
                    if (!TextUtils.isEmpty(subItem.authorUid)) {
                        userClickListener?.onUserClick(subItem, bindingAdapterPosition)
                    }
                }
                tvSubAuthor.setOnClickListener {
                    if (!TextUtils.isEmpty(subItem.authorUid)) {
                        userClickListener?.onUserClick(subItem, bindingAdapterPosition)
                    }
                }

                tvSubAuthor.text = subItem.author ?: "匿名"
                tvSubOpBadge.visibility = if (subItem.isOP) View.VISIBLE else View.GONE

                
                
                
                val subHtml = subItem.contentHtml
                
                val subEditSplit = if (TextUtils.isEmpty(subHtml)) null else splitEditMarker(subHtml)
                
                
                val subSource = if (subEditSplit != null) {
                    collapseReplyHtml(restoreInlineImageSources(subEditSplit.first))
                } else {
                    ""
                }
                val subBody: CharSequence? = if (subEditSplit != null) {
                    val rendered = Html.fromHtml(
                        BBCodeUtil.stripHtmlColors(subSource),
                        Html.FROM_HTML_MODE_COMPACT,
                        createInlineImageGetter(tvSubContent),
                        BBCodeUtil.createTagHandler(itemView.context)
                    )
                    BBCodeUtil.stripForegroundColorSpans(rendered) ?: rendered
                } else {
                    subItem.contentText
                }

                val inReplyTo = subItem.inReplyToName
                val subSb = android.text.SpannableStringBuilder()
                if (!TextUtils.isEmpty(inReplyTo)) {
                    
                    val prefix = "回复 $inReplyTo："
                    subSb.append(prefix)
                    subSb.setSpan(
                        RelativeSizeSpan(0.85f), 0, prefix.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    subSb.setSpan(
                        StyleSpan(Typeface.ITALIC), 0, prefix.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (!TextUtils.isEmpty(subBody)) {
                    subSb.append(trimSpanned(subBody!!))
                }
                tvSubContent.text = subSb
                
                attachInlineImageClick(tvSubContent)
                setupClickableLinks(tvSubContent)
                val subEditTime = subEditSplit?.second
                if (!subEditTime.isNullOrEmpty()) {
                    tvSubEditTime.visibility = View.VISIBLE
                    tvSubEditTime.text = "$subEditTime 编辑"
                } else {
                    tvSubEditTime.visibility = View.GONE
                }
                
                tvSubContent.isLongClickable = false
                tvSubContent.setOnLongClickListener(null)
                tvSubAuthor.setOnLongClickListener {
                    replyLongClickListener?.onReplyLongClick(subItem, bindingAdapterPosition)
                    true
                }
                ivSubAvatar.setOnLongClickListener {
                    replyLongClickListener?.onReplyLongClick(subItem, bindingAdapterPosition)
                    true
                }

                val sTime = subItem.time ?: ""
                val sLoc = subItem.location ?: ""
                val fullSubTime = when {
                    sTime.isNotEmpty() && sLoc.isNotEmpty() -> "$sTime  $sLoc"
                    sTime.isNotEmpty() -> sTime
                    else -> sLoc
                }
                tvSubTimeLoc.text = fullSubTime

                btnSubReply.setOnClickListener {
                    replyClickListener?.onReplyClick(subItem, bindingAdapterPosition)
                }

                subView.setOnLongClickListener {
                    replyLongClickListener?.onReplyLongClick(subItem, bindingAdapterPosition)
                    true
                }

                listLayout.addView(subView)
            }
        }
    }

    companion object {

        
        private const val MATCH_MIN_CHARS = 6

        
        private const val TYPE_HEADER = 0
        private const val TYPE_REPLY = 1
        private const val TYPE_FOOTER = 2

        
        private const val IDENTITY_HEADER = "header"

        
        const val HEADER_ITEM_COUNT = 1

        
        private const val BATCH_MAX_LEN = 6

        
        private const val BATCH_PREFIX = "batch_"

        
        private const val BATCH_EMOJI_SUFFIX = "emoji"

        
        private const val MAX_HTML_SCAN = 4000

        
        private const val MAX_LABEL_TEXT_LEN = 60

        
        private val EXPRESSION_HINTS = listOf(
            "smiley", "emoticon", "/static/image/", "face", "stamp", "magic"
        )

        private val IMG_TAG_RE = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val IMG_SRC_RE = Regex("src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        




        private fun normalizeText(s: String): String {
            val sb = StringBuilder(s.length)
            for (raw in s) {
                val ch = when {
                    
                    raw.code in 0xFF01..0xFF5E -> (raw.code - 0xFEE0).toChar()
                    
                    raw.code == 0x3000 -> ' '
                    else -> raw
                }
                if (ch.isLetterOrDigit()) sb.append(ch.lowercaseChar())
            }
            return sb.toString()
        }

        private fun isBatchGroupKey(key: String): Boolean = key.startsWith(BATCH_PREFIX)

        private fun groupLabelOf(key: String): String {
            return when {
                key == BATCH_PREFIX + BATCH_EMOJI_SUFFIX -> "纯表情回复"
                isBatchGroupKey(key) -> "短回复"
                else -> "相同回复"
            }
        }

        





        private fun setupClickableLinks(textView: TextView?) {
            if (textView == null) return

            
            
            textView.setTextIsSelectable(true)
            textView.isFocusable = true
            textView.isFocusableInTouchMode = true
            textView.movementMethod = SelectableLinkMovementMethod()
            textView.isClickable = true
            textView.isLongClickable = true
            textView.highlightColor = 0x66FFC107

            
            var value: CharSequence? = textView.text
            if (value !is Spannable) {
                
                value = SpannableString(value ?: "")
                textView.setText(value, TextView.BufferType.SPANNABLE)
            }

            
            val spannable = textView.text as Spannable

            
            val urlPattern = Pattern.compile(
                "(?<!\\w)" +  
                        "(?:" +
                        "https?://[^\\s<>\"\\x00-\\x1f\\x7f-\\xff]+" +  
                        "|" +
                        "www\\.[^\\s<>\"\\x00-\\x1f\\x7f-\\xff]+" +      
                        ")" +
                        "(?<![,.;:!?)>])",  
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val themeColor = com.solosu.mtforum.util.ThemeManager.getThemeColor(textView.context)

            FixNestedScrollLinkMovementMethod.matcherLinkify(
                spannable,
                urlPattern,
                { url -> url },  
                { url -> openContentLink(url, textView.context) },  
                themeColor
            )

            
            val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
            for (oldSpan in urlSpans) {
                var targetUrl = oldSpan.url
                
                if (targetUrl.startsWith("//")) {
                    targetUrl = "https:$targetUrl"
                } else if (targetUrl.startsWith("/")) {
                    targetUrl = HttpClient.BASE_URL + targetUrl.substring(1)
                } else if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    targetUrl = HttpClient.BASE_URL + targetUrl
                }

                val start = spannable.getSpanStart(oldSpan)
                val end = spannable.getSpanEnd(oldSpan)
                val flags = spannable.getSpanFlags(oldSpan)
                spannable.removeSpan(oldSpan)
                if (start >= 0 && end > start && !TextUtils.isEmpty(targetUrl)) {
                    val finalUrl = targetUrl
                    spannable.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            openContentLink(finalUrl, widget.context)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = themeColor
                            ds.isUnderlineText = true
                        }
                    }, start, end, flags)
                }
            }

            
            
            textView.autoLinkMask = 0
            
            textView.setTextIsSelectable(true)
            textView.highlightColor = 0x66FFC107
        }

        


        private fun openContentLink(url: String?, context: Context?) {
            if (TextUtils.isEmpty(url) || context == null) return
            try {
                val lower = url!!.lowercase(java.util.Locale.ROOT)
                val isForumLink = lower.contains("bbs.binmt.cc")

                if (isForumLink) {
                    
                    val threadMatcher = Pattern.compile("thread[-=]?(\\d+)").matcher(lower)
                    if (threadMatcher.find()) {
                        NavigationHelper.openThread(context, threadMatcher.group(1))
                        return
                    }
                    
                    val uidMatcher = Pattern.compile("(?:uid[-=]|(?<=[?&])uid=)(\\d+)").matcher(lower)
                    if (uidMatcher.find()) {
                        val intent = Intent(context, UserProfileActivity::class.java)
                        intent.putExtra("uid", uidMatcher.group(1))
                        context.startActivity(intent)
                        return
                    }
                    val usernameMatcher = Pattern.compile("space-username-([^./?&]+)").matcher(lower)
                    if (usernameMatcher.find()) {
                        val intent = Intent(context, UserProfileActivity::class.java)
                        intent.putExtra("username", usernameMatcher.group(1))
                        context.startActivity(intent)
                        return
                    }
                }
                
                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
            } catch (ignored: Exception) {
            }
        }

        


        private fun setImageClick(context: Context?, imageView: ImageView, imgUrl: String?) {
            imageView.setOnClickListener {
                if (context != null && !TextUtils.isEmpty(imgUrl)) {
                    val intent = Intent(context, ImagePreviewActivity::class.java)
                    intent.putExtra("image_url", imgUrl)
                    context.startActivity(intent)
                }
            }
        }

        


        private fun isSmileyOrIcon(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains("smiley") || lower.contains("emoticon")
                    || lower.contains("face") || lower.contains("/static/image/smiley")
                    || lower.contains("stamp") || lower.contains("magic")
                    || lower.contains("mini") || lower.contains("icon")
                    || lower.contains("common_")
        }

        private fun isPostImageUrl(url: String?): Boolean {
            if (TextUtils.isEmpty(url)) return false
            val lower = url!!.lowercase(Locale.ROOT)
            if (lower.contains("none.gif") || lower.contains("none.png") || lower.contains("blank.gif")
                || lower.contains("loading") || lower.contains("avatar.php")
                || lower.contains("/static/image/common/") || lower.contains("/static/image/filetype/")
                || lower.contains("/static/image/smiley/")) {
                return false
            }
            if (lower.contains("smiley") || lower.contains("emoticon")) {
                return false
            }
            return true
        }

        private fun extractAidFromUrl(url: String): String {
            val m = Pattern.compile("(?i)[?&]aid=([^&#]+)").matcher(url)
            return if (m.find()) m.group(1) ?: "" else ""
        }

        private fun getImageCanonicalKey(url: String?): String {
            if (url.isNullOrBlank()) return ""
            val clean = url.trim().lowercase(Locale.ROOT)
            val aid = extractAidFromUrl(clean)
            if (aid.isNotEmpty()) return "aid:$aid"
            val noQuery = clean.substringBefore("?").substringBefore("#")
            val stripped = noQuery
                .replace(".thumb.jpg", "")
                .replace(".thumb.png", "")
                .replace(".middle.jpg", "")
                .replace(".middle.png", "")
                .replace("_thumb.jpg", ".jpg")
                .replace("_thumb.png", ".png")
            val lastSlash = stripped.lastIndexOf('/')
            val filename = if (lastSlash >= 0) stripped.substring(lastSlash + 1) else stripped
            if (filename.endsWith(".php") || filename.isEmpty()) {
                return clean
            }
            return filename
        }

        private fun isSameImage(url1: String?, url2: String?): Boolean {
            if (url1.isNullOrBlank() || url2.isNullOrBlank()) return false
            if (url1.equals(url2, ignoreCase = true)) return true
            val key1 = getImageCanonicalKey(url1)
            val key2 = getImageCanonicalKey(url2)
            return key1.isNotEmpty() && key1 == key2
        }

        private fun firstNonEmptyAttr(element: org.jsoup.nodes.Element, vararg attrNames: String): String? {
            for (attr in attrNames) {
                if (element.hasAttr(attr)) {
                    val value = element.attr(attr).trim()
                    if (value.isNotEmpty()
                        && !value.contains("none.gif", ignoreCase = true)
                        && !value.contains("none.png", ignoreCase = true)
                        && !value.contains("blank.gif", ignoreCase = true)) {
                        return value
                    }
                }
            }
            return null
        }

        




        private fun attachInlineImageClick(textView: TextView) {
            val slop = android.view.ViewConfiguration.get(textView.context).scaledTouchSlop
            textView.setOnTouchListener(object : View.OnTouchListener {
                private var downX = 0f
                private var downY = 0f
                private var downTime = 0L

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            downTime = System.currentTimeMillis()
                        }
                        MotionEvent.ACTION_UP -> {
                            val moved = Math.abs(event.x - downX) > slop ||
                                    Math.abs(event.y - downY) > slop
                            val longPress = System.currentTimeMillis() - downTime > 400
                            if (!moved && !longPress) {
                                val url = findInlineImageUrlAt(textView, event.x, event.y)
                                if (url != null) {
                                    val intent = Intent(v.context, ImagePreviewActivity::class.java)
                                    intent.putExtra("image_url", url)
                                    v.context.startActivity(intent)
                                    return true
                                }
                            }
                        }
                    }
                    return false
                }
            })
        }

        
        private fun findInlineImageUrlAt(textView: TextView, x: Float, y: Float): String? {
            val layout = textView.layout ?: return null
            val text = textView.text as? Spanned ?: return null
            for (span in text.getSpans(0, text.length, ImageSpan::class.java)) {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                if (start < 0 || end < 0) continue
                val top = layout.getLineTop(layout.getLineForOffset(start))
                val bottom = layout.getLineBottom(layout.getLineForOffset(end))
                val left = layout.getPrimaryHorizontal(start)
                val right = layout.getPrimaryHorizontal(end)
                if (x in left..right && y >= top && y <= bottom) {
                    val url = normalizeImageUrl(span.source) ?: continue
                    if (isSmileyOrIcon(url)) continue
                    return url
                }
            }
            return null
        }

        




        private fun restoreInlineImageSources(html: String?): String {
            if (html.isNullOrEmpty() || !html.contains("<img", ignoreCase = true)) return html ?: ""
            return try {
                val doc = org.jsoup.Jsoup.parseBodyFragment(html)
                for (ignoreOp in doc.select("ignore_js_op")) {
                    ignoreOp.unwrap()
                }
                for (img in doc.select("img")) {
                    val realUrl = firstNonEmptyAttr(
                        img,
                        "zoomfile", "file", "comiis_loadimages", "data-original",
                        "data-src", "data-file", "data-lazy-src", "src"
                    )
                    val fullUrl = normalizeImageUrl(realUrl)
                    if (fullUrl.isNullOrEmpty()) {
                        img.remove()
                    } else {
                        img.attr("src", fullUrl)
                    }
                }
                doc.body().html()
            } catch (e: Exception) {
                html
            }
        }

        
        




        private fun collapseReplyHtml(html: String): String {
            if (html.isEmpty()) return html
            return Regex("(?i)(?:<br\\s*/?>\\s*){2,}").replace(html, "<br>")
                .replace(Regex("(?i)<p\\s*>\\s*(?:&nbsp;|&#160;|\\s)*</p>"), "")
                .replace(Regex("(?i)<div[^>]*>\\s*(?:&nbsp;|&#160;|<br\\s*/?>|\\s)*</div>"), "")
                .replace(Regex("(?i)(?:\\r?\\n\\s*){3,}"), "\n\n")
        }

        private fun extractImagesFromHtml(html: String?, outImageUrls: MutableList<String>): String {
            if (TextUtils.isEmpty(html)) {
                return ""
            }
            try {
                val doc = org.jsoup.Jsoup.parseBodyFragment(html!!)
                for (ignoreOp in doc.select("ignore_js_op")) {
                    ignoreOp.unwrap()
                }
                val imgs = doc.select("img")
                for (img in imgs) {
                    val realUrl = firstNonEmptyAttr(
                        img,
                        "zoomfile", "file", "comiis_loadimages", "data-original", "data-src",
                        "data-file", "data-lazy-src", "src"
                    )
                    val fullUrl = normalizeImageUrl(realUrl)
                    if (fullUrl != null && isPostImageUrl(fullUrl)) {
                        if (outImageUrls.none { isSameImage(it, fullUrl) }) {
                            outImageUrls.add(fullUrl)
                        }
                        img.remove()
                    } else if (fullUrl != null && isSmileyOrIcon(fullUrl)) {
                        img.attr("src", fullUrl)
                    } else {
                        img.remove()
                    }
                }
                return doc.body().html()
            } catch (e: Exception) {
                return html ?: ""
            }
        }

        


        private fun normalizeImageUrl(url: String?): String? {
            if (TextUtils.isEmpty(url)) return null
            return if (url!!.startsWith("//")) {
                "https:$url"
            } else if (url.startsWith("/")) {
                HttpClient.BASE_URL + url.substring(1)
            } else if (url.startsWith("./")) {
                HttpClient.BASE_URL + url.substring(2)
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                
                HttpClient.BASE_URL + url
            }
        }

        


        private fun loadReplyImages(context: Context, html: String?, container: LinearLayout) {
            val urls = ArrayList<String>()
            extractImagesFromHtml(html, urls)
            if (urls.isEmpty()) {
                container.visibility = View.GONE
                return
            }
            container.removeAllViews()
            container.visibility = View.VISIBLE
            val maxImgWidth = getMaxImageWidth(context)
            for (imgUrl in urls) {
                val imageView = ImageView(context)
                imageView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                imageView.adjustViewBounds = true
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.setBackgroundResource(R.drawable.bg_post_image_rounded)
                imageView.clipToOutline = true
                imageView.maxWidth = maxImgWidth
                imageView.maxHeight = (maxImgWidth * 1.2f).toInt()
                
                imageView.isClickable = true
                imageView.isFocusable = true
                setImageClick(context, imageView, imgUrl)
                Glide.with(context)
                    .load(imgUrl)
                    .placeholder(ColorDrawable(context.getColor(R.color.background_secondary)))
                    .error(ColorDrawable(context.getColor(R.color.divider)))
                    .into(imageView)
                container.addView(imageView)
            }
        }

        


        private fun getMaxImageWidth(context: Context): Int {
            val screenWidth = Resources.getSystem().displayMetrics.widthPixels
            val maxDp = 180
            val maxPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, maxDp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
            val widthHalf = (screenWidth * 0.52f).toInt()
            return Math.min(widthHalf, maxPx)
        }

        

        




        private fun createInlineImageGetter(targetView: TextView): Html.ImageGetter {
            return Html.ImageGetter { source ->
                var imgUrl = source
                if (imgUrl.startsWith("//")) {
                    imgUrl = "https:$imgUrl"
                } else if (imgUrl.startsWith("/")) {
                    imgUrl = HttpClient.BASE_URL + imgUrl.substring(1)
                } else if (!imgUrl.startsWith("http")) {
                    imgUrl = HttpClient.BASE_URL + imgUrl
                }

                val tv = targetView
                val placeholder = UrlDrawable(tv, dpToPx(tv.context, 24))
                val maxW = getMaxImageWidth(tv.context)

                Glide.with(tv.context)
                    .load(imgUrl)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            var w = resource.intrinsicWidth
                            var h = resource.intrinsicHeight
                            val emotSize = dpToPx(tv.context, 24)
                            val maxSize = if (maxW > 0) maxW else dpToPx(tv.context, 320)
                            if (w <= 0) w = emotSize
                            if (h <= 0) h = emotSize
                            
                            
                            if (w <= dpToPx(tv.context, 32)) {
                                val r = emotSize.toFloat() / Math.max(w, h)
                                w = Math.max(1, (w * r).toInt())
                                h = Math.max(1, (h * r).toInt())
                            } else if (w > maxSize) {
                                h = (h.toLong() * maxSize / Math.max(1, w)).toInt()
                                w = maxSize
                            }
                            resource.setBounds(0, 0, w, h)
                            
                            placeholder.setRealNoRelayout(resource)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            
                            placeholder.setBounds(0, 0, 0, 0)
                            tv.requestLayout()
                            tv.postInvalidate()
                        }

                        override fun onLoadCleared(placeholderD: Drawable?) {
                        }
                    })
                placeholder
            }
        }

        private val P_QUOTE_META = Pattern.compile(
            "^((?:回复\\s+)?.+?\\s+发表于\\s+(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?|(?:\\d+\\s*(?:秒|分钟|小时|天)前)|(?:半小时前)|(?:昨天|前天)\\s+\\d{1,2}:\\d{2}(?::\\d{2})?))\\s*([\\s\\S]*)",
            Pattern.CASE_INSENSITIVE
        )
        private val P_QUOTE_META_FALLBACK = Pattern.compile(
            "^((?:回复\\s+)?.+?\\s+发表于[^\\r\\n]+)[\\r\\n]+\\s*([\\s\\S]*)",
            Pattern.CASE_INSENSITIVE
        )

        
        private val P_EDIT_MARKER = Pattern.compile(
            "(?is)(?:<(?:i|span|font|div|p|em)\\b[^>]*>|\\s)*本[帖贴]最后由[\\s\\S]*?编辑(?:\\s*</(?:i|span|font|div|p|em)>)*"
        )

        



        private fun splitEditMarker(html: String?): Pair<String, String?> {
            if (html.isNullOrEmpty()) return (html ?: "") to null
            val m = P_EDIT_MARKER.matcher(html)
            if (!m.find()) return stripLeadingHtmlBreak(html) to null
            var pureText = Regex("<[^>]+>").replace(m.group(0) ?: "", "")
            pureText = Regex("&nbsp;").replace(pureText, " ").trim()
            return stripLeadingHtmlBreak(m.replaceFirst("")) to extractEditTime(pureText)
        }

        private fun stripLeadingHtmlBreak(html: String?): String {
            if (html.isNullOrEmpty()) return ""
            var s = html.trim()
            val leadPattern = Pattern.compile(
                "^(?:\\s|&nbsp;|<br\\s*/?>|<p>(?:\\s|&nbsp;|<br\\s*/?>)*</p>|<div>(?:\\s|&nbsp;|<br\\s*/?>)*</div>)+",
                Pattern.CASE_INSENSITIVE
            )
            while (true) {
                val m = leadPattern.matcher(s)
                if (m.find()) {
                    s = s.substring(m.end()).trim()
                } else {
                    break
                }
            }
            return s
        }

        private fun trimSpanned(spanned: CharSequence): CharSequence {
            var start = 0
            var end = spanned.length
            while (start < end && (spanned[start].isWhitespace() || spanned[start] == '\u00A0')) {
                start++
            }
            while (end > start && (spanned[end - 1].isWhitespace() || spanned[end - 1] == '\u00A0')) {
                end--
            }
            if (start == 0 && end == spanned.length) {
                return spanned
            }
            return spanned.subSequence(start, end)
        }

        private fun dpToPx(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        



        private fun extractEditTime(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val m = Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?").find(text)
            return m?.value
        }
    }
}
