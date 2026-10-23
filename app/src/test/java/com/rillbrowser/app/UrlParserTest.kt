package com.rillbrowser.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlParserTest {
    @Test fun fullUrlIsKept() = assertEquals("https://example.com/a?b=1", UrlParser.toUrl(" https://example.com/a?b=1 "))
    @Test fun bareDomainGetsHttps() = assertEquals("https://fctv33.com/live", UrlParser.toUrl("fctv33.com/live"))
    @Test fun ipGetsHttp() = assertEquals("http://192.168.1.5:8080/x", UrlParser.toUrl("192.168.1.5:8080/x"))
    @Test fun localhostGetsHttp() = assertEquals("http://localhost:3000", UrlParser.toUrl("localhost:3000"))
    @Test fun wordsBecomeSearch() = assertEquals("https://www.google.com/search?q=funny+cats", UrlParser.toUrl("funny cats"))
    @Test fun singleWordIsSearch() = assertEquals("https://www.google.com/search?q=youtube", UrlParser.toUrl("youtube"))
    @Test fun sentenceWithDotIsSearch() =
        assertEquals("https://www.google.com/search?q=what+is+a+.com", UrlParser.toUrl("what is a .com"))
    @Test fun emptyStaysEmpty() = assertEquals("", UrlParser.toUrl("   "))
}
