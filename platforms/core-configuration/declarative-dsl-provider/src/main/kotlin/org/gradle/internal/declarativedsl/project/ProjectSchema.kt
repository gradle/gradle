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

package org.gradle.internal.declarativedsl.project

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.plus
import org.gradle.internal.declarativedsl.plugins.PluginsInterpretationSequenceStep
import org.gradle.internal.declarativedsl.plugins.PluginsTopLevelReceiver
import org.gradle.internal.declarativedsl.plugins.isPluginsCall


internal
fun projectInterpretationSequence(
    target: ProjectInternal,
    targetScope: ClassLoaderScope,
    scriptSource: ScriptSource
) = InterpretationSequence(
    listOf(
        step1Plugins(target, targetScope, scriptSource),
        step2Project(target, targetScope)
    )
)


private
fun step1Plugins(target: ProjectInternal, targetScope: ClassLoaderScope, scriptSource: ScriptSource): InterpretationSequenceStep<PluginsTopLevelReceiver> =
    PluginsInterpretationSequenceStep(target, targetScope, scriptSource, ProjectInternal::getServices)


private
fun step2Project(target: ProjectInternal, targetScope: ClassLoaderScope) = object : InterpretationSequenceStep<ProjectInternal> {
    override val stepIdentifier: String = "project"

    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema(
        ProjectTopLevelReceiver::class,
        projectEvaluationSchemaComponent(target, targetScope),
        analysisStatementFilter = analyzeEverythingExceptPluginsBlock,
    )

    override fun topLevelReceiver(): ProjectInternal = target

    override fun whenEvaluated(resultReceiver: ProjectInternal) = Unit
}


private
fun projectEvaluationSchemaComponent(
    target: ProjectInternal,
    targetScope: ClassLoaderScope
) = gradleDslGeneralSchemaComponent() +
    ThirdPartyExtensionsComponent(ProjectTopLevelReceiver::class, target, "projectExtension") +
    DependencyConfigurationsComponent(target) +
    TypesafeProjectAccessorsComponent(targetScope)


private
val analyzeEverythingExceptPluginsBlock = AnalysisStatementFilter { statement, scopes ->
    if (scopes.last().receiver is ObjectOrigin.TopLevelReceiver) {
        !isPluginsCall(statement)
    } else true
}
