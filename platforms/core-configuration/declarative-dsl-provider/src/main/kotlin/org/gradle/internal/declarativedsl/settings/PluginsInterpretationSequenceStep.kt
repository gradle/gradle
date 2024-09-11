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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.InterpretationSequenceStep.StepIdentifier
import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isCallNamed
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isConfiguringCall
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isTopLevelElement
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.analysis.implies
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.evaluationSchema.DefaultStepIdentifier
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluator.conversion.InterpretationSequenceStepWithConversion
import org.gradle.internal.declarativedsl.plugins.PluginsTopLevelReceiver
import org.gradle.internal.service.ServiceRegistry
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.plugin.use.internal.PluginRequestApplicator


internal
class PluginsInterpretationSequenceStep(
    stepIdentifierString: String = "plugins",
    private val targetScope: ClassLoaderScope,
    private val scriptSource: ScriptSource,
    private val getTargetServices: () -> ServiceRegistry,
) : InterpretationSequenceStepWithConversion<PluginsTopLevelReceiver> {

    override val stepIdentifier: StepIdentifier = DefaultStepIdentifier(stepIdentifierString)

    override val evaluationSchemaForStep: EvaluationAndConversionSchema
        get() = buildEvaluationAndConversionSchema(PluginsTopLevelReceiver::class, isTopLevelPluginsBlock) {
            gradleDslGeneralSchema()
        }

    override fun getTopLevelReceiverFromTarget(target: Any) = PluginsTopLevelReceiver()

    override val features: Set<InterpretationStepFeature>
        get() = setOf(SettingsBlocksCheck.feature, UnsupportedSyntaxFeatureCheck.feature)

    override fun whenEvaluated(resultReceiver: PluginsTopLevelReceiver) {
        val pluginRequests = resultReceiver.plugins.specs.map {
            DefaultPluginRequest(DefaultPluginId.unvalidated(it.id), it.apply, PluginRequestInternal.Origin.OTHER, scriptSource.displayName, null, it.version, null, null, null)
        }
        with(getTargetServices()) {
            val scriptHandler = get(ScriptHandlerFactory::class.java).create(scriptSource, targetScope, StandaloneDomainObjectContext.forScript(scriptSource))
            val pluginManager = get(PluginManagerInternal::class.java)
            val pluginApplicator = get(PluginRequestApplicator::class.java)
            pluginApplicator.applyPlugins(PluginRequests.of(pluginRequests), scriptHandler, pluginManager, targetScope)
        }
        targetScope.lock()
    }
}


private
val isPluginConfiguringCall: AnalysisStatementFilter = isConfiguringCall.and(isCallNamed("plugins"))


internal
val isTopLevelPluginsBlock: AnalysisStatementFilter = isTopLevelElement.implies(isPluginConfiguringCall)
