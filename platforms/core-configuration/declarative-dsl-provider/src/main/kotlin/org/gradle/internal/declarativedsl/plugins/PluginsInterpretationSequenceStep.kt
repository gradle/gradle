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

package org.gradle.internal.declarativedsl.plugins

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.service.ServiceRegistry
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.plugin.use.internal.PluginRequestApplicator


internal
class PluginsInterpretationSequenceStep<T>(
    private val target: T,
    private val targetScope: ClassLoaderScope,
    private val scriptSource: ScriptSource,
    private val getTargetServices: (T) -> ServiceRegistry,
    override val stepIdentifier: String = "plugins",
) : InterpretationSequenceStep<PluginsTopLevelReceiver> {
    override fun evaluationSchemaForStep(): EvaluationSchema = EvaluationSchema(
        schemaForPluginsBlock,
        analysisStatementFilter = analyzeTopLevelPluginsBlockOnly
    )

    override fun topLevelReceiver() = PluginsTopLevelReceiver()

    override fun whenEvaluated(resultReceiver: PluginsTopLevelReceiver) {
        val pluginRequests = resultReceiver.plugins.specs.map {
            DefaultPluginRequest(DefaultPluginId.unvalidated(it.id), it.apply, PluginRequestInternal.Origin.OTHER, scriptSource.displayName, null, it.version, null, null, null)
        }
        with(getTargetServices(target)) {
            val scriptHandler = get(ScriptHandlerFactory::class.java).create(scriptSource, targetScope)
            val pluginManager = get(PluginManagerInternal::class.java)
            val pluginApplicator = get(PluginRequestApplicator::class.java)
            pluginApplicator.applyPlugins(PluginRequests.of(pluginRequests), scriptHandler, pluginManager, targetScope)
        }
        targetScope.lock()
    }
}


internal
val analyzeTopLevelPluginsBlockOnly = AnalysisStatementFilter { statement, scopes ->
    if (scopes.last().receiver is ObjectOrigin.TopLevelReceiver) {
        isPluginsCall(statement)
    } else true
}


internal
fun isPluginsCall(statement: DataStatement) =
    statement is FunctionCall && statement.name == "plugins" && statement.args.size == 1 && statement.args.single() is FunctionArgument.Lambda
