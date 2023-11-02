/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.execution

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionResult
import com.h0tk3y.kotlin.staticObjectNotation.analysis.Resolver
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaTypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.analysis.defaultCodeResolver
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.DefaultLanguageTreeBuilder
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.Element
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.FailingResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeBuilderWithTopLevelBlock
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.parseToAst
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTracer
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect
import org.antlr.v4.kotlinruntime.misc.ParseCancellationException
import org.gradle.kotlin.dsl.execution.SchemaUtils.SchemaKeys.applyProperty
import org.gradle.kotlin.dsl.execution.SchemaUtils.SchemaKeys.genericPluginType
import org.gradle.kotlin.dsl.execution.SchemaUtils.SchemaKeys.idProperty
import org.gradle.kotlin.dsl.execution.SchemaUtils.SchemaKeys.kotlinPluginType
import org.gradle.kotlin.dsl.execution.SchemaUtils.SchemaKeys.pluginsProperty
import org.gradle.kotlin.dsl.execution.SchemaUtils.SchemaKeys.versionProperty
import org.gradle.kotlin.dsl.execution.SchemaUtils.asObject
import org.gradle.kotlin.dsl.execution.SchemaUtils.resolve
import kotlin.reflect.KClass

internal
fun tryInterpretRestrictedPluginsBlock(program: Program.Plugins): PluginsBlockInterpretation? {
    val restrictedTree = try {
        restrictedLanguageTree(program.fragment.identifierString)
    } catch (parseCancellationException: ParseCancellationException) {
        return null
    }
    if (restrictedTree.results.none { it is FailingResult }) {
        val resultFromRestrictedKotlin = pluginsBlockAnalysisSchema.resolve(restrictedTree)
        if (resultFromRestrictedKotlin.errors.isEmpty()) {
            val reflection = reflectResult(resultFromRestrictedKotlin)
            val interpretation = pluginSpecsFromReflection(reflection)
            if (interpretation != null) {
                return interpretation
            }
        }
    }
    return null
}


private
fun restrictedLanguageTree(code: String): LanguageTreeResult {
    val ast = parseToAst(code)
    val languageBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())
    val tree = languageBuilder.build(ast.single())
    return tree
}

private fun reflectResult(resultFromRestrictedKotlin: ResolutionResult): ObjectReflection {
    val assignmentTrace = AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(resultFromRestrictedKotlin)
    val reflectionContext = ReflectionContext(SchemaTypeRefContext(pluginsBlockAnalysisSchema), resultFromRestrictedKotlin, assignmentTrace)
    return reflect(resultFromRestrictedKotlin.topLevelReceiver, reflectionContext)
}

private fun pluginSpecsFromReflection(reflection: ObjectReflection): PluginsBlockInterpretation? {
    val plugins = reflection.asObject().properties[pluginsProperty]?.asObject() ?: return null
    val addedPlugins = plugins.addedObjects
    val specs = addedPlugins.map { addedObject ->
        val id = addedObject.properties[idProperty].constantOrDefault { error("expected id to be present") } as? String ?: return null
        val version = (addedObject.properties[versionProperty].constantOrDefault { null } as? String?)
        val apply = addedObject.properties[applyProperty].constantOrDefault { null } as? Boolean
        when (addedObject.type) {
            genericPluginType -> {
                apply?.let { ResidualProgram.PluginRequestSpec(id, version, apply) } ?: ResidualProgram.PluginRequestSpec(id, version)
            }
            kotlinPluginType -> {
                val kotlinId = "org.jetbrains.kotlin.$id"
                apply?.let { ResidualProgram.PluginRequestSpec(kotlinId, version, apply) } ?: ResidualProgram.PluginRequestSpec(kotlinId, version)
            }
            else -> error("unexpected plugin type")
        }
    }
    return PluginsBlockInterpretation.Static(specs)
}

private fun ObjectReflection?.constantOrDefault(default: () -> Any?) = when (this) {
    is ObjectReflection.ConstantValue -> value
    is ObjectReflection.DefaultValue -> default()
    else -> error("unexpected non-constant and non-default reflection $this")
}

private
object SchemaUtils {
    object SchemaKeys {
        val pluginsProperty = pluginsBlockAnalysisSchema.schemaClass(TopLevelReceiver::class).properties.first { it.name == "plugins" }
        val idProperty = pluginsBlockAnalysisSchema.schemaClass(RestrictedPluginDependencySpec::class).properties.first { it.name == "id" }
        val versionProperty = pluginsBlockAnalysisSchema.schemaClass(RestrictedPluginDependencySpec::class).properties.first { it.name == "version" }
        val applyProperty = pluginsBlockAnalysisSchema.schemaClass(RestrictedPluginDependencySpec::class).properties.first { it.name == "apply" }
        val genericPluginType = pluginsBlockAnalysisSchema.schemaClass(RestrictedPluginDependencySpec::class)
        val kotlinPluginType = pluginsBlockAnalysisSchema.schemaClass(KotlinRestrictedPluginDependencySpec::class)
    }

    fun AnalysisSchema.schemaClass(kClass: KClass<*>) = dataClassesByFqName[FqName.parse(kClass.qualifiedName!!)]!!
    fun ObjectReflection.asClass() = type as DataType.DataClass<*>
    fun ObjectReflection.asObject() = this as ObjectReflection.DataObjectReflection

    fun AnalysisSchema.resolve(languageTreeResult: LanguageTreeResult): ResolutionResult {
        val resolver: Resolver = defaultCodeResolver()
        val languageElements = languageTreeResult.results.filterIsInstance<Element<*>>().map { it.element }

        return resolver.resolve(this, languageElements)
    }
}
