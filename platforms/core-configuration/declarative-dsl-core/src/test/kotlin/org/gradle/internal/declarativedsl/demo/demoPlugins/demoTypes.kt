package org.gradle.internal.declarativedsl.demo.demoPlugins

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Builder
import org.gradle.declarative.dsl.model.annotations.HasDefaultValue
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition


class TopLevelScope {
    val plugins = PluginsBlock()

    @HiddenInDefinition
    fun plugins(configure: PluginsBlock.() -> Unit) {
        configure(plugins)
    }
}


class PluginsBlock {
    private
    val pluginDefinitions = mutableListOf<PluginDefinition>()

    @Adding
    fun id(id: String): PluginDefinition =
        PluginDefinition(id).also(pluginDefinitions::add)
}


class PluginDefinition(val id: String) {
    var version: String = ""

    @get:HasDefaultValue
    var apply: Boolean = false

    @Builder
    infix fun version(value: String): PluginDefinition =
        apply { version = value }

    @Builder
    infix fun apply(value: Boolean): PluginDefinition =
        apply { apply = value }
}
