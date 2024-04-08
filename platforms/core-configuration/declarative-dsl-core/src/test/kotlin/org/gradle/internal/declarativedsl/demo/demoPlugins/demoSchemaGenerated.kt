package org.gradle.internal.declarativedsl.demo.demoPlugins

import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes


fun main() {
    val schema = schemaFromTypes(
        TopLevelScope::class,
        listOf(TopLevelScope::class, PluginsBlock::class, PluginDefinition::class)
    )
    println(schema)
}
