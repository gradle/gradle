/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.plugins.quality.internal

import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.quality.CodeQualityExtension
import org.gradle.api.tasks.SourceSet

abstract class AbstractCodeQualityPlugin<T> implements Plugin<ProjectInternal> {
    protected ProjectInternal project
    protected CodeQualityExtension extension

    final void apply(ProjectInternal project) {
        this.project = project
        beforeApply()
        createConfigurations()
        extension = createExtension()
        configureTaskRule()
        configureSourceSetRule()
        configureCheckTask()
    }

    protected abstract String getToolName()

    protected abstract Class<T> getTaskType()

    protected String getTaskBaseName() {
        return toolName.toLowerCase()
    }

    protected String getConfigurationName() {
        return toolName.toLowerCase()
    }

    protected void beforeApply() {
    }

    protected void createConfigurations() {
        project.configurations.add(configurationName).with {
            visible = false
            transitive = true
            description = "The ${toolName} libraries to be used for this project."
        }
    }

    protected abstract CodeQualityExtension createExtension()

    private void configureTaskRule() {
        project.tasks.withType(taskType) { T task ->
            def prunedName = (task.name - taskBaseName ?: task.name)
            prunedName = prunedName[0].toLowerCase() + prunedName.substring(1)
            configureTaskDefaults(task, prunedName)
        }
    }

    protected void configureTaskDefaults(T task, String baseName) {
    }

    private void configureSourceSetRule() {
        project.sourceSets.all { SourceSet sourceSet ->
            T task = project.tasks.add(sourceSet.getTaskName(taskBaseName, null), taskType)
            configureForSourceSet(sourceSet, task)
        }
    }

    protected void configureForSourceSet(SourceSet sourceSet, T task) {
    }

    private void configureCheckTask() {
        project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName(taskBaseName, null) }}
    }
}
