/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders.features

import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder

/**
 * A {@link ProjectFeaturePluginClassBuilder} that uses an unsafe service (Project) in the services interface.
 */
class ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder extends ProjectFeaturePluginClassBuilder {
    ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder(ProjectFeatureDefinitionClassBuilder definition) {
        super(definition)
    }

    @Override
    String getServicesInterface() {
        return """
            interface Services {
                @javax.inject.Inject
                ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

                @javax.inject.Inject
                ${ProviderFactory.class.name} getProviderFactory();

                @javax.inject.Inject
                Project getProject(); // Unsafe Service

                default ${TaskContainer.class.name} getTaskRegistrar() {
                    return getProject().getTasks();
                }
            }
        """
    }
}
