package com.solosu.mtforum.ui.message

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.solosu.mtforum.util.ToastUtil as Toast

import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.solosu.mtforum.R
import com.solosu.mtforum.adapter.ChatMessageAdapter
import com.solosu.mtforum.model.ChatMessage
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.session.DiscuzUserActionManager
import com.solosu.mtforum.session.UserSessionManager

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean


class ChatActivity : AppCompatActivity() {
    private var pmid: String? = null
    private var uid: String? = null
    private var name: String? = null
    private var avatar: String? = null
    private var adapter: ChatMessageAdapter? = null
    private var recycler: RecyclerView? = null
    private var progress: ProgressBar? = null
    private var input: EditText? = null
    private var status: TextView? = null
    private val loading = AtomicBoolean(false)
    
    private val pendingMessages: MutableList<ChatMessage> = ArrayList()
    @Volatile
    private var reloadAfterSend = false
    @Volatile
    private var destroyed = false

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        pmid = getIntent().getStringExtra(EXTRA_PMID)
        uid = getIntent().getStringExtra(EXTRA_UID)
        name = getIntent().getStringExtra(EXTRA_NAME)
        avatar = getIntent().getStringExtra(EXTRA_AVATAR)

        val title = findViewById<TextView>(R.id.tv_chat_title)
        status = findViewById(R.id.tv_chat_status)
        val back = findViewById<TextView>(R.id.tv_chat_back)
        recycler = findViewById(R.id.chat_recycler)
        progress = findViewById(R.id.chat_progress)
        input = findViewById(R.id.chat_input)
        val send = findViewById<TextView>(R.id.chat_send)

        title.setText(if (TextUtils.isEmpty(name)) "消息" else name)
        status!!.setText("")
        back.setOnClickListener { v -> finish() }

        adapter = ChatMessageAdapter(this)
        recycler!!.setLayoutManager(LinearLayoutManager(this))
        recycler!!.setAdapter(adapter)
        send.setOnClickListener { v -> sendMessage() }
        loadMessages()
    }

    private fun canUpdateUi(): Boolean {
        return !destroyed && !isFinishing()
                && (android.os.Build.VERSION.SDK_INT < 17 || !isDestroyed())
    }

    private fun loadMessages() {
        if (TextUtils.isEmpty(pmid)) {
            Toast.makeText(this, "缺少会话ID", Toast.LENGTH_SHORT).show()
            return
        }
        if (!loading.compareAndSet(false, true)) {
            
            reloadAfterSend = true
            return
        }
        reloadAfterSend = false
        if (canUpdateUi()) progress!!.setVisibility(View.VISIBLE)

        val currentUid = UserSessionManager.getInstance()
                .getUid(getApplicationContext())
        java.lang.Thread({
            try {
                val client = HttpClient.getInstance()
                client.syncFromCookieManager()
                val url = HttpClient.BASE_URL +
                        "home.php?mod=space&do=pm&subop=view&touid=" + pmid +
                        "&mobile=2&_ts=" + System.currentTimeMillis()
                val html = client.get(url)
                val messages = ForumParser.parseChatMessages(
                        html, currentUid, avatar)
                val parsedStatus = ForumParser.parseChatOnlineStatus(html)
                val onlineText = if (parsedStatus == null) "" else parsedStatus

                runOnUiThread {
                    loading.set(false)
                    if (!canUpdateUi()) return@runOnUiThread
                    progress!!.setVisibility(View.GONE)
                    status!!.setText(onlineText)
                    status!!.setTextColor(if (onlineText.contains("在线"))
                            0xFF35A853.toInt() else getColor(R.color.text_hint))
                    val visibleMessages = mergePendingMessages(messages)
                    adapter!!.setItems(visibleMessages)
                    if (visibleMessages != null && !visibleMessages.isEmpty()) {
                        recycler!!.scrollToPosition(visibleMessages.size - 1)
                    }
                    
                    
                    
                    if (reloadAfterSend) {
                        reloadAfterSend = false
                        recycler!!.postDelayed({ loadMessages() }, 120)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading.set(false)
                    if (!canUpdateUi()) return@runOnUiThread
                    progress!!.setVisibility(View.GONE)
                    Toast.makeText(this, "聊天记录加载失败", Toast.LENGTH_SHORT).show()
                }
            }
        }, "chat-load").start()
    }

    private fun mergePendingMessages(messages: List<ChatMessage>?): List<ChatMessage> {
        val merged: MutableList<ChatMessage> = ArrayList()
        if (messages != null) merged.addAll(messages)

        var i = 0
        while (i < pendingMessages.size) {
            val pending = pendingMessages[i]
            var alreadyLoaded = false
            for (loaded in merged) {
                if (loaded != null && loaded.outgoing
                        && TextUtils.equals(loaded.content, pending.content)) {
                    alreadyLoaded = true
                    break
                }
            }
            if (alreadyLoaded) {
                pendingMessages.removeAt(i)
            } else {
                merged.add(pending)
                i++
            }
        }
        return merged
    }

    private fun createPendingMessage(text: String): ChatMessage {
        val now = Date()
        val message = ChatMessage()
        message.content = text
        message.avatarUrl = avatar
        message.authorUid = UserSessionManager.getInstance().getUid(getApplicationContext())
        message.date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(now)
        message.time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(now)
        message.outgoing = true
        return message
    }

    private fun showPendingMessage(text: String) {
        val pending = createPendingMessage(text)
        pendingMessages.add(pending)
        
        adapter!!.addItem(pending)
        recycler!!.post { recycler!!.scrollToPosition(Math.max(0, adapter!!.getItemCount() - 1)) }
    }

    private fun sendMessage() {
        if (!canUpdateUi()) return
        val text = if (input!!.getText() == null) "" else input!!.getText().toString().trim()
        if (text.isEmpty()) return
        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "缺少对方UID，无法发送", Toast.LENGTH_SHORT).show()
            return
        }

        input!!.setEnabled(false)
        java.lang.Thread({
            val success = DiscuzUserActionManager.sendPrivateMessage(
                    getApplicationContext(), uid, "", text)
            runOnUiThread {
                if (!canUpdateUi()) return@runOnUiThread
                input!!.setEnabled(true)
                if (success) {
                    input!!.setText("")
                    
                    showPendingMessage(text)
                    
                    
                    loadMessages()
                } else {
                    Toast.makeText(this, "发送失败，请检查登录状态",
                            Toast.LENGTH_SHORT).show()
                }
            }
        }, "chat-send").start()
    }

    override fun onDestroy() {
        destroyed = true
        loading.set(false)
        if (recycler != null) recycler!!.setAdapter(null)
        adapter = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PMID = "pmid"
        const val EXTRA_UID = "uid"
        const val EXTRA_NAME = "name"
        const val EXTRA_AVATAR = "avatar"
    }
}
