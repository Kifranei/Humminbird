package com.ella.music.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePagingTest {
    @Test
    fun fetchOffsetPagedResultsLoadsAllPagesUntilShortPage() = runBlocking {
        val requests = mutableListOf<Pair<Int, Int>>()

        val result = fetchOffsetPagedResults<Int>(pageSize = 3) { offset, pageSize ->
            requests += offset to pageSize
            when (offset) {
                0 -> listOf(1, 2, 3)
                3 -> listOf(4, 5, 6)
                6 -> listOf(7)
                else -> emptyList()
            }
        }

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), result)
        assertEquals(listOf(0 to 3, 3 to 3, 6 to 3), requests)
    }

    @Test
    fun fetchOffsetPagedResultsStopsAtRequestedLimit() = runBlocking {
        val requests = mutableListOf<Pair<Int, Int>>()

        val result = fetchOffsetPagedResults<Int>(limit = 5, pageSize = 3) { offset, pageSize ->
            requests += offset to pageSize
            when (offset) {
                0 -> listOf(1, 2, 3)
                3 -> listOf(4, 5, 6)
                else -> emptyList()
            }
        }

        assertEquals(listOf(1, 2, 3, 4, 5), result)
        assertEquals(listOf(0 to 3, 3 to 2), requests)
    }

    @Test
    fun normalizeRemoteFetchLimitTreatsNonPositiveAsUnlimited() {
        assertEquals(Int.MAX_VALUE, normalizeRemoteFetchLimit(0))
        assertEquals(Int.MAX_VALUE, normalizeRemoteFetchLimit(-1))
        assertEquals(42, normalizeRemoteFetchLimit(42))
    }
}
