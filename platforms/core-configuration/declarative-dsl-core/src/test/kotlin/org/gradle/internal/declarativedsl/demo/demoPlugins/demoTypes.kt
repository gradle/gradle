package org.gradle.internal.declarativedsl.demo.demoPlugins

import org.gradle.internal.declarativedsl.Adding
import org.gradle.internal.declarativedsl.Builder
import org.gradle.internal.declarativedsl.Configuring
import org.gradle.internal.declarativedsl.HasDefaultValue
import org.gradle.internal.declarativedsl.Restricted


class TopLevelScope {
    @Restricted
    val plugins = PluginsBlock()

    @Configuring
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


class PluginDefinition(@Restricted val id: String) {
    @Restricted
    var version: String = ""

    @HasDefaultValue
    var apply: Boolean = false

    @Builder
    infix fun version(value: String): PluginDefinition =
        apply { version = value }

    @Builder
    infix fun apply(value: Boolean): PluginDefinition =
        apply { apply = value }
}
