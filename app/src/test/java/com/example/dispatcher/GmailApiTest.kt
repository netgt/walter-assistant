package com.example.dispatcher

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GmailApiTest {
    @Test
    fun multipartPayloadPrefersPlainText() {
        fun part(type: String, text: String) = JSONObject()
            .put("mimeType", type)
            .put("body", JSONObject().put("data", Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())))
        val payload = JSONObject()
            .put("mimeType", "multipart/alternative")
            .put("parts", org.json.JSONArray().put(part("text/html", "<b>Тема</b>"))
                .put(part("text/plain", "Обычный текст")))
        assertTrue(GmailApi.payloadText(payload) == "Обычный текст")
    }
}
