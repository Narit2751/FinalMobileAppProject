package com.example.finalproject1.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteBandDaoTest {
    private lateinit var database: OtoNaviDatabase
    private lateinit var dao: FavoriteBandDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OtoNaviDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.favoriteBandDao()
    }

    @After fun tearDown() = database.close()

    @Test fun insertUpdateDeleteAndObserve() = runTest {
        val row = FavoriteBandEntity("id", "Band", "JP", "rock", null, null, 1)
        dao.upsert(row)
        assertEquals("Band", dao.observeAll().first().single().name)

        dao.deleteById("id")
        assertNull(dao.getById("id"))
        assertEquals(emptyList<FavoriteBandEntity>(), dao.observeAll().first())
    }
}
