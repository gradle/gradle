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

package org.gradle.kotlin.dsl.provider

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
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
import kotlinx.ast.common.ast.Ast
import org.antlr.v4.kotlinruntime.misc.ParseCancellationException
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.FailuresInLanguageTree
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.FailuresInResolution
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoLanguageTree
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoParseResult
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoSchemaAvailable
import org.gradle.kotlin.dsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.UnassignedValuesUsed

internal
interface RestrictedKotlinScriptEvaluator {
    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        targetScope: ClassLoaderScope,
    ): EvaluationResult

    sealed interface EvaluationResult {
        object Evaluated : EvaluationResult
        class NotEvaluated(val reason: NotEvaluatedReason) : EvaluationResult {
            sealed interface NotEvaluatedReason {
                object EvaluationNotSupported : NotEvaluatedReason
                object NoSchemaAvailable : NotEvaluatedReason
                object NoLanguageTree : NotEvaluatedReason
                object NoParseResult : NotEvaluatedReason
                object FailuresInLanguageTree : NotEvaluatedReason
                object FailuresInResolution : NotEvaluatedReason
                object UnassignedValuesUsed : NotEvaluatedReason
            }
        } // TODO: make reason more structured
    }
}

internal
class NoopRestrictedKotlinScriptEvaluator : RestrictedKotlinScriptEvaluator {
    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        targetScope: ClassLoaderScope
    ): RestrictedKotlinScriptEvaluator.EvaluationResult {
        return NotEvaluated(NotEvaluated.NotEvaluatedReason.EvaluationNotSupported)
    }
}

internal
class DefaultRestrictedKotlinScriptEvaluator(
    private val schemaBuilder: RestrictedScriptSchemaBuilder
) : RestrictedKotlinScriptEvaluator {
    override fun evaluate(target: Any, scriptSource: ScriptSource, targetScope: ClassLoaderScope): RestrictedKotlinScriptEvaluator.EvaluationResult {
        // We need to lock the scope here: we don't really need it now, but downstream scopes will rely on us locking it
        // TODO: when the scope is used, this call should be removed
        targetScope.lock()

        return when (val schema = schemaBuilder.getAnalysisSchemaForScript(target, targetScope, scriptContextFor(target))) {
            ScriptSchemaBuildingResult.SchemaNotBuilt -> NotEvaluated(NoSchemaAvailable)

            is ScriptSchemaBuildingResult.SchemaAvailable -> {
                evaluateWithSchema(schema.schema, scriptSource, target)
            }
        }
    }

    private fun evaluateWithSchema(schema: AnalysisSchema, scriptSource: ScriptSource, target: Any): RestrictedKotlinScriptEvaluator.EvaluationResult {
        val resolver = defaultCodeResolver()
        val ast = astFromScript(scriptSource).singleOrNull()
            ?: return NotEvaluated(NoParseResult)
        val languageModel = languageModelFromAst(ast)
            ?: return NotEvaluated(NoLanguageTree)
        val failures = languageModel.results.filterIsInstance<FailingResult>()
        if (failures.isNotEmpty()) {
            return NotEvaluated(FailuresInLanguageTree)
        }
        val elements = languageModel.results.filterIsInstance<Element<*>>().map { it.element }
        val resolution = resolver.resolve(schema, elements)
        if (resolution.errors.isNotEmpty()) {
            return NotEvaluated(FailuresInResolution)
        }

        val trace = assignmentTrace(resolution)
        if (trace.elements.any { it is AssignmentTraceElement.UnassignedValueUsed }) {
            return NotEvaluated(UnassignedValuesUsed)
        }
        val context = ReflectionContext(SchemaTypeRefContext(schema), resolution, trace)
        val topLevel = reflect(resolution.topLevelReceiver, context)

        RestrictedReflectionToObjectConverter(emptyMap(), SchemaTypeRefContext(schema), target).apply(topLevel)
        return RestrictedKotlinScriptEvaluator.EvaluationResult.Evaluated
    }

    private fun assignmentTrace(result: ResolutionResult) =
        AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(result)

    private fun astFromScript(scriptSource: ScriptSource): List<Ast> =
        try {
            parseToAst(scriptSource.resource.text)
        } catch (e: ParseCancellationException) {
            emptyList()
        }

    private fun languageModelFromAst(ast: Ast): LanguageTreeResult? {
        return languageTreeBuilder.build(ast)
    }

    private val languageTreeBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())

    private fun scriptContextFor(target: Any) = when (target) {
        is Settings -> RestrictedScriptContext.SettingsScript
        else -> RestrictedScriptContext.UnknownScript
    }
}

