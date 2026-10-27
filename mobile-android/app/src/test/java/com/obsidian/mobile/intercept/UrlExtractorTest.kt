package com.obsidian.mobile.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun testExtractStandardHttpsUrl() {
        val message = "Please check your account at https://secure-login-verify.co/kyc-update immediately."
        val urls = UrlExtractor.extractUrls(message)
        assertEquals(1, urls.size)
        assertEquals("https://secure-login-verify.co/kyc-update", urls[0])
    }

    @Test
    fun testExtractUrlWithoutScheme() {
        val message = "Your parcel delivery updates are at bit.ly/3xQa9"
        val urls = UrlExtractor.extractUrls(message)
        assertEquals(1, urls.size)
        assertEquals("https://bit.ly/3xQa9", urls[0])
    }

    @Test
    fun testExtractMultipleDistinctUrls() {
        val message = "Compare https://example.com and https://test.org/path?query=1"
        val urls = UrlExtractor.extractUrls(message)
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("https://test.org/path?query=1"))
    }

    @Test
    fun testExtractDomain() {
        val domain1 = UrlExtractor.extractDomain("https://secure-login-verify.co/kyc-update")
        assertEquals("secure-login-verify.co", domain1)

        val domain2 = UrlExtractor.extractDomain("https://www.youtube.com/watch?v=123")
        assertEquals("youtube.com", domain2)

        val domain3 = UrlExtractor.extractDomain("bit.ly/3xQa9")
        assertEquals("bit.ly", domain3)
    }

    @Test
    fun testEmptyOrNoUrlMessage() {
        val message = "Hey, are we still meeting today at 5 PM?"
        val urls = UrlExtractor.extractUrls(message)
        assertTrue(urls.isEmpty())
    }
}
