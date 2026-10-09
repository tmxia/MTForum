package com.solosu.mtforum.ui.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.solosu.mtforum.R





object FrostedGlassHelper {

    
    @JvmStatic
    fun applyToCardViews(root: View?, context: Context?) {
        if (root == null || context == null) return
        if (root is CardView) {
            applyToCard(root, context)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyToCardViews(root.getChildAt(i), context)
            }
        }
    }

    
    @JvmStatic
    fun applyToCard(card: CardView?, context: Context?) {
        if (card == null || context == null) return
        val density = context.resources.displayMetrics.density
        val surfaceColor = ContextCompat.getColor(context, R.color.surface)
        val dividerColor = ContextCompat.getColor(context, R.color.divider)

        
        if (card.radius <= 0f || card.radius < 12f * density) {
            card.radius = 16f * density
        }

        if (card is MaterialCardView) {
            card.setCardBackgroundColor(surfaceColor)
            card.strokeColor = dividerColor
            card.strokeWidth = Math.max(1, (1f * density).toInt())
            card.cardElevation = 1f * density
        } else {
            card.setCardBackgroundColor(surfaceColor)
            card.cardElevation = 1f * density
        }
    }

    
    @JvmStatic
    fun applyToItem(root: View?, context: Context?) {
        applyToCardViews(root, context)
    }

    
    @JvmStatic
    fun setVisible(root: View?, visible: Boolean) {
        
    }
}
