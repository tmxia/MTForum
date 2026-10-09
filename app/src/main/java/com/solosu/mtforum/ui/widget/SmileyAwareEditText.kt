package com.solosu.mtforum.ui.widget

import android.content.Context
import android.text.Editable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper











open class SmileyAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : RichTextInputEditText(context, attrs, defStyleAttr) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return SmileyInputConnection(base, this)
    }

    
    private fun deleteSmileyAtCaret(before: Boolean): Boolean {
        val editable = text ?: return false
        var start = selectionStart
        var end = selectionEnd
        if (start != end) {
            
            val selected = editable.subSequence(Math.min(start, end), Math.max(start, end)).toString()
            if (!isSmileyCode(selected)) return false
            editable.delete(Math.min(start, end), Math.max(start, end))
            return true
        }
        val caret = start
        if (caret < 0) return false
        val range = if (before) {
            smileyRangeBefore(editable, caret)
        } else {
            smileyRangeAfter(editable, caret)
        } ?: return false
        editable.delete(range.first, range.second)
        return true
    }

    
    private fun smileyRangeBefore(editable: Editable, caret: Int): Pair<Int, Int>? {
        if (caret <= 0 || editable[caret - 1] != ']') return null
        val from = editable.toString().lastIndexOf('[', caret - 1)
        if (from < 0) return null
        
        val code = editable.subSequence(from, caret).toString()
        if (!isSmileyCode(code)) return null
        return from to caret
    }

    
    private fun smileyRangeAfter(editable: Editable, caret: Int): Pair<Int, Int>? {
        if (caret >= editable.length || editable[caret] != '[') return null
        val to = editable.toString().indexOf(']', caret)
        if (to < 0) return null
        val code = editable.subSequence(caret, to + 1).toString()
        if (!isSmileyCode(code)) return null
        return caret to (to + 1)
    }

    





    private fun isSmileyCode(s: String): Boolean {
        if (s.length !in 3..16 || s[0] != '[' || s[s.length - 1] != ']') return false
        val body = s.substring(1, s.length - 1)
        if (body.isEmpty() || body.any { it == '[' || it.isWhitespace() }) return false
        if (body.any { it == '<' || it == '>' || it == '/' || it == '=' || it == '\\' }) return false
        if (RESERVED_CODES.contains(body.lowercase())) return false
        
        return body.startsWith("#") || body.any { it.code > 127 }
    }

    private companion object {
        
        private val RESERVED_CODES = hashSetOf(
            "attach", "attachimg", "quote", "free", "hide", "code", "url", "img",
            "media", "flash", "b", "i", "u", "color", "size", "font", "align",
            "table", "tr", "td", "hr", "list", "audio", "video", "sup", "sub",
            "email", "backcolor", "qq"
        )
    }

    private class SmileyInputConnection(
        target: InputConnection,
        private val host: SmileyAwareEditText
    ) : InputConnectionWrapper(target, false) {

        
        private var handledKeyCode = 0

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            
            if (beforeLength == 1 && afterLength == 0 && host.deleteSmileyAtCaret(true)) {
                return true
            }
            if (beforeLength == 0 && afterLength == 1 && host.deleteSmileyAtCaret(false)) {
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            val del = event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL
            if (del && event.action == KeyEvent.ACTION_DOWN) {
                if (host.deleteSmileyAtCaret(event.keyCode == KeyEvent.KEYCODE_DEL)) {
                    handledKeyCode = event.keyCode
                    return true
                }
            } else if (del && event.action == KeyEvent.ACTION_UP && handledKeyCode == event.keyCode) {
                handledKeyCode = 0
                return true
            }
            return super.sendKeyEvent(event)
        }
    }
}
