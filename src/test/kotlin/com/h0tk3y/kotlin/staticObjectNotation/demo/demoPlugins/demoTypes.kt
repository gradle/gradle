package com.h0tk3y.kotlin.staticObjectNotation.demo.demoPlugins

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Configuring

class TopLevelScope {
    val plugins = PluginsBlock()

    @Configuring
    fun plugins(configure: PluginsBlock.() -> Unit) {
        configure(plugins)
    }
}

class PluginsBlock {
    private val pluginDefinitions = mutableListOf<PluginDefinition>()

    @Adding
    fun id(id: String): PluginDefinition =
        PluginDefinition(id).also(pluginDefinitions::add)
}

class PluginDefinition(val id: String) {
    var version: String = ""
    var apply: Boolean = false

    @Builder
    fun version(value: String): PluginDefinition =
        apply { version = value }

    @Builder
    fun apply(value: Boolean): PluginDefinition =
        apply { apply = value }
}