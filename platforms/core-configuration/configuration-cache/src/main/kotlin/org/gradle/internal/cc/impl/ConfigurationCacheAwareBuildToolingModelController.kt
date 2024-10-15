/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildToolingModelController
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import org.gradle.tooling.provider.model.internal.ToolingModelScope


internal
class ConfigurationCacheAwareBuildToolingModelController(
    private val delegate: BuildToolingModelController,
    private val cache: BuildTreeConfigurationCache
) : BuildToolingModelController {
    override fun getConfiguredModel(): GradleInternal = delegate.configuredModel

    override fun locateBuilderForTarget(modelName: String, param: Boolean): ToolingModelScope {
        return wrap(delegate.locateBuilderForTarget(modelName, param))
    }

    override fun locateBuilderForTarget(target: ProjectState, modelName: String, param: Boolean): ToolingModelScope {
        return wrap(delegate.locateBuilderForTarget(target, modelName, param))
    }

    private
    fun wrap(scope: ToolingModelScope): ToolingModelScope {
        return CachingToolingModelScope(scope, cache)
    }

    private
    class CachingToolingModelScope(
        private val delegate: ToolingModelScope,
        private val cache: BuildTreeConfigurationCache
    ) : ToolingModelScope {
        override fun getTarget() = delegate.target

        override fun getModel(modelName: String, parameter: ToolingModelParameterCarrier?): Any? {
            return cache.loadOrCreateIntermediateModel(getTargetPath(), modelName, parameter) {
                delegate.getModel(modelName, parameter)
            }
        }

        private
        fun getTargetPath(): ProjectIdentityPath? {
            return target?.run {
                ProjectIdentityPath(identityPath, owner.identityPath, projectPath)
            }
        }
    }
}
