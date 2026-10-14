package com.example.finalproject1.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TheAudioDbRateLimiter(
    private val minIntervalMillis: Long = 2_100L,
    private val clock: MillisClock = MillisClock(System::currentTimeMillis),
    private val sleeper: CoroutineSleeper = CoroutineSleeper { delay(it) },
) {
    private val mutex = Mutex()
    private var lastRequestStartedAt: Long? = null

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        lastRequestStartedAt?.let { previous ->
            val waitFor = minIntervalMillis - (clock.now() - previous)
            if (waitFor > 0) sleeper.sleep(waitFor)
        }
        lastRequestStartedAt = clock.now()
        block()
    }
}
