package org.gradle.script.lang.kotlin

import org.gradle.groovy.scripts.StringScriptSource

import org.gradle.plugin.use.internal.PluginRequest
import org.gradle.plugin.use.internal.PluginRequestCollector

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class KotlinPluginDependenciesHandlerTest {

    @Test
    fun `given a single id, it should create a single request with no version`() {
        expecting(plugin(id = "plugin-id")) {
            id("plugin-id")
        }
    }

    @Test
    fun `given a single id and apply value, it should create a single request with no version`() {
        listOf(true, false).forEach { applyValue ->
            expecting(plugin(id = "plugin-id", isApply = applyValue)) {
                id("plugin-id") apply applyValue
            }
        }
    }

    @Test
    fun `given a single id and version, it should create a single request`() {
        expecting(plugin(id = "plugin-id", version = "1.0")) {
            id("plugin-id") version "1.0"
        }
    }

    @Test
    fun `given two ids and a single version, it should create two requests`() {
        expecting(plugin(id = "plugin-a", version = "1.0"), plugin(id = "plugin-b")) {
            id("plugin-a") version "1.0"
            id("plugin-b")
        }
    }

    fun expecting(vararg expected: Plugin, block: KotlinPluginDependenciesHandler.() -> Unit) {
        assertThat(
            plugins(block).map { Plugin(it.id.asString(), it.version, it.isApply) },
            equalTo(expected.asList()))
    }

    fun plugins(block: KotlinPluginDependenciesHandler.() -> Unit): List<PluginRequest> =
        PluginRequestCollector(StringScriptSource("script", "")).run {
            KotlinPluginDependenciesHandler(createSpec(1)).block()
            listPluginRequests()
        }

    fun plugin(id: String, version: String? = null, isApply: Boolean = true) = Plugin(id, version, isApply)

    data class Plugin(val id: String, val version: String?, val isApply: Boolean)
}


