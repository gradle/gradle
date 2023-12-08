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

package org.gradle.internal.restricteddsl.evaluator

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
import com.h0tk3y.kotlin.staticObjectNotation.language.AstSourceIdentifier
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.CompositeFunctionResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.CompositePropertyResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.MemberFunctionResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.ReflectionRuntimePropertyResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedReflectionToObjectConverter
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTraceElement
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTracer
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.plus
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
import kotlinx.ast.common.ast.Ast
import org.antlr.v4.kotlinruntime.misc.ParseCancellationException
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.restricteddsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.restricteddsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationContext.ScriptPluginEvaluationContext
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.FailuresInLanguageTree
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.FailuresInResolution
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.NoParseResult
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.NoSchemaAvailable
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.UnassignedValuesUsed
import org.gradle.internal.restricteddsl.plugins.RuntimeTopLevelPluginsReceiver


interface RestrictedKotlinScriptEvaluator {
    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        evaluationContext: EvaluationContext,
    ): EvaluationResult

    sealed interface EvaluationResult {
        object Evaluated : EvaluationResult
        class NotEvaluated(val stageFailures: List<StageFailure>) : EvaluationResult {
            sealed interface StageFailure {
                data class NoSchemaAvailable(val target: Any) : StageFailure
                object NoParseResult : StageFailure
                data class FailuresInLanguageTree(val failures: List<FailingResult>) : StageFailure
                data class FailuresInResolution(val errors: List<ResolutionError>) : StageFailure
                data class UnassignedValuesUsed(val usages: List<AssignmentTraceElement.UnassignedValueUsed>) : StageFailure
            }
        } // TODO: make reason more structured
    }

    sealed interface EvaluationContext {
        class ScriptPluginEvaluationContext(
            val targetScope: ClassLoaderScope
        ) : EvaluationContext

        object PluginsDslEvaluationContext : EvaluationContext
    }
}


/**
 * A default implementation of a restricted DSL script evaluator, for use when no additional information needs to be provided at the use site.
 * TODO: The consumers should get an instance properly injected instead.
 */
val defaultRestrictedKotlinScriptEvaluator: RestrictedKotlinScriptEvaluator by lazy {
    DefaultRestrictedKotlinScriptEvaluator(DefaultInterpretationSchemaBuilder())
}


internal
class DefaultRestrictedKotlinScriptEvaluator(
    private val schemaBuilder: InterpretationSchemaBuilder
) : RestrictedKotlinScriptEvaluator {
    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        evaluationContext: RestrictedKotlinScriptEvaluator.EvaluationContext
    ): RestrictedKotlinScriptEvaluator.EvaluationResult {
        return when (val built = schemaBuilder.getEvaluationSchemaForScript(target, scriptContextFor(target, scriptSource, evaluationContext))) {
            InterpretationSchemaBuildingResult.SchemaNotBuilt -> NotEvaluated(listOf(NoSchemaAvailable(target)))
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> runInterpretationSequence(scriptSource, built.sequence)
        }
    }

    private
    fun runInterpretationSequence(
        scriptSource: ScriptSource,
        sequence: InterpretationSequence
    ): RestrictedKotlinScriptEvaluator.EvaluationResult {
        sequence.steps.forEach { step ->
            val result = runInterpretationSequenceStep(scriptSource, step)
            if (result is NotEvaluated) {
                return result
            }
        }
        return RestrictedKotlinScriptEvaluator.EvaluationResult.Evaluated
    }

    private
    fun <R : Any> runInterpretationSequenceStep(
        scriptSource: ScriptSource,
        step: InterpretationSequenceStep<R>
    ): RestrictedKotlinScriptEvaluator.EvaluationResult {
        val failureReasons = mutableListOf<NotEvaluated.StageFailure>()

        val evaluationSchema = step.evaluationSchemaForStep()

        val resolver = defaultCodeResolver(evaluationSchema.analysisStatementFilter)
        val ast = astFromScript(scriptSource).singleOrNull()
            ?: run {
                failureReasons += (NoParseResult)
                return NotEvaluated(failureReasons)
            }
        val languageModel = languageModelFromAst(ast, scriptSource)
        val failures = languageModel.results.filterIsInstance<FailingResult>()
        if (failures.isNotEmpty()) {
            failureReasons += FailuresInLanguageTree(failures)
        }
        val elements = languageModel.results.filterIsInstance<Element<*>>().map { it.element }
        val resolution = resolver.resolve(evaluationSchema.analysisSchema, elements)
        if (resolution.errors.isNotEmpty()) {
            failureReasons += FailuresInResolution(resolution.errors)
        }

        val trace = assignmentTrace(resolution)
        val unassignedValueUsages = trace.elements.filterIsInstance<AssignmentTraceElement.UnassignedValueUsed>()
        if (unassignedValueUsages.isNotEmpty()) {
            failureReasons += UnassignedValuesUsed(unassignedValueUsages)
        }
        if (failureReasons.isNotEmpty()) {
            return NotEvaluated(failureReasons)
        }
        val context = ReflectionContext(SchemaTypeRefContext(evaluationSchema.analysisSchema), resolution, trace)
        val topLevelObjectReflection = reflect(resolution.topLevelReceiver, context)

        val propertyResolver = CompositePropertyResolver(listOf(ReflectionRuntimePropertyResolver) + evaluationSchema.additionalPropertyResolvers)
        val configureLambdas = treatInterfaceAsConfigureLambda(Action::class).plus(evaluationSchema.configureLambdas)
        val functionResolver = CompositeFunctionResolver(listOf(MemberFunctionResolver(configureLambdas)) + evaluationSchema.additionalFunctionResolvers)

        val topLevelReceiver = step.topLevelReceiver()
        val converter = RestrictedReflectionToObjectConverter(emptyMap(), topLevelReceiver, functionResolver, propertyResolver)
        converter.apply(topLevelObjectReflection)

        step.whenEvaluated(topLevelReceiver)

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
    fun languageModelFromAst(ast: Ast, scriptSource: ScriptSource): LanguageTreeResult =
        languageTreeBuilder.build(ast, AstSourceIdentifier(ast, scriptSource.fileName))

    private
    val languageTreeBuilder = LanguageTreeBuilderWithTopLevelBlock(DefaultLanguageTreeBuilder())

    private
    fun scriptContextFor(
        target: Any,
        scriptSource: ScriptSource,
        evaluationContext: RestrictedKotlinScriptEvaluator.EvaluationContext
    ) = when (target) {
        is Settings -> RestrictedScriptContext.SettingsScript
        is Project -> {
            require(evaluationContext is ScriptPluginEvaluationContext) { "restricted DSL for projects is only supported in script plugins" }
            RestrictedScriptContext.ProjectScript(evaluationContext.targetScope, scriptSource)
        }
        is RuntimeTopLevelPluginsReceiver -> RestrictedScriptContext.PluginsBlock
        else -> RestrictedScriptContext.UnknownScript
    }
}
