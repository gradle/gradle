package demo.demoPlugins

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics.StoreValueInProperty

fun demoSchema(): AnalysisSchema {
    val topLevelScopeRef = typeRef<TopLevelScope>()
    val pluginsBlockRef = typeRef<PluginsBlock>()
    val pluginDefinitionRef = typeRef<PluginDefinition>()
    
    val topLevelScopePlugins = DataProperty("plugins", pluginsBlockRef, isReadOnly = true)
    val pluginDefinitionId = DataProperty("id", string, isReadOnly = true)
    val pluginDefinitionVersion = DataProperty("version", string, isReadOnly = true)
    val pluginDefinitionApply = DataProperty("apply", boolean, isReadOnly = true)
    
    val topLevelScope = DataType.DataClass(
        TopLevelScope::class,
        properties = listOf(topLevelScopePlugins),
        memberFunctions = listOf(
            DataMemberFunction(
                topLevelScopeRef, "plugins", 
                parameters = emptyList(), 
                semantics = FunctionSemantics.AccessAndConfigure(
                    ConfigureAccessor.Property(topLevelScopeRef, topLevelScopePlugins)
                )
            )
        ),
        constructorSignatures = emptyList()
    )
    
    val pluginsBlock = DataType.DataClass(
        PluginsBlock::class,
        properties = emptyList(),
        memberFunctions = listOf(
            DataMemberFunction(
                pluginsBlockRef, "id",
                parameters = listOf(
                    DataParameter("identifier", string, isDefault = false, semantics = StoreValueInProperty(pluginDefinitionId))
                ),
                semantics = FunctionSemantics.AddAndConfigure(pluginDefinitionRef)
            )
        ),
        constructorSignatures = emptyList()
    )
    
    val pluginDefinition = DataType.DataClass(
        PluginDefinition::class,
        properties = listOf(
            pluginDefinitionId, pluginDefinitionVersion, pluginDefinitionApply
        ),
        memberFunctions = listOf(
            DataBuilderFunction(
                pluginDefinitionRef, pluginDefinitionVersion.name,
                DataParameter("newValue", string, isDefault = false, StoreValueInProperty(pluginDefinitionVersion))
            ),
            DataBuilderFunction(
                pluginDefinitionRef, pluginDefinitionApply.name,
                DataParameter("newValue", boolean, isDefault = false, StoreValueInProperty(pluginDefinitionApply))
            ),
        ),
        constructorSignatures = emptyList()
    )
    
    return AnalysisSchema(
        topLevelScope, 
        listOf(topLevelScope, pluginsBlock, pluginDefinition).associateBy { FqName.parse(it.kClass.java.name) },
        emptyMap(),
        emptyMap(),
        emptySet()
    )
}