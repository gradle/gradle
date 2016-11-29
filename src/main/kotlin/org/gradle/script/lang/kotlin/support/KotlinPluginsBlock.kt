package org.gradle.script.lang.kotlin.support

import org.gradle.plugin.use.PluginDependenciesSpec

abstract class KotlinPluginsBlock(val pluginDependencies: PluginDependenciesSpec) {

    inline fun plugins(configuration: PluginDependenciesSpec.() -> Unit) {
        pluginDependencies.configuration()
    }
}
