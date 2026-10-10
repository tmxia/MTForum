package com.solosu.mtforum.network

import com.solosu.mtforum.util.AiLog

import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response













object RateLimiter {

    
    @JvmStatic
    var observeOnly: Boolean = false

    
    @JvmStatic
    var minIntervalMs: Long = 500L  

    
    @JvmStatic
    var readBurstCapacity: Int = 3

    
    @JvmStatic
    var readRefillMs: Long = 150L

    
    @JvmStatic
    var writeMinIntervalMs: Long = 1000L

    
    @JvmStatic
    var maxConcurrent: Int = 2

    
    @JvmStatic
    var maxConcurrentPerHost: Int = 2

    
    @JvmStatic
    var circuitThreshold: Int = 12

    
    @JvmStatic
    var blockWindowMs: Long = 5 * 60 * 1000L

    
    @JvmStatic
    var circuitOpenMs: Long = 60 * 1000L

    
    @JvmStatic
    var maxCircuitOpenMs: Long = 15 * 60 * 1000L

    private const val STATS_WINDOW_MS = 5 * 60 * 1000L

    private val globalConcurrent = AtomicInteger(0)
    private val hostConcurrent = ConcurrentHashMap<String, AtomicInteger>()

    private val lastStartAt = AtomicLong(0L)
    private val lastWriteAt = AtomicLong(0L)
    private val writeLock = Any()

    
    private class TokenBucket(
        private val capacity: Int,
        private val refillMs: Long,
    ) {
        private var tokens = capacity
        private var lastRefillAt = System.currentTimeMillis()
        private val lock = Any()

        fun acquire() {
            while (true) {
                var wait = 0L
                synchronized(lock) {
                    refill()
                    if (tokens > 0) {
                        tokens--
                        return
                    }
                    wait = refillMs - (System.currentTimeMillis() - lastRefillAt)
                }
                if (wait > 0) {
                    try { Thread.sleep(wait) } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        }

        private fun refill() {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefillAt
            if (elapsed >= refillMs) {
                val add = (elapsed / refillMs).toInt()
                tokens = minOf(capacity, tokens + add)
                lastRefillAt = now
            }
        }
    }

    private val readBucket = TokenBucket(readBurstCapacity, readRefillMs)


    private val startLock = Any()

    private val circuitOpenUntil = AtomicLong(0L)
    private val lastWriteStartAt = AtomicLong(0L)
    private const val WRITE_INTERVAL_DURING_CIRCUIT = 3000L  
    private val blockLock = Any()
    private val blockedTimes = ArrayDeque<Long>()

    
    private val consecutiveOpens = AtomicInteger(0)

    
    private val probeGate = AtomicBoolean(false)

    private val statsLock = Any()
    private var windowStartAt = 0L
    private var windowTotal = 0L
    private var windowBlocked = 0L

    private fun counterFor(host: String): AtomicInteger {
        var c = hostConcurrent[host]
        if (c == null) {
            c = AtomicInteger(0)
            val prev = hostConcurrent.putIfAbsent(host, c)
            if (prev != null) c = prev
        }
        return c
    }

    
    private val circuitHost: String by lazy {
        HttpClient.BASE_URL.toHttpUrlOrNull()?.host ?: ""
    }

    
    @JvmStatic
    fun isCircuitHost(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return host == circuitHost
    }

    
    @JvmStatic
    fun circuitRemainingMs(): Long {
        val remain = circuitOpenUntil.get() - System.currentTimeMillis()
        return if (remain > 0) remain else 0L
    }

    




    



    @JvmStatic
    @JvmOverloads
    fun reportChallenge(url: String, isWrite: Boolean = false) {
        
        if (!isWrite) {
            AiLog.i("ratelimit", "读操作 WAF 挑战（不熔断）url=" + url)
            return
        }
        reportBlocked("waf_challenge_write:" + (url.toHttpUrlOrNull()?.host ?: "?"))
    }

    @JvmStatic
    fun reportBlocked(reason: String) {
        var openMs = 0L
        var opens = 0
        synchronized(blockLock) {
            val now = System.currentTimeMillis()
            blockedTimes.addLast(now)
            while (blockedTimes.isNotEmpty() && now - blockedTimes.first() > blockWindowMs) {
                blockedTimes.removeFirst()
            }
            
            
            val stillBlocked = consecutiveOpens.get() > 0
            if (blockedTimes.size >= circuitThreshold || stillBlocked) {
                blockedTimes.clear()
                opens = consecutiveOpens.incrementAndGet()
                var window = circuitOpenMs
                var step = 1
                while (step < opens && window < maxCircuitOpenMs) {
                    window = window shl 1
                    step++
                }
                if (window > maxCircuitOpenMs) window = maxCircuitOpenMs
                circuitOpenUntil.set(now + window)
                openMs = window
            }
        }
        if (openMs > 0) {
            AiLog.e(
                "ratelimit",
                "拦截信号频繁（" + reason + "），熔断 " + (openMs / 1000) + " 秒内暂停站点出站请求（第 " +
                        opens + " 次）"
            )
        } else {
            AiLog.e("ratelimit", "疑似被拦截（" + reason + "）")
        }
    }

    
    private fun onHealthyResponse() {
        if (consecutiveOpens.get() != 0) consecutiveOpens.set(0)
    }

    private fun throttle(isWrite: Boolean) {
        if (isWrite) {
            
            synchronized(writeLock) {
                val wait = lastWriteAt.get() + writeMinIntervalMs - System.currentTimeMillis()
                if (wait > 0) {
                    try {
                        Thread.sleep(wait)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                lastWriteAt.set(System.currentTimeMillis())
            }
        } else {
            
            readBucket.acquire()
        }
    }

    private fun acquire(counter: AtomicInteger, max: Int) {
        while (true) {
            val cur = counter.get()
            if (cur < max && counter.compareAndSet(cur, cur + 1)) return
            try {
                Thread.sleep(15)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("请求排队被中断")
            }
        }
    }

    private fun recordStats(code: Int) {
        val blocked = code == 403 || code == 429 || code == 503
        synchronized(statsLock) {
            val now = System.currentTimeMillis()
            if (windowStartAt == 0L) windowStartAt = now
            windowTotal++
            if (blocked) windowBlocked++
            if (now - windowStartAt >= STATS_WINDOW_MS) {
                AiLog.i(
                    "ratelimit",
                    "近 " + ((now - windowStartAt) / 1000) + " 秒出站请求 " + windowTotal +
                            " 次，被拦截 " + windowBlocked + " 次"
                )
                windowStartAt = now
                windowTotal = 0
                windowBlocked = 0
            }
        }
    }

    
    @JvmStatic
    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val host = request.url.host
        val isTargetHost = host == circuitHost

        if (observeOnly) {
            val response = chain.proceed(request)
            recordStats(response.code)
            return@Interceptor response
        }

        
            var probing = false
            val isWriteRequest = request.method == "POST" || request.method == "PUT" || request.method == "DELETE"
            if (isTargetHost) {
                val remain = circuitRemainingMs()
                if (remain > 0) {
                    if (isWriteRequest) {
                        throw IOException("站点风控冷却中，写操作暂停 " + (remain / 1000 + 1) + " 秒")
                    }
                    val lastWrite = lastWriteStartAt.get()
                    val nowMs = System.currentTimeMillis()
                    val waitMs = WRITE_INTERVAL_DURING_CIRCUIT - (nowMs - lastWrite)
                    if (waitMs > 0) {
                        try { Thread.sleep(waitMs) } catch (_: InterruptedException) {}
                    }
                    lastWriteStartAt.set(System.currentTimeMillis())
                } else if (consecutiveOpens.get() > 0) {
                    if (!probeGate.compareAndSet(false, true)) {
                        if (isWriteRequest) {
                            throw IOException("风控恢复试探中，请稍候")
                        }
                    } else {
                        probing = true
                    }
                }
            }
        acquire(globalConcurrent, maxConcurrent)
        var hostCounter: AtomicInteger? = null
        var hostAcquired = false
        try {
            hostCounter = counterFor(host)
            acquire(hostCounter, maxConcurrentPerHost)
            hostAcquired = true
            throttle(isWriteRequest)
            val response: Response = chain.proceed(request)
            recordStats(response.code)
            if (isTargetHost) {
                if (response.code == 403 || response.code == 429 || response.code == 503) {
                    reportBlocked("HTTP " + response.code)
                } else {
                    onHealthyResponse()
                }
            }
            return@Interceptor response
        } finally {
            if (probing) probeGate.set(false)
            if (hostAcquired && hostCounter != null) hostCounter.decrementAndGet()
            globalConcurrent.decrementAndGet()
        }
    }
}
