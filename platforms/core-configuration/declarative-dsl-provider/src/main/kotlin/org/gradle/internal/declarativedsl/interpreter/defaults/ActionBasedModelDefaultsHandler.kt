/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.declarativedsl.interpreter.defaults

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.file.ProjectLayout
import org.gradle.api.initialization.internal.SharedModelDefaultsInternal
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.initialization.ActionBasedDefault
import org.gradle.internal.Cast
import org.gradle.plugin.software.internal.ModelDefault
import org.gradle.plugin.software.internal.ModelDefaultsApplicator.ClassLoaderContext
import org.gradle.plugin.software.internal.ModelDefaultsHandler
import org.gradle.plugin.software.internal.ProjectFeatureImplementation
import org.gradle.plugin.software.internal.ProjectFeatureRegistry

class ActionBasedModelDefaultsHandler(
    private val sharedModelDefaults: SharedModelDefaultsInternal,
    private val projectLayout: ProjectLayout,
    private val projectFeatureRegistry: ProjectFeatureRegistry,
) : ModelDefaultsHandler {

    override fun apply(target: Any, definition: Any, classLoaderContext: ClassLoaderContext, projectFeatureName: String, plugin: Plugin<*>) {
        val projectFeatureImplementation: ProjectFeatureImplementation<*, *> = projectFeatureRegistry.getProjectFeatureImplementations()[projectFeatureName]!!

        if (target is DynamicObjectAware) {
            sharedModelDefaults.setProjectLayout(projectLayout)
            try {
                projectFeatureImplementation.visitModelDefaults(
                    Cast.uncheckedNonnullCast(ActionBasedDefault::class.java),
                    executeActionVisitor(projectFeatureImplementation, definition)
                )
                executeActionVisitor(projectFeatureImplementation, target)
            } finally {
                sharedModelDefaults.clearProjectLayout()
            }
        } else {
            throw GradleException("Tried to apply defaults for project feature '$projectFeatureName', got unexpected target object: ${target::class.qualifiedName}")
        }
    }

    private fun <T : Any, V : Any> executeActionVisitor(
        projectFeatureImplementation: ProjectFeatureImplementation<T, V>,
        modelObject: Any?
    ): ModelDefault.Visitor<Action<in T>> {
        checkNotNull(modelObject) {
            "The model object for " + projectFeatureImplementation.getFeatureName() + " declared in " + projectFeatureImplementation.getPluginClass().getName() + " is null."
        }
        return ModelDefault.Visitor { action: Action<in T>? -> action!!.execute(Cast.uncheckedNonnullCast(modelObject)) }
    }
}
