package com.example

import com.example.data.remote.TelegramWebScraper
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramDigestUnitTest {
    @Test
    fun normalizeUsername_handlesVariousFormats() {
        assertEquals("digiato", TelegramWebScraper.normalizeUsername("digiato"))
        assertEquals("digiato", TelegramWebScraper.normalizeUsername("@digiato"))
        assertEquals("digiato", TelegramWebScraper.normalizeUsername("https://t.me/digiato"))
        assertEquals("digiato", TelegramWebScraper.normalizeUsername("t.me/s/digiato"))
        assertEquals("digiato", TelegramWebScraper.normalizeUsername("https://t.me/s/digiato/"))
        assertEquals("akharinkhabar", TelegramWebScraper.normalizeUsername("@AkharinKhabar"))
    }
}
