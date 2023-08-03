package org.gradle.kotlin.dsl

import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.resource.StringTextResource
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class PluginDependenciesSpecScopeTest {

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

    @Test
    fun `given gradle-enterprise plugin accessor, it should create a single request matching the auto-applied plugin version`() {
        expecting(plugin(id = "com.gradle.enterprise", version = AutoAppliedGradleEnterprisePlugin.VERSION)) {
            `gradle-enterprise`
        }
    }

    @Test
    fun `given gradle-enterprise plugin accessor with version, it should create a single request with given version`() {
        expecting(plugin(id = "com.gradle.enterprise", version = "1.7.1")) {
            `gradle-enterprise` version "1.7.1"
        }
    }

    @Test
    fun `given kotlin plugin accessor, it should create a single request with no version`() {
        expecting(plugin(id = "org.jetbrains.kotlin.jvm", version = null)) {
            kotlin("jvm")
        }
    }

    @Test
    fun `given kotlin plugin accessor with version, it should create a single request with given version`() {
        expecting(plugin(id = "org.jetbrains.kotlin.jvm", version = "1.1.1")) {
            kotlin("jvm") version "1.1.1"
        }
    }

    @Test
    fun `given embedded kotlin plugin accessor, it should create a single request with embedded version`() {
        expecting(plugin(id = "org.jetbrains.kotlin.jvm", version = embeddedKotlinVersion)) {
            embeddedKotlin("jvm")
        }
    }
}


fun expecting(vararg expected: Plugin, block: PluginDependenciesSpec.() -> Unit) {
    assertThat(
        plugins(block).map { Plugin(it.id.id, it.version, it.isApply) },
        equalTo(expected.asList())
    )
}


fun plugins(block: PluginDependenciesSpecScope.() -> Unit): List<PluginRequestInternal> =
    PluginRequestCollector(
        TextResourceScriptSource(StringTextResource("script", ""))
    ).run {
        PluginDependenciesSpecScope(createSpec(1)).block()
        pluginRequests.toList()
    }


fun plugin(id: String, version: String? = null, isApply: Boolean = true) = Plugin(id, version, isApply)


data class Plugin(val id: String, val version: String?, val isApply: Boolean)
