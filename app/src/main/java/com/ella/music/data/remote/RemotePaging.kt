package com.ella.music.data.remote

internal const val REMOTE_LIBRARY_PAGE_SIZE = 500

internal fun normalizeRemoteFetchLimit(limit: Int): Int =
    limit.takeIf { it > 0 } ?: Int.MAX_VALUE

internal suspend fun <T> fetchOffsetPagedResults(
    limit: Int = Int.MAX_VALUE,
    pageSize: Int = REMOTE_LIBRARY_PAGE_SIZE,
    fetchPage: suspend (offset: Int, pageSize: Int) -> List<T>
): List<T> {
    val targetCount = normalizeRemoteFetchLimit(limit)
    val safePageSize = pageSize.coerceAtLeast(1)
    val results = mutableListOf<T>()
    var offset = 0

    while (results.size < targetCount) {
        val requestSize = if (targetCount == Int.MAX_VALUE) {
            safePageSize
        } else {
            minOf(safePageSize, targetCount - results.size)
        }
        val page = fetchPage(offset, requestSize)
        if (page.isEmpty()) break
        results += page.take(requestSize)
        if (page.size < requestSize) break
        offset += page.size
    }

    return if (targetCount == Int.MAX_VALUE) results else results.take(targetCount)
}
