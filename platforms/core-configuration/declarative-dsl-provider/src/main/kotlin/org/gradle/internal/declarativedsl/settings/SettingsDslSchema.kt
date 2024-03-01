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
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.plus
import org.gradle.internal.declarativedsl.plugins.PluginsInterpretationSequenceStep
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
            PluginsInterpretationSequenceStep("settingsPlugins", settings, targetScope, scriptSource) { it.services },
            SimpleInterpretationSequenceStep("settings", settings) { settingsEvaluationSchema(settings) }
        )
    )


internal
fun settingsEvaluationSchema(settings: Settings): EvaluationSchema {
    val schemaBuildingComponent = gradleDslGeneralSchemaComponent() +
        ThirdPartyExtensionsComponent(Settings::class, settings, "settingsExtension")

    return buildEvaluationSchema(Settings::class, schemaBuildingComponent, analyzeEverything)
}
