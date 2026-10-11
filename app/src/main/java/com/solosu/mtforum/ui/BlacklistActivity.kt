package com.solosu.mtforum.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.solosu.mtforum.util.ToastUtil as Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.solosu.mtforum.R
import com.solosu.mtforum.session.BlacklistManager
import com.solosu.mtforum.session.BlacklistSyncer
import com.solosu.mtforum.ui.space.UserProfileActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale





class BlacklistActivity : AppCompatActivity() {

    private lateinit var tvCount: TextView
    private lateinit var adapter: EntryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        
        val bgTv = android.util.TypedValue()
        val bgColor = if (theme.resolveAttribute(R.attr.appColorBackground, bgTv, true)) {
            bgTv.data
        } else {
            ContextCompat.getColor(this, R.color.background)
        }
        root.setBackgroundColor(bgColor)

        
        val toolbar = MaterialToolbar(this)
        toolbar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48f)
        )
        toolbar.title = "个人小黑屋"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary))
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationIconTint(ContextCompat.getColor(this, R.color.text_primary))
        toolbar.setNavigationOnClickListener { finish() }
        root.addView(toolbar)

        tvCount = TextView(this)
        tvCount.setPadding(dp(16f), dp(10f), dp(16f), dp(8f))
        tvCount.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
        tvCount.textSize = 12f
        tvCount.isClickable = true
        
        tvCount.setOnLongClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("清空黑名单")
                .setMessage("确定清空本地全部黑名单？")
                .setPositiveButton("清空") { d, w ->
                    BlacklistManager.clearLocal(this)
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        root.addView(tvCount, LinearLayout.LayoutParams(-1, -2))

        val listView = ListView(this)
        listView.setPadding(0, 0, 0, 0)
        listView.clipToPadding = false
        listView.isVerticalScrollBarEnabled = false
        adapter = EntryAdapter(this)
        listView.adapter = adapter

        
        listView.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        root.addView(listView)

        setContentView(root)

        render()
        
        
        
        
        BlacklistManager.resetAllPendingRetry(this)

        BlacklistSyncer.syncIfNeeded(this, object : BlacklistSyncer.Callback {
            override fun onSynced(count: Int, error: String?) {
                runOnUiThread { render() }
                
                BlacklistSyncer.syncPendingAsync(this@BlacklistActivity, true) {
                    runOnUiThread { render() }
                }
            }
        }, true)
    }

    private fun render() {
        val serverList = BlacklistManager.getServerList(this)
        val localList = BlacklistManager.getLocalList(this)
        val serverUids = HashSet<String>()
        for (e in serverList) serverUids.add(e.uid)

        val merged = ArrayList<BlacklistManager.Entry>()
        merged.addAll(serverList)
        for (e in localList) {
            if (!serverUids.contains(e.uid)) merged.add(e)
        }

        adapter.reload(merged)

        val pendingCount = localList.count { it.syncState == "pending" }
        val failedCount = localList.count { it.syncState == "failed" }
        val extra = buildString {
            if (pendingCount > 0) append(" · 待同步 " + pendingCount)
            if (failedCount > 0) append(" · 失败 " + failedCount)
        }

        tvCount.text = "共 " + merged.size + " 人（个人 " + localList.size +
                " · 服务端 " + serverList.size + extra + "）长按此处清空"
    }

    private fun dp(v: Float): Int {
        return (resources.displayMetrics.density * v + 0.5f).toInt()
    }

    
    private inner class EntryAdapter(c: Context) : ArrayAdapter<BlacklistManager.Entry>(c, 0) {
        fun reload(data: MutableList<BlacklistManager.Entry>) {
            clear()
            addAll(data)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, cv: View?, parent: ViewGroup): View {
            var view = cv
            val h: ViewHolder
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.item_blacklist_entry, parent, false)
                h = ViewHolder()
                h.name = view.findViewById(R.id.tv_bl_name)
                h.meta = view.findViewById(R.id.tv_bl_meta)
                h.remove = view.findViewById(R.id.btn_bl_remove)
                h.avatar = view.findViewById(R.id.iv_bl_avatar)
                view.tag = h
            } else {
                h = view.tag as ViewHolder
            }
            val e = getItem(position)
            if (e == null) return view
            h.name!!.text = if (!TextUtils.isEmpty(e.user)) e.user else "UID: " + e.uid
            val src = if ("server" == e.source) "服务端" else "个人"
            val time = if (e.time > 0) SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(e.time)) else ""
            val stateTag = when (e.syncState) {
                "pending" -> " · 待同步"
                "failed" -> " · 同步失败"
                else -> ""
            }
            h.meta!!.text = "UID " + e.uid + " · " + src + stateTag + (if (time.isEmpty()) "" else " · 拉黑于 " + time)
            h.remove!!.setOnClickListener {
                if ("server" == e.source) {
                    
                    BlacklistManager.removeServer(context, e.uid)
                    java.lang.Thread {
                        try {
                            BlacklistSyncer.removeFromServer(e.uid)
                        } catch (_: Exception) {
                        }
                    }.start()
                } else {
                    
                    BlacklistManager.removeLocal(context, e.uid)
                }
                render()
            }
            
            Glide.with(this@BlacklistActivity)
                .load("https://bbs.binmt.cc/uc_server/avatar.php?uid=" + e.uid + "&size=middle")
                .placeholder(R.drawable.ic_account)
                .error(R.drawable.ic_account)
                .circleCrop()
                .into(h.avatar!!)
            
            view.setOnClickListener {
                val it = Intent(this@BlacklistActivity, UserProfileActivity::class.java)
                it.putExtra("uid", e.uid)
                it.putExtra("username", if (!TextUtils.isEmpty(e.user)) e.user else "UID: " + e.uid)
                startActivity(it)
            }
            return view
        }
    }

    private class ViewHolder {
        var name: TextView? = null
        var meta: TextView? = null
        var remove: TextView? = null
        var avatar: android.widget.ImageView? = null
    }
}
