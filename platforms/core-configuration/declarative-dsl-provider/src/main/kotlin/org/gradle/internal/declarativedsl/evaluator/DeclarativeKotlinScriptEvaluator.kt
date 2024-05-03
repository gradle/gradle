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

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.dom.resolvedDocument
import org.gradle.internal.declarativedsl.dom.toDocument
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationContext.ScriptPluginEvaluationContext
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.AssignmentErrors
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.FailuresInLanguageTree
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.FailuresInResolution
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator.EvaluationResult.NotEvaluated.StageFailure.NoSchemaAvailable
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.CompositePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeReflectionToObjectConverter
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTracer
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext
import org.gradle.internal.declarativedsl.objectGraph.reflect
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse


internal
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
                data class DocumentCheckFailures(val failures: List<DocumentCheckFailure>) : StageFailure
                data class AssignmentErrors(val usages: List<AssignmentTraceElement.FailedToRecordAssignment>) : StageFailure
            }
        } // TODO: make reason more structured
    }

    sealed interface EvaluationContext {
        class ScriptPluginEvaluationContext(
            val targetScope: ClassLoaderScope
        ) : EvaluationContext
    }
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
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> runInterpretationSequence(scriptSource, built.sequence, target)
        }
    }

    private
    fun runInterpretationSequence(
        scriptSource: ScriptSource,
        sequence: InterpretationSequence,
        target: Any
    ): DeclarativeKotlinScriptEvaluator.EvaluationResult {
        sequence.steps.forEach { step ->
            val result = runInterpretationSequenceStep(target, scriptSource, step)
            if (result is NotEvaluated) {
                return result
            }
        }
        return DeclarativeKotlinScriptEvaluator.EvaluationResult.Evaluated
    }

    private
    fun <R : Any> runInterpretationSequenceStep(
        target: Any,
        scriptSource: ScriptSource,
        step: InterpretationSequenceStep<R>
    ): DeclarativeKotlinScriptEvaluator.EvaluationResult {
        val failureReasons = mutableListOf<NotEvaluated.StageFailure>()

        val evaluationSchema = step.evaluationSchemaForStep()

        val resolver = tracingCodeResolver(evaluationSchema.analysisStatementFilter)

        val languageModel = languageModelFromLightParser(scriptSource)

        if (languageModel.allFailures.isNotEmpty()) {
            failureReasons += FailuresInLanguageTree(languageModel.allFailures)
        }
        val resolution = resolver.resolve(evaluationSchema.analysisSchema, languageModel.imports, languageModel.topLevelBlock)
        if (resolution.errors.isNotEmpty()) {
            failureReasons += FailuresInResolution(resolution.errors)
        }

        val document = resolvedDocument(evaluationSchema.analysisSchema, resolver.trace, languageModel.toDocument())
        val checkResults = evaluationSchema.documentChecks.flatMap { it.detectFailures(document) }
        if (checkResults.isNotEmpty()) {
            failureReasons += NotEvaluated.StageFailure.DocumentCheckFailures(checkResults)
        }

        val trace = assignmentTrace(resolution)
        val assignmentErrors = trace.elements.filterIsInstance<AssignmentTraceElement.FailedToRecordAssignment>()
        if (assignmentErrors.isNotEmpty()) {
            failureReasons += AssignmentErrors(assignmentErrors)
        }
        if (failureReasons.isNotEmpty()) {
            return NotEvaluated(failureReasons)
        }
        val context = ReflectionContext(SchemaTypeRefContext(evaluationSchema.analysisSchema), resolution, trace)
        val topLevelObjectReflection = reflect(resolution.topLevelReceiver, context)

        val propertyResolver = CompositePropertyResolver(evaluationSchema.runtimePropertyResolvers)
        val functionResolver = CompositeFunctionResolver(evaluationSchema.runtimeFunctionResolvers)
        val customAccessors = CompositeCustomAccessors(evaluationSchema.runtimeCustomAccessors)

        val topLevelReceiver = step.getTopLevelReceiverFromTarget(target)
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
    ): RestrictedScriptContext = when (target) {
        is Settings -> RestrictedScriptContext.SettingsScript(requirePluginContext(evaluationContext).targetScope, scriptSource)
        is Project -> RestrictedScriptContext.ProjectScript(requirePluginContext(evaluationContext).targetScope, scriptSource)
        else -> RestrictedScriptContext.UnknownScript
    }

    private
    fun requirePluginContext(evaluationContext: DeclarativeKotlinScriptEvaluator.EvaluationContext): ScriptPluginEvaluationContext {
        require(evaluationContext is ScriptPluginEvaluationContext) { "this target is not supported outside script plugins" }
        return evaluationContext
    }
}
