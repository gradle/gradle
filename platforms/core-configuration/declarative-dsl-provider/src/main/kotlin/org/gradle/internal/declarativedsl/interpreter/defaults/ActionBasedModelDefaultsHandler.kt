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
import org.gradle.plugin.software.internal.SoftwareFeatureImplementation
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry

class ActionBasedModelDefaultsHandler(
    private val sharedModelDefaults: SharedModelDefaultsInternal,
    private val projectLayout: ProjectLayout,
    private val softwareFeatureRegistry: SoftwareFeatureRegistry,
) : ModelDefaultsHandler {

    override fun apply(target: Any, definition: Any, classLoaderContext: ClassLoaderContext, softwareFeatureName: String, plugin: Plugin<*>) {
        val softwareFeatureImplementation: SoftwareFeatureImplementation<*, *> = softwareFeatureRegistry.getSoftwareFeatureImplementations()[softwareFeatureName]!!

        if (target is DynamicObjectAware) {
            sharedModelDefaults.setProjectLayout(projectLayout)
            try {
                softwareFeatureImplementation.visitModelDefaults(
                    Cast.uncheckedNonnullCast(ActionBasedDefault::class.java),
                    executeActionVisitor(softwareFeatureImplementation, definition)
                )
                executeActionVisitor(softwareFeatureImplementation, target)
            } finally {
                sharedModelDefaults.clearProjectLayout()
            }
        } else {
            throw GradleException("Tried to apply defaults for software feature '$softwareFeatureName', got unexpected target object: ${target::class.qualifiedName}")
        }
    }

    private fun <T : Any, V : Any> executeActionVisitor(
        softwareFeatureImplementation: SoftwareFeatureImplementation<T, V>,
        modelObject: Any?
    ): ModelDefault.Visitor<Action<in T>> {
        checkNotNull(modelObject) {
            "The model object for " + softwareFeatureImplementation.getFeatureName() + " declared in " + softwareFeatureImplementation.getPluginClass().getName() + " is null."
        }
        return ModelDefault.Visitor { action: Action<in T>? -> action!!.execute(Cast.uncheckedNonnullCast(modelObject)) }
    }
}
