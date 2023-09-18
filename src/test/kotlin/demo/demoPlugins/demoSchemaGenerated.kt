package demo.demoPlugins

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes

fun main() {
    val schema = schemaFromTypes(
        TopLevelScope::class,
        listOf(TopLevelScope::class, PluginsBlock::class, PluginDefinition::class)
    )
    println(schema)
}