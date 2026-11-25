package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramChannelsTest {

    private val sampleHtml = """
        <html><body><section class="tgme_channel_history js-message_history">
        <div class="tgme_widget_message_wrap js-widget_message_wrap">
          <div class="tgme_widget_message" data-post="ch/1">
            <div class="tgme_widget_message_author accent_color"><a class="tgme_widget_message_owner_name" href="https://t.me/ch"><span dir="auto">Channel</span></a></div>
            <div class="tgme_widget_message_text js-message_text" dir="auto"><i class="emoji"><b>🎉</b></i> Большой <b>анонс</b> сегодня!<br/>Подробности по ссылке <a href="https://example.com">тут</a>.</div>
            <div class="tgme_widget_message_meta">
              <span class="tgme_widget_message_views">1.2K views</span>
              <time datetime="2026-11-15T10:00:00+00:00">Nov 15 at 10:00</time>
            </div>
          </div>
        </div>
        <div class="tgme_widget_message_wrap js-widget_message_wrap">
          <div class="tgme_widget_message" data-post="ch/2">
            <div class="tgme_widget_message_text js-message_text" dir="auto">Второй пост без эмодзи.</div>
            <div class="tgme_widget_message_meta">
              <time datetime="2026-11-14T09:30:00+00:00">Nov 14 at 09:30</time>
            </div>
          </div>
        </div>
        </section></body></html>
    """.trimIndent()

    @Test
    fun parsePosts() {
        val posts = TelegramChannels.parse(sampleHtml)
        assertEquals(2, posts.size)
        assertTrue(posts[0].text.contains("Большой анонс"))
        assertTrue(!posts[0].text.contains("🎉"))
        assertTrue(!posts[0].text.contains("<b>"))
        assertTrue(posts[0].date.isNotBlank())
        assertEquals("Второй пост без эмодзи.", posts[1].text)
    }

    @Test
    fun formatPostsNames() {
        val out = TelegramChannels.formatPosts("@channel", listOf(TelegramChannels.Post("текст", "15.11")))
        assertTrue(out.contains("@channel"))
        assertTrue(out.contains("[15.11] текст"))
        assertTrue(TelegramChannels.formatPosts("@ch", emptyList()).contains("не нашлось"))
    }

    @Test
    fun emptyHtml() {
        assertEquals(0, TelegramChannels.parse("").size)
    }

    @Test
    fun normalizeChannelForms() {
        assertEquals("durov", TelegramChannels.normalizeChannel("@durov"))
        assertEquals("durov", TelegramChannels.normalizeChannel("durov"))
        assertEquals("durov", TelegramChannels.normalizeChannel("https://t.me/s/durov"))
        assertEquals("durov", TelegramChannels.normalizeChannel("t.me/durov"))
        assertEquals("durov", TelegramChannels.normalizeChannel("https://t.me/durov/"))
    }

    @Test
    fun channelsJsonRoundtrip() {
        val list = listOf("durov", "breakingmash")
        assertEquals(list, TelegramChannels.channelsFromJson(TelegramChannels.channelsToJson(list)))
        assertEquals(emptyList<String>(), TelegramChannels.channelsFromJson(null))
        assertEquals(emptyList<String>(), TelegramChannels.channelsFromJson("мусор"))
    }
}

class MailAccountsTest {

    @Test
    fun jsonRoundtrip() {
        val list = listOf(
            MailAccount(1, "imap.yandex.ru", 993, "a@yandex.com", "p1"),
            MailAccount(2, "imap.gmail.com", 993, "b@gmail.com", "p2", ssl = false)
        )
        assertEquals(list, MailAccounts.fromJson(MailAccounts.toJson(list)))
        assertEquals(emptyList<MailAccount>(), MailAccounts.fromJson(null))
        assertEquals(emptyList<MailAccount>(), MailAccounts.fromJson("мусор"))
    }

    @Test
    fun addRemoveLegacy() {
        val a = MailAccounts.add(emptyList(), "imap.yandex.ru", 993, "u", "p")
        assertEquals(1L, a.single().id)
        val b = MailAccounts.add(a, "imap.gmail.com", 993, "u2", "p2")
        assertEquals(2, b.size)
        assertEquals(1, MailAccounts.remove(b, 2L).size)
        assertEquals(1, MailAccounts.fromLegacy("imap.yandex.ru", 993, "u", "p").size)
        assertEquals(0, MailAccounts.fromLegacy("", 993, "u", "p").size)
        assertEquals(0, MailAccounts.fromLegacy("imap.yandex.ru", 993, "", "").size)
    }

    @Test
    fun proxyListParsed() {
        assertEquals(
            listOf("http://103.19.59.186:8080", "socks5://1.2.3.4:1080"),
            TelegramChannels.parseProxyList("http://103.19.59.186:8080, socks5://1.2.3.4:1080")
        )
        assertEquals(listOf("a:1", "b:2"), TelegramChannels.parseProxyList("a:1\nb:2"))
        assertEquals(emptyList<String>(), TelegramChannels.parseProxyList("  "))
    }

    @Test
    fun proxySpecParsed() {
        assertEquals(Triple("http", "103.19.59.186", 8080), TelegramChannels.parseProxy("http://103.19.59.186:8080"))
        assertEquals(Triple("socks5", "1.2.3.4", 1080), TelegramChannels.parseProxy("socks5://1.2.3.4:1080"))
        assertEquals(Triple("http", "host", 80), TelegramChannels.parseProxy("host"))
        assertEquals(null, TelegramChannels.parseProxy("host:port"))
        assertEquals(null, TelegramChannels.parseProxy(""))
    }
}
