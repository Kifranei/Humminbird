package com.ella.music.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseArtistBiographyTest {
    @Test fun qqMatchesSimplifiedKurakiReturnedForTheTraditionalQuery() = runBlocking {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.toString()
            val body = if (request.url.encodedPath.contains("client_search")) {
                """{"data":{"singer":{"list":[{"singerName":"仓木麻衣","singerMID":"002qS3if49uvn0"}]}}}"""
            } else "<desc><![CDATA[仓木麻衣简介]]></desc>"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        assertEquals("仓木麻衣简介", ChineseMusicMetadata.artist("倉木麻衣", AlbumInfoSource.QQ, client)?.text)
        assertEquals(2, requests.size)
        org.junit.Assert.assertTrue(requests.last().contains("singermid=002qS3if49uvn0"))
    }
    @Test fun simplifiedQueriesNeverRetryTraditionalWriting() {
        assertEquals(listOf("倉木麻衣", "仓木麻衣"), artistBiographyScriptCandidates("倉木麻衣"))
        assertEquals(listOf("仓木麻衣"), artistBiographyScriptCandidates("仓木麻衣"))
        assertEquals(emptyList<Int>(), artistBiographyCandidateIndices("仓木麻衣", listOf("倉木麻衣")))
    }
    @Test fun chineseScriptsPreferAnExactNameAndRetryTheAlternateSearchWhenMissing() = runBlocking {
        assertEquals(listOf(1, 0), artistBiographyCandidateIndices("倉木麻衣", listOf("仓木麻衣", "倉木麻衣")))
        val searches = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.contains("search")) {
                val query = request.url.queryParameter("keyword")
                searches += query
                if (query == "倉木麻衣") """{"data":{"info":[]}}"""
                else """{"data":{"info":[{"singerid":7,"singername":"仓木麻衣"}]}}"""
            } else """{"data":{"profile":"Matching biography"}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        assertEquals("Matching biography", ChineseMusicMetadata.artist("倉木麻衣", AlbumInfoSource.Kugou, client)?.text)
        assertEquals(listOf("倉木麻衣", "仓木麻衣"), searches)
    }

    @Test fun kugouPrefersLISAAndFallsBackOnlyWhenItsBiographyIsEmpty() = runBlocking {
        for (emptyExact in listOf(false, true)) {
            val ids = mutableListOf<String?>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val body = if (request.url.encodedPath.contains("search")) {
                    """{"data":{"info":[{"singerid":1,"singername":"LiSA"},{"singerid":2,"singername":"LISA"}]}}"""
                } else {
                    val id = request.url.queryParameter("singerid")
                    ids += id
                    if (id == "2" && emptyExact) """{"data":{"profile":""}}""" else """{"data":{"profile":"Biography $id"}}"""
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
            }.build()
            assertEquals(if (emptyExact) "Biography 1" else "Biography 2", ChineseMusicMetadata.artist("LISA", AlbumInfoSource.Kugou, client)?.text)
            assertEquals(if (emptyExact) listOf("2", "1") else listOf("2"), ids)
        }
    }
}
