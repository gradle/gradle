package org.gradle.script.lang.kotlin

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * @see PluginDependenciesSpec
 */
@BuildScriptBlockMarker
class KotlinPluginDependenciesHandler(private val plugins: PluginDependenciesSpec) {
    fun id(id: String): PluginDependencySpec = plugins.id(id)
}

infix fun PluginDependencySpec.version(version: String?): PluginDependencySpec = version(version)

infix fun PluginDependencySpec.apply(apply: Boolean): PluginDependencySpec  = apply(apply)
