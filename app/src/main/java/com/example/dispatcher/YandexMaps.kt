package com.example.dispatcher

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Чистые парсеры Яндекс-геокодера и Яндекс-маршрутизации.
 * Ответы API сырые; при ошибке (ключ не активирован и т.п.) — честный текст ошибки.
 */
object YandexMaps {

    fun formatGeocode(json: String, query: String): String {
        return try {
            val root = JSONObject(json)
            val members = root.optJSONObject("response")
                ?.optJSONObject("GeoObjectCollection")
                ?.optJSONArray("featureMember")
            if (members == null || members.length() == 0) {
                return "По запросу «$query» ничего не найдено."
            }
            val b = StringBuilder("Геокодинг «$query»:\n")
            val n = minOf(members.length(), 5)
            for (i in 0 until n) {
                val geo = members.optJSONObject(i)?.optJSONObject("GeoObject") ?: continue
                val name = geo.optString("name")
                val desc = geo.optString("description")
                val pos = geo.optJSONObject("Point")?.optString("pos") ?: ""
                b.append("• ").append(name)
                if (desc.isNotBlank()) b.append(" — ").append(desc)
                if (pos.isNotBlank()) b.append(" [").append(pos).append("]")
                b.append("\n")
            }
            b.trim().toString()
        } catch (e: Exception) {
            "Не удалось разобрать ответ геокодера: ${e.message}"
        }
    }

    fun formatRoute(json: String): String {
        return try {
            val root = JSONObject(json)
            val code = root.optString("code", "").ifBlank { root.optString("status", "") }
            val errs = root.optJSONArray("errors")
            val firstErr = errs?.optString(0)?.takeIf { it.isNotBlank() }
            if (code.isNotBlank() && code != "Ok") {
                return "Маршрут не построен: $code" + (firstErr?.let { ": $it" } ?: "")
            }
            if (firstErr != null) {
                return "Маршрут не построен: $firstErr"
            }
            val duration = findFieldValue(root, "duration")
            val distance = findFieldValue(root, "distance")
            if (duration == null && distance == null) return "Маршрут не найден (проверь адреса)."
            val b = StringBuilder("Маршрут: ")
            duration?.let { b.append("время ≈ ").append(fmtSeconds(it)) }
            if (duration != null && distance != null) b.append(", ")
            distance?.let { b.append("дистанция ≈ ").append(fmtMeters(it)) }
            b.toString()
        } catch (e: Exception) {
            "Не удалось разобрать ответ маршрутизатора: ${e.message}"
        }
    }

    /** Первое вхождение ключа в JSON; значение читается из поля "value" (секунды/метры) или числом. */
    fun findFieldValue(node: Any, key: String): Double? {
        return when (node) {
            is org.json.JSONObject -> {
                val o = node.optJSONObject(key)
                if (o != null) {
                    val v = o.optDouble("value", Double.NaN)
                    if (!v.isNaN()) return v
                    o.optString("text").let { if (it.isNotBlank()) return null }
                    val direct = node.optDouble(key, Double.NaN)
                    if (!direct.isNaN()) return direct
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val r = findFieldValue(node.opt(keys.next()), key)
                    if (r != null) return r
                }
                null
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    val r = findFieldValue(node.opt(i), key)
                    if (r != null) return r
                }
                null
            }
            else -> null
        }
    }

    fun fmtSeconds(s: Double): String {
        val sec = s.roundToInt()
        val m = sec / 60
        return if (m < 60) "$m мин" else "${m / 60} ч ${m % 60} мин"
    }

    fun fmtMeters(m: Double): String {
        val meters = m.roundToInt()
        return if (meters < 1000) "$meters м" else {
            val km = meters / 1000.0
            "${((km * 10).roundToInt() / 10.0)} км"
        }
    }

    /** Адрес из обратного геокодинга (GeocoderMetaData.text) или пусто. */
    fun formatReverse(json: String): String {
        return try {
            val root = JSONObject(json)
            val geo = root.optJSONObject("response")
                ?.optJSONObject("GeoObjectCollection")
                ?.optJSONArray("featureMember")
                ?.optJSONObject(0)
                ?.optJSONObject("GeoObject") ?: return ""
            val meta = geo.optJSONObject("metaDataProperty")
                ?.optJSONObject("GeocoderMetaData")
                ?.optString("text")
            val desc = geo.optString("description")
            (meta ?: desc).trim()
        } catch (e: Exception) {
            ""
        }
    }

    /** Расстояние по прямой между двумя точками (гаверсинус), км. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** Направление от первой точки ко второй, градусы от севера (0–360). */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dL = Math.toRadians(lon2 - lon1)
        val y = Math.sin(dL) * Math.cos(p2)
        val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dL)
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
    }

    /** Разбор «широта,долгота» (допускает и «долгота,широта» — по диапазонам). */
    fun parseCoords(s: String): Pair<Double, Double>? {
        val parts = s.trim().split(Regex("""[,\s]+"""))
        if (parts.size < 2) return null
        val a = parts[0].toDoubleOrNull() ?: return null
        val b = parts[1].toDoubleOrNull() ?: return null
        return if (Math.abs(a) <= 90.0 && Math.abs(b) <= 180.0) a to b else b to a
    }

    /** Сторона света по азимуту. */
    fun compassPoint(deg: Double): String = when {
        deg < 22.5 || deg >= 337.5 -> "север"
        deg < 67.5 -> "северо-восток"
        deg < 112.5 -> "восток"
        deg < 157.5 -> "юго-восток"
        deg < 202.5 -> "юг"
        deg < 247.5 -> "юго-запад"
        deg < 292.5 -> "запад"
        else -> "северо-запад"
    }

    /**
     * URL запроса к геокодеру по документации API:
     * новый /v1/ — apikey+geocode+lang+format (+sco=latlong для обратного);
     * легаси /1.x/ — apikey+format+results+geocode (+sco).
     */
    fun buildGeocodeUrl(base: String, key: String, query: String, reverse: Boolean = false, results: Int = 3): String {
        val b = base.trim().trimEnd('/')
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        return if (b.contains("/v1") && !b.contains("/1.x")) {
            val sco = if (reverse) "&sco=latlong" else ""
            val res = if (reverse) "" else "&results=$results"
            "$b/?apikey=$key&geocode=$enc&lang=ru_RU&format=json$sco$res"
        } else {
            val sco = if (reverse) "&sco=latlong" else ""
            val res = if (reverse) "" else "&results=$results"
            "$b/?apikey=$key&format=json&geocode=$enc$sco$res"
        }
    }
}
