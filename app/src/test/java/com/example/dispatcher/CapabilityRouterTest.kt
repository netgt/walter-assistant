package com.example.dispatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRouterTest {
    private val catalog = CapabilityRouter.parseCatalog(
        """
        [
          {"id":"health.calories","aliases":["счетчик калорий","калории","дневник питания"],"targets":[
            {"package":"app.one"},
            {"package":"app.two"}
          ]},
          {"id":"navigation.route","aliases":["маршрут"],"targets":[
            {"package":"maps.one","deepLink":"app://route","verified":true},
            {"package":"maps.two"}
          ]}
        ]
        """.trimIndent()
    )

    @Test
    fun naturalAliasFindsMainAppOnlyWhenUnambiguous() {
        val result = CapabilityRouter.resolve("где счетчик калорий", setOf("app.one"), catalog)
        assertEquals(CapabilityResolution.MainApp("health.calories", "app.one"), result)
    }

    @Test
    fun multipleCandidatesReturnAmbiguousNotRandomChoice() {
        val result = CapabilityRouter.resolve("дневник питания", setOf("app.one", "app.two"), catalog)
        assertEquals(CapabilityResolution.Ambiguous("health.calories", listOf("app.one", "app.two")), result)
    }

    @Test
    fun verifiedDeepLinkWinsOverMainApp() {
        val result = CapabilityRouter.resolve("маршрут", setOf("maps.one", "maps.two"), catalog)
        assertEquals(CapabilityResolution.DeepLink("navigation.route", "maps.one", "app://route"), result)
    }

    @Test
    fun userPreferenceAndExclusionAreApplied() {
        val preferred = RoutingRules(preferred = mapOf("health.calories" to "app.two"))
        assertEquals("health.calories", CapabilityRouter.capabilityId("калории", catalog, preferred))
        assertEquals("health.calories", CapabilityRouter.capabilityId("калории", catalog, preferred))
        assertEquals(
            CapabilityResolution.MainApp("health.calories", "app.two"),
            CapabilityRouter.resolve("калории", setOf("app.one", "app.two"), catalog, preferred)
        )
        val excluded = RoutingRules(excluded = mapOf("health.calories" to setOf("app.two")))
        assertEquals(
            CapabilityResolution.MainApp("health.calories", "app.one"),
            CapabilityRouter.resolve("калории", setOf("app.one", "app.two"), catalog, excluded)
        )
    }

    @Test
    fun customAliasAndRulesRoundtripWork() {
        val rules = RoutingRules(aliases = mapOf("моя еда" to "health.calories"))
        assertEquals("health.calories", CapabilityRouter.capabilityId("моя еда", catalog, rules))
        val restored = CapabilityRouter.parseRules(CapabilityRouter.rulesToJson(rules))
        assertEquals(rules, restored)
    }

    @Test
    fun notFoundIsHonest() {
        assertTrue(CapabilityRouter.resolve("неизвестная функция", setOf("app.one"), catalog)
            is CapabilityResolution.NotFound)
        assertTrue(CapabilityRouter.resolve("калории", emptySet(), catalog)
            is CapabilityResolution.NotFound)
    }
}
