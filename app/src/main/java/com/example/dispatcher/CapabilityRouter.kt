package com.example.dispatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Цель приложения для capability. Deep link используется только при verified=true. */
data class CapabilityTarget(
    val packageName: String = "",
    val label: String? = null,
    val deepLink: String? = null,
    val verified: Boolean = false
)

data class CapabilityDef(
    val id: String,
    val aliases: List<String>,
    val targets: List<CapabilityTarget>
)

/** Пользовательские правила поверх встроенного каталога. */
data class RoutingRules(
    val preferred: Map<String, String> = emptyMap(),
    val excluded: Map<String, Set<String>> = emptyMap(),
    val aliases: Map<String, String> = emptyMap()
)

sealed interface CapabilityResolution {
    data class DeepLink(val id: String, val packageName: String, val uri: String) : CapabilityResolution
    data class MainApp(val id: String, val packageName: String) : CapabilityResolution
    data class Ambiguous(val id: String, val packages: List<String>) : CapabilityResolution
    data class NotFound(val query: String) : CapabilityResolution
}

/** Детерминированный capability-роутер: модель формулирует функцию, код выбирает маршрут. */
object CapabilityRouter {
    fun parseCatalog(raw: String): List<CapabilityDef> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val aliases = o.getJSONArray("aliases")
            val targets = o.getJSONArray("targets")
            CapabilityDef(
                id = o.getString("id"),
                aliases = (0 until aliases.length()).map { aliases.getString(it) },
                targets = (0 until targets.length()).map { j ->
                    val t = targets.getJSONObject(j)
                    CapabilityTarget(
                        packageName = t.optString("package"),
                        label = t.optString("label").takeIf { it.isNotBlank() },
                        deepLink = t.optString("deepLink").takeIf { it.isNotBlank() },
                        verified = t.optBoolean("verified", false)
                    )
                }
            )
        }
    }

    fun parseRules(raw: String?): RoutingRules {
        if (raw.isNullOrBlank()) return RoutingRules()
        return runCatching {
            val o = JSONObject(raw)
            fun stringMap(key: String): Map<String, String> {
                val obj = o.optJSONObject(key) ?: return emptyMap()
                return buildMap {
                    obj.keys().forEach { k -> put(k, obj.optString(k)) }
                }
            }
            val preferred = stringMap("preferred")
            val aliases = stringMap("aliases")
            val excludedObj = o.optJSONObject("excluded")
            val excluded = buildMap<String, Set<String>> {
                excludedObj?.keys()?.forEach { k ->
                    val arr = excludedObj.optJSONArray(k)
                    if (arr != null) put(k, (0 until arr.length()).map { arr.optString(it) }.toSet())
                }
            }
            RoutingRules(preferred, excluded, aliases)
        }.getOrDefault(RoutingRules())
    }

    fun rulesToJson(rules: RoutingRules): String {
        val o = JSONObject()
        o.put("preferred", JSONObject().apply { rules.preferred.forEach { (k, v) -> put(k, v) } })
        o.put("aliases", JSONObject().apply { rules.aliases.forEach { (k, v) -> put(k, v) } })
        o.put("excluded", JSONObject().apply {
            rules.excluded.forEach { (k, values) -> put(k, JSONArray().apply { values.forEach { put(it) } }) }
        })
        return o.toString()
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    fun labelKey(text: String): String = normalize(text)

    private fun score(query: String, alias: String): Int {
        val q = normalize(query); val a = normalize(alias)
        if (q.isBlank() || a.isBlank()) return 0
        if (q == a) return 100
        if (q.startsWith(a) || q.endsWith(a)) return 90
        if (a.length >= 4 && q.contains(a)) return 82
        val qWords = q.split(' ').filter { it.length >= 3 }.toSet()
        val aWords = a.split(' ').filter { it.length >= 3 }.toSet()
        if (qWords.isNotEmpty() && qWords.all { a.contains(it) }) return 76
        if (aWords.isNotEmpty() && aWords.all { q.contains(it) }) return 68
        return 0
    }

    fun capabilityId(query: String, catalog: List<CapabilityDef>, rules: RoutingRules = RoutingRules()): String? {
        val q = normalize(query)
        rules.aliases.entries.firstOrNull { normalize(it.key) == q }?.value?.let { return it }
        val best = catalog.flatMap { def -> def.aliases.map { alias -> def.id to score(query, alias) } }
            .maxByOrNull { it.second }
        return if (best != null && best.second >= 68) best.first else null
    }

    private data class Candidate(val target: CapabilityTarget, val packageName: String)

    fun resolve(
        query: String,
        installedPackages: Set<String>,
        catalog: List<CapabilityDef>,
        rules: RoutingRules = RoutingRules(),
        installedLabels: Map<String, String> = emptyMap(),
        usageCounts: Map<String, Int> = emptyMap()
    ): CapabilityResolution {
        val id = capabilityId(query, catalog, rules) ?: return CapabilityResolution.NotFound(query)
        val def = catalog.firstOrNull { it.id == id } ?: return CapabilityResolution.NotFound(query)
        val excluded = rules.excluded[id].orEmpty()
        val candidates = def.targets.mapNotNull { target ->
            val pkg = when {
                target.packageName.isNotBlank() -> target.packageName.takeIf { it in installedPackages }
                !target.label.isNullOrBlank() -> installedLabels[labelKey(target.label)]
                else -> null
            }
            pkg?.takeIf { it !in excluded }?.let { Candidate(target, it) }
        }
        rules.preferred[id]?.let { preferred ->
            val candidate = candidates.firstOrNull { it.packageName == preferred }
                ?: return CapabilityResolution.NotFound(query)
            val target = candidate.target
            return target.takeIf { it.verified && !it.deepLink.isNullOrBlank() }
                ?.let { CapabilityResolution.DeepLink(id, candidate.packageName, it.deepLink!!) }
                ?: CapabilityResolution.MainApp(id, candidate.packageName)
        }
        if (candidates.isEmpty()) return CapabilityResolution.NotFound(query)
        val scoredTargets = candidates.map { candidate ->
            val linkScore = if (candidate.target.verified && !candidate.target.deepLink.isNullOrBlank()) 1000 else 0
            candidate to linkScore + (usageCounts[candidate.packageName] ?: 0).coerceAtMost(100)
        }
        val bestScore = scoredTargets.maxOf { it.second }
        val best = scoredTargets.filter { it.second == bestScore }.map { it.first }
        if (best.size > 1) return CapabilityResolution.Ambiguous(id, best.map { it.packageName })
        val candidate = best.single()
        val target = candidate.target
        return if (target.verified && !target.deepLink.isNullOrBlank()) {
            CapabilityResolution.DeepLink(id, candidate.packageName, target.deepLink!!)
        } else CapabilityResolution.MainApp(id, candidate.packageName)
    }
}

/** Загрузка встроенного каталога и пользовательских правил. */
object CapabilityStore {
    fun loadCatalog(ctx: Context): List<CapabilityDef> = runCatching {
        ctx.assets.open("capabilities.json").bufferedReader().use { it.readText() }
    }.getOrDefault("[]").let { raw ->
        runCatching { CapabilityRouter.parseCatalog(raw) }.getOrDefault(emptyList())
    }

    fun loadRules(prefs: android.content.SharedPreferences): RoutingRules =
        CapabilityRouter.parseRules(prefs.getString(Prefs.KEY_CAPABILITY_RULES, null))

    fun saveRules(prefs: android.content.SharedPreferences, rules: RoutingRules) {
        prefs.edit().putString(Prefs.KEY_CAPABILITY_RULES, CapabilityRouter.rulesToJson(rules)).apply()
    }
}
