package com.solosu.mtforum.network

import java.util.regex.Matcher
import java.util.regex.Pattern






















object WafChallenge {

    
    const val COOKIE_NAME = "acw_sc__v2"

    
    private val P_ARG1 =
        Pattern.compile("var\\s+arg1\\s*=\\s*['\"]([0-9A-Fa-f]+)['\"]")

    
    private val PERMUTATION = intArrayOf(
        15, 35, 29, 24, 33, 16, 1, 38, 10, 9,
        19, 31, 40, 27, 22, 23, 25, 13, 6, 11,
        39, 18, 20, 8, 14, 21, 32, 26, 2, 30,
        7, 4, 17, 5, 3, 28, 34, 37, 12, 36,
    )

    
    private const val XOR_KEY_HEX = "3000176000856006061501533003690027800375"

    









    @JvmStatic
    fun looksLikeChallenge(body: String?): Boolean {
        if (body == null || body.isEmpty()) return false
        
        if (body.length > 64 * 1024) return false
        val lower = body.lowercase()
        if (!lower.contains("<html")) return false
        if (!lower.contains("<body")) {
            
            return lower.contains("<script") || lower.contains("<meta")
        }
        
        return lower.contains("acw_sc__v2") || P_ARG1.matcher(body).find()
    }

    
    @JvmStatic
    fun extractArg1(body: String?): String? {
        if (body == null || body.isEmpty()) return null
        val m: Matcher = P_ARG1.matcher(body)
        return if (m.find()) m.group(1) else null
    }

    




    @JvmStatic
    fun solve(arg1: String?): String? {
        if (arg1 == null || arg1.isEmpty()) return null
        val src = arg1.trim()
        
        if ((src.length and 1) != 0) return null
        for (i in src.indices) {
            if (Character.digit(src[i], 16) < 0) return null
        }

        val key = XOR_KEY_HEX
        if (key.length < src.length) return null

        
        val reordered = CharArray(PERMUTATION.size)
        for (x in src.indices) {
            val ch = src[x]
            for (z in PERMUTATION.indices) {
                if (PERMUTATION[z] == x + 1) {
                    reordered[z] = ch
                }
            }
        }

        
        val out = StringBuilder(src.length)
        var i = 0
        while (i < reordered.size) {
            val a = Character.digit(reordered[i], 16)
            val b = Character.digit(reordered[i + 1], 16)
            val k1 = Character.digit(key[i], 16)
            val k2 = Character.digit(key[i + 1], 16)
            if (a < 0 || b < 0 || k1 < 0 || k2 < 0) return null
            val v = ((a shl 4) or b) xor ((k1 shl 4) or k2)
            out.append(String.format("%02x", v))
            i += 2
        }
        return out.toString()
    }

    
    @JvmStatic
    fun solveFromPage(body: String?): String? {
        val arg1 = extractArg1(body)
        if (arg1 == null || arg1.isEmpty()) return null
        return solve(arg1)
    }
}
