/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.settings

import org.gradle.api.initialization.Settings
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isCallNamed
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isConfiguringCall
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isTopLevelElement
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.analysis.implies
import org.gradle.internal.declarativedsl.analysis.not
import org.gradle.internal.declarativedsl.conventions.isConventionsConfiguringCall
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStepWithConversion
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.conventions.conventionsDefinitionInterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluationSchema.DefaultInterpretationSequence
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.project.thirdPartyExtensions
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


internal
fun settingsInterpretationSequence(
    settings: SettingsInternal,
    targetScope: ClassLoaderScope,
    scriptSource: ScriptSource,
    softwareTypeRegistry: SoftwareTypeRegistry
): InterpretationSequence =
    DefaultInterpretationSequence(
        listOf(
            SimpleInterpretationSequenceStepWithConversion("settingsPluginManagement", features = setOf(SettingsBlocksCheck.feature)) { pluginManagementEvaluationSchema() },
            PluginsInterpretationSequenceStep("settingsPlugins", targetScope, scriptSource) { settings.services },
            conventionsDefinitionInterpretationSequenceStep(softwareTypeRegistry),
            SimpleInterpretationSequenceStepWithConversion("settings") { settingsEvaluationSchema(settings) }
        )
    )


internal
fun pluginManagementEvaluationSchema(): EvaluationAndConversionSchema =
    buildEvaluationAndConversionSchema(
        Settings::class,
        isTopLevelPluginManagementBlock,
        schemaComponents = EvaluationSchemaBuilder::gradleDslGeneralSchema
    )


/** TODO: Instead of [SettingsInternal], this should rely on the public API of [Settings];
 *  missing single-arg [Settings.include] (or missing vararg support) prevents this from happening,
 *  and we use the [SettingsInternal.include] single-argument workaround for now. */
internal
fun settingsEvaluationSchema(settings: Settings): EvaluationAndConversionSchema =
    buildEvaluationAndConversionSchema(
        SettingsInternal::class,
        ignoreTopLevelPluginsPluginManagementAndConventions
    ) {
        gradleDslGeneralSchema()
        thirdPartyExtensions(SettingsInternal::class, settings)
    }


private
val isPluginManagementCall: AnalysisStatementFilter = isConfiguringCall.and(isCallNamed("pluginManagement"))


private
val isTopLevelPluginManagementBlock = isTopLevelElement.implies(isPluginManagementCall)


private
val ignoreTopLevelPluginsPluginManagementAndConventions = isTopLevelElement.implies(
    isPluginManagementCall.not()
        .and(isTopLevelPluginsBlock.not())
        .and(isConventionsConfiguringCall.not())
)
