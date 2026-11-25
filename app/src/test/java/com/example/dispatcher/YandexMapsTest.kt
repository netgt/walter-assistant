package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMapsTest {

    @Test
    fun geocodeParses() {
        val json = """
            {"response": {"GeoObjectCollection": {"featureMember": [
              {"GeoObject": {"name": "Тверская улица, 1", "description": "Москва", "Point": {"pos": "37.617635 55.755814"}}},
              {"GeoObject": {"name": "Тверская улица, 2", "description": "Москва", "Point": {"pos": "37.618000 55.756000"}}}
            ]}}}
        """.trimIndent()
        val out = YandexMaps.formatGeocode(json, "Тверская 1")
        assertTrue(out.contains("Тверская улица, 1"))
        assertTrue(out.contains("Москва"))
        assertTrue(out.contains("37.617635 55.755814"))
    }

    @Test
    fun geocodeNotFound() {
        val out = YandexMaps.formatGeocode("""{"response":{"GeoObjectCollection":{"featureMember":[]}}}""", "x")
        assertTrue(out.contains("ничего не найдено"))
    }

    @Test
    fun routeParses() {
        val json = """
            {"code": "Ok", "routes": [{"legs": [{
              "duration": {"text": "15 мин", "value": 901},
              "distance": {"text": "5.1 км", "value": 5120}
            }]}]}
        """.trimIndent()
        val out = YandexMaps.formatRoute(json)
        assertTrue(out.contains("15 мин"))
        assertTrue(out.contains("5.1 км"))
    }

    @Test
    fun routeErrorPassesThrough() {
        val out = YandexMaps.formatRoute("""{"errors":["Apikey rejected."]}""")
        assertTrue(out.contains("Apikey rejected"))
    }

    @Test
    fun fmtHelpers() {
        assertEquals("15 мин", YandexMaps.fmtSeconds(901.0))
        assertEquals("1 ч 10 мин", YandexMaps.fmtSeconds(4200.0))
        assertEquals("900 м", YandexMaps.fmtMeters(900.0))
        assertEquals("5.1 км", YandexMaps.fmtMeters(5120.0))
    }

    @Test
    fun haversineMoscowSpb() {
        val km = YandexMaps.haversineKm(55.755814, 37.617635, 59.938732, 30.316229)
        assertTrue("факт: $km", km > 600 && km < 670)
    }

    @Test
    fun bearingNorthEast() {
        val br = YandexMaps.bearingDeg(55.755814, 37.617635, 59.938732, 30.316229)
        assertTrue("азимут: $br", br > 300 && br < 330)
        assertEquals("северо-запад", YandexMaps.compassPoint(br))
        assertEquals("север", YandexMaps.compassPoint(0.0))
        assertEquals("восток", YandexMaps.compassPoint(90.0))
        assertEquals("юг", YandexMaps.compassPoint(180.0))
        assertEquals("запад", YandexMaps.compassPoint(270.0))
    }

    @Test
    fun parseCoordsBothOrders() {
        // «широта, долгота» — обычный порядок
        assertEquals(55.755814 to 37.617635, YandexMaps.parseCoords("55.755814, 37.617635"))
        // оба числа в диапазонах — сохраняем порядок как «широта, долгота»
        assertEquals(37.617635 to 55.755814, YandexMaps.parseCoords("37.617635 55.755814"))
        // явно недопустимая широта (200) — меняем местами
        assertEquals(55.0 to 200.0, YandexMaps.parseCoords("200 55"))
        assertEquals(null, YandexMaps.parseCoords("мусор"))
        assertEquals(null, YandexMaps.parseCoords("55"))
    }

    @Test
    fun reverseGeocodeParses() {
        val json = """
            {"response": {"GeoObjectCollection": {"featureMember": [
              {"GeoObject": {
                "name": "Россия, Москва, Кремль",
                "description": "Москва",
                "Point": {"pos": "37.617635 55.755814"},
                "metaDataProperty": {"GeocoderMetaData": {"text": "Россия, Москва, Красная площадь, 1"}}
              }}
            ]}}}
        """.trimIndent()
        assertEquals("Россия, Москва, Красная площадь, 1", YandexMaps.formatReverse(json))
        assertEquals("", YandexMaps.formatReverse("мусор"))
        assertEquals("", YandexMaps.formatReverse("""{"response":{"GeoObjectCollection":{"featureMember":[]}}}"""))
    }

    @Test
    fun buildGeocodeUrlV1() {
        val fwd = YandexMaps.buildGeocodeUrl("https://geocode-maps.yandex.ru/v1/", "KEY", "Москва, Кремль")
        assertTrue(fwd.contains("/v1/?apikey=KEY"))
        assertTrue(fwd.contains("geocode="))
        assertTrue(fwd.contains("lang=ru_RU"))
        assertTrue(fwd.contains("format=json"))
        assertTrue(fwd.contains("results=3"))
        assertTrue(!fwd.contains("sco"))
        val rev = YandexMaps.buildGeocodeUrl("https://geocode-maps.yandex.ru/v1/", "KEY", "55.75,37.61", reverse = true)
        assertTrue(rev.contains("sco=latlong"))
        assertTrue(!rev.contains("results"))
    }

    @Test
    fun buildGeocodeUrlLegacy() {
        val fwd = YandexMaps.buildGeocodeUrl("https://geocode-maps.yandex.ru/1.x/", "KEY", "Москва")
        assertTrue(fwd.contains("/1.x/?apikey=KEY"))
        assertTrue(fwd.contains("results=3"))
        assertTrue(!fwd.contains("lang=ru_RU"))
        val rev = YandexMaps.buildGeocodeUrl("https://geocode-maps.yandex.ru/1.x/", "KEY", "55.75,37.61", reverse = true)
        assertTrue(rev.contains("sco=latlong"))
        assertTrue(!rev.contains("results"))
    }
}
