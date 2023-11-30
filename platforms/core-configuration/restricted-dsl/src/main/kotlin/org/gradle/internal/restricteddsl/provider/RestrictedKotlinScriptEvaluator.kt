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

package org.gradle.internal.restricteddsl.provider

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionError
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionResult
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaTypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.analysis.defaultCodeResolver
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.DefaultLanguageTreeBuilder
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.Element
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.FailingResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeBuilderWithTopLevelBlock
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.LanguageTreeResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.parseToAst
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedReflectionToObjectConverter
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTraceElement
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTracer
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.kotlinFunctionAsConfigureLambda
import kotlinx.ast.common.ast.Ast
import org.antlr.v4.kotlinruntime.misc.ParseCancellationException
import org.gradle.api.initialization.Settings
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.restricteddsl.plugins.RuntimeTopLevelPluginsReceiver
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.FailuresInLanguageTree
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.FailuresInResolution
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoParseResult
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoSchemaAvailable
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.UnassignedValuesUsed


interface RestrictedKotlinScriptEvaluator {
    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
    ): EvaluationResult

    sealed interface EvaluationResult {
        object Evaluated : EvaluationResult
        class NotEvaluated(val reason: NotEvaluatedReason) : EvaluationResult {
            sealed interface NotEvaluatedReason {
                data class NoSchemaAvailable(val target: Any) : NotEvaluatedReason
                object NoParseResult : NotEvaluatedReason
                data class FailuresInLanguageTree(val failures: List<FailingResult>) : NotEvaluatedReason
                data class FailuresInResolution(val errors: List<ResolutionError>) : NotEvaluatedReason
                data class UnassignedValuesUsed(val usages: List<AssignmentTraceElement.UnassignedValueUsed>) : NotEvaluatedReason
            }
        } // TODO: make reason more structured
    }
}


/**
 * A default implementation of a restricted DSL script evaluator, for use when no additional information needs to be provided at the use site.
 * TODO: The consumers should get an instance properly injected instead.
 */
val defaultRestrictedKotlinScriptEvaluator: RestrictedKotlinScriptEvaluator by lazy {
    DefaultRestrictedKotlinScriptEvaluator(DefaultRestrictedScriptSchemaBuilder())
}


internal
class DefaultRestrictedKotlinScriptEvaluator(
    private val schemaBuilder: RestrictedScriptSchemaBuilder
) : RestrictedKotlinScriptEvaluator {
    override fun evaluate(target: Any, scriptSource: ScriptSource): RestrictedKotlinScriptEvaluator.EvaluationResult {
        return when (val schema = schemaBuilder.getAnalysisSchemaForScript(target, scriptContextFor(target))) {
            ScriptSchemaBuildingResult.SchemaNotBuilt -> NotEvaluated(NoSchemaAvailable(target))

            is ScriptSchemaBuildingResult.SchemaAvailable -> {
                evaluateWithSchema(schema.schema, scriptSource, target)
            }
        }
    }

    private
    fun evaluateWithSchema(schema: AnalysisSchema, scriptSource: ScriptSource, target: Any): RestrictedKotlinScriptEvaluator.EvaluationResult {
        val resolver = defaultCodeResolver()
        val ast = astFromScript(scriptSource).singleOrNull()
            ?: return NotEvaluated(NoParseResult)
        val languageModel = languageModelFromAst(ast)
        val failures = languageModel.results.filterIsInstance<FailingResult>()
        if (failures.isNotEmpty()) {
            return NotEvaluated(FailuresInLanguageTree(failures))
        }
        val elements = languageModel.results.filterIsInstance<Element<*>>().map { it.element }
        val resolution = resolver.resolve(schema, elements)
        if (resolution.errors.isNotEmpty()) {
            return NotEvaluated(FailuresInResolution(resolution.errors))
        }

        val trace = assignmentTrace(resolution)
        val unassignedValueUsages = trace.elements.filterIsInstance<AssignmentTraceElement.UnassignedValueUsed>()
        if (unassignedValueUsages.isNotEmpty()) {
            return NotEvaluated(UnassignedValuesUsed(unassignedValueUsages))
        }
        val context = ReflectionContext(SchemaTypeRefContext(schema), resolution, trace)
        val topLevel = reflect(resolution.topLevelReceiver, context)

        RestrictedReflectionToObjectConverter(emptyMap(), target, kotlinFunctionAsConfigureLambda).apply(topLevel)
        return RestrictedKotlinScriptEvaluator.EvaluationResult.Evaluated
    }

    private
    fun assignmentTrace(result: ResolutionResult) =
        AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(result)

    private
    fun astFromScript(scriptSource: ScriptSource): List<Ast> =
        try {
            parseToAst(scriptSource.resource.text)
        } catch (e: ParseCancellationException) {
            emptyList()
        }

    private
    fun languageModelFromAst(ast: Ast): LanguageTreeResult =
        languageTreeBuilder.build(ast)

    private
    val languageTreeBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())

    private
    fun scriptContextFor(target: Any) = when (target) {
        is Settings -> RestrictedScriptContext.SettingsScript
        is RuntimeTopLevelPluginsReceiver -> RestrictedScriptContext.PluginsBlock
        else -> RestrictedScriptContext.UnknownScript
    }
}
