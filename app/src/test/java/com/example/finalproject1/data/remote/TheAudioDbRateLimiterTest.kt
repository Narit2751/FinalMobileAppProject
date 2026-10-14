package com.example.finalproject1.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TheAudioDbRateLimiterTest {
    @Test
    fun `keeps requests below the documented free limit`() = runTest {
        var now = 5_000L
        val sleeps = mutableListOf<Long>()
        val starts = mutableListOf<Long>()
        val limiter = TheAudioDbRateLimiter(
            minIntervalMillis = 2_100L,
            clock = MillisClock { now },
            sleeper = CoroutineSleeper { duration -> sleeps += duration; now += duration },
        )

        limiter.run { starts += now }
        now += 100L
        limiter.run { starts += now }

        assertEquals(listOf(5_000L, 7_100L), starts)
        assertEquals(listOf(2_000L), sleeps)
    }
}
