package com.solosu.mtforum.ui.widget

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window

import com.google.android.material.bottomsheet.BottomSheetDialog




object DialogHelper {

    @JvmStatic
    fun applyToBottomSheet(dialog: BottomSheetDialog?, activity: Activity?) {
        if (dialog == null || activity == null) return
        dialog.setOnShowListener { d ->
            val window: Window? = dialog.window
            val parent = if (window != null) window.decorView else null
            if (parent == null) return@setOnShowListener
            parent.setBackgroundResource(android.R.color.transparent)
            FrostedGlassHelper.applyToCardViews(parent, activity)
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                sheet.background = ColorDrawable(Color.TRANSPARENT)
                sheet.clipToOutline = false
            }
        }
    }

    @JvmStatic
    fun applyToBottomSheet(dialog: BottomSheetDialog?, contentView: View?, activity: Activity?) {
        if (dialog == null || activity == null) return
        dialog.setOnShowListener { d ->
            val window: Window? = dialog.window
            val parent = if (window != null) window.decorView else null
            if (parent != null) {
                parent.setBackgroundResource(android.R.color.transparent)
                FrostedGlassHelper.applyToCardViews(parent, activity)
            }
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                sheet.background = ColorDrawable(Color.TRANSPARENT)
                sheet.clipToOutline = false
            }
            if (contentView != null) {
                FrostedGlassHelper.applyToCardViews(contentView, activity)
            }
        }
    }

    


    @JvmStatic
    fun applyToAlertDialog(dialog: android.app.Dialog?, context: Context?) {
        if (dialog == null || context == null) return

        if (dialog.isShowing) {
            applyFrostedNow(dialog, context)
        } else {
            dialog.setOnShowListener { d -> applyFrostedNow(dialog, context) }
        }
    }

    private fun applyFrostedNow(dialog: android.app.Dialog, context: Context) {
        val window: Window? = dialog.window
        if (window == null) return

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val decorView = window.decorView
        if (decorView == null) return

        FrostedGlassHelper.applyToCardViews(decorView, context)

        val isDark = isDarkMode(context)
        val density = context.resources.displayMetrics.density
        val radius = 12f * density

        
        val content: View = decorView.findViewById<View>(android.R.id.content) ?: decorView

        var root: View? = null
        if (content is ViewGroup) {
            val contentGroup = content
            if (contentGroup.childCount > 0) {
                root = contentGroup.getChildAt(0)
            }
        }
        if (root == null) root = content

        
        root.setBackground(FrostedGlassDrawable.create(context, 16f))

        
        clearChildBackgrounds(root)
    }

    private fun clearChildBackgrounds(view: View) {
        if (view !is ViewGroup) return
        val group = view
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ViewGroup) {
                child.background = ColorDrawable(Color.TRANSPARENT)
                clearChildBackgrounds(child)
            }
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode
        return (mode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
