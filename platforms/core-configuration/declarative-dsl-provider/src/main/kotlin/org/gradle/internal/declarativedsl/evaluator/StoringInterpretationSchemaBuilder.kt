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

import org.gradle.internal.declarativedsl.serialization.SchemaSerialization
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import java.io.File


/**
 * In addition to creating the interpretation schema by delegating to [schemaBuilder],
 * stores the produced serialized schema in the file system (under `.gradle/restricted-schema/...` in the project).
 */
internal
class StoringInterpretationSchemaBuilder(
    private val schemaBuilder: InterpretationSchemaBuilder,
    private val declarativeSchemaRegistry: DeclarativeSchemaRegistry
) : InterpretationSchemaBuilder {
    override fun getEvaluationSchemaForScript(targetInstance: Any, scriptContext: RestrictedScriptContext): InterpretationSchemaBuildingResult =
        addSerializationToSteps(targetInstance, schemaBuilder.getEvaluationSchemaForScript(targetInstance, scriptContext))

    private
    fun addSerializationToSteps(targetInstance: Any, evaluationSchemaForScript: InterpretationSchemaBuildingResult): InterpretationSchemaBuildingResult =
        when (evaluationSchemaForScript) {
            is InterpretationSchemaBuildingResult.InterpretationSequenceAvailable -> {
                val stepsWithSchemaStoring = evaluationSchemaForScript.sequence.steps.map {
                    SchemaHandlingInterpretationSequenceStep(it) { id, schema -> storeSchemaResult(targetInstance, id, schema) }
                }
                InterpretationSchemaBuildingResult.InterpretationSequenceAvailable(InterpretationSequence(stepsWithSchemaStoring))
            }

            InterpretationSchemaBuildingResult.SchemaNotBuilt -> evaluationSchemaForScript
        }

    private
    fun storeSchemaResult(targetInstance: Any, identifier: String, analysisSchema: AnalysisSchema) {
        val file = schemaFile(targetInstance, identifier)
        file.parentFile.mkdirs()
        file.writeText(SchemaSerialization.schemaToJsonString(analysisSchema))

        declarativeSchemaRegistry.storeSchema(targetInstance, identifier, analysisSchema)
    }

    private
    fun schemaFile(targetInstance: Any, identifier: String) =
        schemaStoreLocationFor(targetInstance).resolve("$identifier.something.schema")

    private
    fun schemaStoreLocationFor(targetInstance: Any): File {
        val suffix = ".gradle/restricted-schema" // TODO: need to rename to "declarative-schema"
        return when (targetInstance) {
            is Settings -> targetInstance.settingsDir.resolve(suffix)
            is Project -> targetInstance.projectDir.resolve(suffix)
            else -> error("unexpected target instance of type ${targetInstance.javaClass}")
        }
    }

    private
    class SchemaHandlingInterpretationSequenceStep<R : Any>(
        private val step: InterpretationSequenceStep<R>,
        private val schemaHandler: (schemaId: String, schema: AnalysisSchema) -> Unit
    ) : InterpretationSequenceStep<R> {
        override val stepIdentifier: String = step.stepIdentifier
        override fun evaluationSchemaForStep(): EvaluationSchema = step.evaluationSchemaForStep().also { schemaHandler(stepIdentifier, it.analysisSchema) }
        override fun topLevelReceiver(): R = step.topLevelReceiver()
        override fun whenEvaluated(resultReceiver: R) = step.whenEvaluated(resultReceiver)
    }
}
