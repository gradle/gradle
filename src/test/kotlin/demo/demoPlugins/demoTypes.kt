package demo.demoPlugins

class TopLevelScope {
    val pluginsBlock = PluginsBlock()
    
    fun plugins(configure: PluginsBlock.() -> Unit) {
        pluginsBlock.configure()
    }
}

class PluginsBlock {
    private val pluginDefinitions = mutableListOf<PluginDefinition>()
    
    fun id(identifier: String) : PluginDefinition {
        return PluginDefinition(identifier).also(pluginDefinitions::add)
    }
}

class PluginDefinition(val id: String) {
    var version: String = ""
    var apply: Boolean = false
    
    fun version(newValue: String) {
        version = newValue
    }
    
    fun apply(newValue: Boolean) {
        apply = newValue
    }
}