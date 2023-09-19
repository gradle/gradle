package demo.demoPlugins

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.Adding
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.Builder
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.Configuring

class TopLevelScope {
    val plugins = PluginsBlock()
    
    @Configuring fun plugins(configure: PluginsBlock.() -> Unit): PluginsBlock = plugins.apply(configure)
}

class PluginsBlock {
    private val pluginDefinitions = mutableListOf<PluginDefinition>()
    
    @Adding fun id(id: String) : PluginDefinition = PluginDefinition(id).also(pluginDefinitions::add)
}

class PluginDefinition(val id: String) {
    var version: String = ""
    var apply: Boolean = false
    
    @Builder fun version(value: String): PluginDefinition = apply { version = value }
    @Builder fun apply(value: Boolean): PluginDefinition = apply { apply = value }
}