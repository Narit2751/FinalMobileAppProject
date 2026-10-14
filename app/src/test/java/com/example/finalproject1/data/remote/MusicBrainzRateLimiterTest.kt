package com.example.finalproject1.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicBrainzRateLimiterTest {
    @Test fun `waits at least configured interval between request starts`() = runTest {
        var now = 10_000L
        val sleeps = mutableListOf<Long>()
        val starts = mutableListOf<Long>()
        val limiter = MusicBrainzRateLimiter(
            minIntervalMillis = 1_100,
            clock = MillisClock { now },
            sleeper = CoroutineSleeper { duration -> sleeps += duration; now += duration },
        )

        limiter.run { starts += now }
        now += 100
        limiter.run { starts += now }

        assertEquals(listOf(10_000L, 11_100L), starts)
        assertEquals(listOf(1_000L), sleeps)
    }
}
