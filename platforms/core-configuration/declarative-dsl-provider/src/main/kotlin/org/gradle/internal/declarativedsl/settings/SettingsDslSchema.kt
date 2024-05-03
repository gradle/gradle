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
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter.Companion.isCallNamed
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter.Companion.isConfiguringCall
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter.Companion.isTopLevelElement
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.analysis.implies
import org.gradle.internal.declarativedsl.analysis.not
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.plus
import org.gradle.internal.declarativedsl.plugins.PluginsInterpretationSequenceStep
import org.gradle.internal.declarativedsl.plugins.isTopLevelPluginsBlock
import org.gradle.internal.declarativedsl.project.ThirdPartyExtensionsComponent
import org.gradle.internal.declarativedsl.project.gradleDslGeneralSchemaComponent


internal
fun settingsInterpretationSequence(
    settings: SettingsInternal,
    targetScope: ClassLoaderScope,
    scriptSource: ScriptSource
): InterpretationSequence =
    InterpretationSequence(
        listOf(
            SimpleInterpretationSequenceStep("settingsPluginManagement") { pluginManagementEvaluationSchema() },
            PluginsInterpretationSequenceStep("settingsPlugins", targetScope, scriptSource, SettingsBlocksCheck) { settings.services },
            SimpleInterpretationSequenceStep("settings") { settingsEvaluationSchema(settings) }
        )
    )


internal
fun pluginManagementEvaluationSchema(): EvaluationSchema =
    buildEvaluationSchema(
        Settings::class,
        gradleDslGeneralSchemaComponent() + SettingsBlocksCheck,
        isTopLevelPluginManagementBlock
    )


internal
fun settingsEvaluationSchema(settings: Settings): EvaluationSchema {
    val schemaBuildingComponent = gradleDslGeneralSchemaComponent() +
        /** TODO: Instead of [SettingsInternal], this should rely on the public API of [Settings];
         *  missing single-arg [Settings.include] (or missing vararg support) prevents this from happening,
         *  and we use the [SettingsInternal.include] single-argument workaround for now. */
        ThirdPartyExtensionsComponent(SettingsInternal::class, settings, "settingsExtension")

    return buildEvaluationSchema(SettingsInternal::class, schemaBuildingComponent, ignoreTopLevelPluginsAndPluginManagement)
}


private
val isPluginManagementCall: AnalysisStatementFilter = isConfiguringCall.and(isCallNamed("pluginManagement"))


private
val isTopLevelPluginManagementBlock = isTopLevelElement.implies(isPluginManagementCall)


private
val ignoreTopLevelPluginsAndPluginManagement = isTopLevelElement.implies(isPluginManagementCall.not().and(isTopLevelPluginsBlock.not()))
