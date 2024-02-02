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

package org.gradle.internal.declarativedsl.evaluator

import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.defaultCodeResolver
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.CompositePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.MemberFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.ReflectionRuntimePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeReflectionToObjectConverter
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTracer
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext
import org.gradle.internal.declarativedsl.objectGraph.reflect
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.plus
import org.gradle.internal.declarativedsl.schemaBuilder.treatInterfaceAsConfigureLambda
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationContext.ScriptPluginEvaluationContext
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.FailuresInLanguageTree
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.FailuresInResolution
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.NoSchemaAvailable
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.UnassignedValuesUsed
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.plugins.PluginsTopLevelReceiver


interface DeclarativeKotlinScriptEvaluator {
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
                data class FailuresInLanguageTree(val failures: List<SingleFailureResult>) : StageFailure
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
 * A default implementation of a declarative DSL script evaluator, for use when no additional information needs to be provided at the use site.
 * TODO: The consumers should get an instance properly injected instead.
 */
val defaultDeclarativeKotlinScriptEvaluator: DeclarativeKotlinScriptEvaluator by lazy {
    DefaultDeclarativeKotlinScriptEvaluator(DefaultInterpretationSchemaBuilder())
}


internal
class DefaultDeclarativeKotlinScriptEvaluator(
    private val schemaBuilder: InterpretationSchemaBuilder
) : DeclarativeKotlinScriptEvaluator {
    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        evaluationContext: DeclarativeKotlinScriptEvaluator.EvaluationContext
    ): DeclarativeKotlinScriptEvaluator.EvaluationResult {
        return when (val built = schemaBuilder.getEvaluationSchemaForScript(target, scriptContextFor(target, scriptSource, evaluationContext))) {
            InterpretationSchemaBuildingResult.SchemaNotBuilt -> NotEvaluated(listOf(NoSchemaAvailable(target)))
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> runInterpretationSequence(scriptSource, built.sequence)
        }
    }

    private
    fun runInterpretationSequence(
        scriptSource: ScriptSource,
        sequence: InterpretationSequence
    ): DeclarativeKotlinScriptEvaluator.EvaluationResult {
        sequence.steps.forEach { step ->
            val result = runInterpretationSequenceStep(scriptSource, step)
            if (result is NotEvaluated) {
                return result
            }
        }
        return DeclarativeKotlinScriptEvaluator.EvaluationResult.Evaluated
    }

    private
    fun <R : Any> runInterpretationSequenceStep(
        scriptSource: ScriptSource,
        step: InterpretationSequenceStep<R>
    ): DeclarativeKotlinScriptEvaluator.EvaluationResult {
        val failureReasons = mutableListOf<NotEvaluated.StageFailure>()

        val evaluationSchema = step.evaluationSchemaForStep()

        val resolver = defaultCodeResolver(evaluationSchema.analysisStatementFilter)

        val languageModel = languageModelFromLightParser(scriptSource)

        if (languageModel.allFailures.isNotEmpty()) {
            failureReasons += FailuresInLanguageTree(languageModel.allFailures)
        }
        val resolution = resolver.resolve(evaluationSchema.analysisSchema, languageModel.imports, languageModel.topLevelBlock)
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

        val propertyResolver = CompositePropertyResolver(listOf(ReflectionRuntimePropertyResolver) + evaluationSchema.runtimePropertyResolvers)
        val configureLambdas = treatInterfaceAsConfigureLambda(Action::class).plus(evaluationSchema.configureLambdas)
        val functionResolver = CompositeFunctionResolver(listOf(MemberFunctionResolver(configureLambdas)) + evaluationSchema.runtimeFunctionResolvers)
        val customAccessors = CompositeCustomAccessors(evaluationSchema.runtimeCustomAccessors)

        val topLevelReceiver = step.topLevelReceiver()
        val converter = DeclarativeReflectionToObjectConverter(emptyMap(), topLevelReceiver, functionResolver, propertyResolver, customAccessors)
        converter.apply(topLevelObjectReflection)

        step.whenEvaluated(topLevelReceiver)

        return DeclarativeKotlinScriptEvaluator.EvaluationResult.Evaluated
    }

    private
    fun assignmentTrace(result: ResolutionResult) =
        AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(result)

    private
    fun languageModelFromLightParser(scriptSource: ScriptSource): LanguageTreeResult {
        val (tree, code, codeOffset) = parse(scriptSource.resource.text)
        return languageTreeBuilder.build(tree, code, codeOffset, SourceIdentifier(scriptSource.fileName))
    }

    private
    val languageTreeBuilder = DefaultLanguageTreeBuilder()

    private
    fun scriptContextFor(
        target: Any,
        scriptSource: ScriptSource,
        evaluationContext: DeclarativeKotlinScriptEvaluator.EvaluationContext
    ) = when (target) {
        is Settings -> RestrictedScriptContext.SettingsScript
        is Project -> {
            require(evaluationContext is ScriptPluginEvaluationContext) { "declarative DSL for projects is only supported in script plugins" }
            RestrictedScriptContext.ProjectScript(evaluationContext.targetScope, scriptSource)
        }
        is PluginsTopLevelReceiver -> RestrictedScriptContext.PluginsBlock
        else -> RestrictedScriptContext.UnknownScript
    }
}
