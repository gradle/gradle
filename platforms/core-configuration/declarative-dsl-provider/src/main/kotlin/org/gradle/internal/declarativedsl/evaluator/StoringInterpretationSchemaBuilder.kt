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

import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.evaluationSchema.DefaultInterpretationSequence
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.InterpretationSequenceStepWithConversion
import org.gradle.internal.declarativedsl.evaluator.schema.DeclarativeScriptContext
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluator.schema.InterpretationSchemaBuildingResult
import org.gradle.internal.declarativedsl.serialization.SchemaSerialization
import java.io.File


/**
 * In addition to creating the interpretation schema by delegating to [schemaBuilder],
 * stores the produced serialized schema in the file system (under `.gradle/declarative-schema/...` in the project).
 */
internal
class StoringInterpretationSchemaBuilder(
    private val schemaBuilder: InterpretationSchemaBuilder,
    private val settingsDir: File
) : InterpretationSchemaBuilder {

    override fun getEvaluationSchemaForScript(scriptContext: DeclarativeScriptContext): InterpretationSchemaBuildingResult =
        addSerializationToSteps(schemaBuilder.getEvaluationSchemaForScript(scriptContext))

    private
    fun addSerializationToSteps(evaluationSchemaForScript: InterpretationSchemaBuildingResult): InterpretationSchemaBuildingResult =
        when (evaluationSchemaForScript) {
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> {
                val stepsWithSchemaStoring = evaluationSchemaForScript.sequence.steps.map {
                    val schemaHandler: (String, AnalysisSchema) -> Unit = { id, schema -> storeSchemaResult(id, schema) }
                    when (it) {
                        is InterpretationSequenceStepWithConversion<*> -> SchemaHandlingInterpretationSequenceStepWithConversion(it, schemaHandler)
                        else -> SchemaHandlingInterpretationSequenceStep(it, schemaHandler)
                    }
                }
                InterpretationSchemaBuildingResult.InterpretationSequenceAvailable(DefaultInterpretationSequence(stepsWithSchemaStoring))
            }

            InterpretationSchemaBuildingResult.SchemaNotBuilt -> evaluationSchemaForScript
        }

    private
    fun storeSchemaResult(identifier: String, analysisSchema: AnalysisSchema) {
        val file = schemaFile(identifier)
        file.parentFile.mkdirs()
        file.writeText(SchemaSerialization.schemaToJsonString(analysisSchema))
    }

    private
    fun schemaFile(identifier: String) =
        schemaStoreLocationFor().resolve("$identifier.dcl.schema")

    private
    fun schemaStoreLocationFor(): File {
        val suffix = ".gradle/declarative-schema"
        return settingsDir.resolve(suffix)
    }

    private
    open class SchemaHandlingInterpretationSequenceStep(
        private val step: InterpretationSequenceStep,
        val schemaHandler: (schemaId: String, schema: AnalysisSchema) -> Unit
    ) : InterpretationSequenceStep {
        override val stepIdentifier: InterpretationSequenceStep.StepIdentifier = step.stepIdentifier
        override val features: Set<InterpretationStepFeature>
            get() = step.features

        override val evaluationSchemaForStep: EvaluationSchema by lazy {
            step.evaluationSchemaForStep.also { schemaHandler(stepIdentifier.key, it.analysisSchema) }
        }
    }

    private
    class SchemaHandlingInterpretationSequenceStepWithConversion<R : Any>(
        private val step: InterpretationSequenceStepWithConversion<R>,
        schemaHandler: (schemaId: String, schema: AnalysisSchema) -> Unit
    ) : SchemaHandlingInterpretationSequenceStep(step, schemaHandler), InterpretationSequenceStepWithConversion<R> {
        override val evaluationSchemaForStep: EvaluationAndConversionSchema by lazy {
            step.evaluationSchemaForStep.also { schemaHandler(stepIdentifier.key, it.analysisSchema) }
        }
        override fun getTopLevelReceiverFromTarget(target: Any): R = step.getTopLevelReceiverFromTarget(target)
        override fun whenEvaluated(resultReceiver: R) = step.whenEvaluated(resultReceiver)
    }
}
