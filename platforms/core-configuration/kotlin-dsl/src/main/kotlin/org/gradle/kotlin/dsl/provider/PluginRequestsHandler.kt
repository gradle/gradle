/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal
import org.gradle.plugin.management.internal.PluginHandler
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import javax.inject.Inject


internal
class PluginRequestsHandler @Inject constructor(
    private val pluginRequestApplicator: PluginRequestApplicator,
    private val pluginHandler: PluginHandler
) {

    fun handle(
        pluginRequests: PluginRequests?,
        scriptHandler: ScriptHandlerInternal,
        target: PluginAwareInternal,
        targetScope: ClassLoaderScope
    ) {
        val initialPluginRequests = pluginRequests ?: PluginRequests.EMPTY
        val allPluginRequests = pluginHandler.getAllPluginRequests(initialPluginRequests, target)
        pluginRequestApplicator.applyPlugins(
            allPluginRequests,
            scriptHandler,
            target.pluginManager,
            targetScope
        )
    }
}
