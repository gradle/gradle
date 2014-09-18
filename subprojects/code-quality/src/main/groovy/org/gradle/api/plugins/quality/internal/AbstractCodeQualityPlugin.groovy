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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.plugins.quality.CodeQualityExtension
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet

abstract class AbstractCodeQualityPlugin<T> implements Plugin<ProjectInternal> {
    protected ProjectInternal project
    protected CodeQualityExtension extension

    final void apply(ProjectInternal project) {
        this.project = project

        beforeApply()
        project.plugins.apply(ReportingBasePlugin)
        createConfigurations()
        extension = createExtension()
        configureExtensionRule()
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

    protected String getReportName() {
        return toolName.toLowerCase()
    }

    protected Class<?> getBasePlugin() {
        return JavaBasePlugin
    }

    protected void beforeApply() {
    }

    protected void createConfigurations() {
        project.configurations.create(configurationName).with {
            visible = false
            transitive = true
            description = "The ${toolName} libraries to be used for this project."
            // Don't need these things, they're provided by the runtime
            exclude group: 'ant', module: 'ant'
            exclude group: 'org.apache.ant', module: 'ant'
            exclude group: 'org.apache.ant', module: 'ant-launcher'
            exclude group: 'org.slf4j', module: 'slf4j-api'
            exclude group: 'org.slf4j', module: 'jcl-over-slf4j'
            exclude group: 'org.slf4j', module: 'log4j-over-slf4j'
            exclude group: 'commons-logging', module: 'commons-logging'
            exclude group: 'log4j', module: 'log4j'
        }
    }

    protected abstract CodeQualityExtension createExtension()

    private void configureExtensionRule() {
        extension.conventionMapping.with {
            sourceSets = { [] }
            reportsDir = { project.extensions.getByType(ReportingExtension).file(reportName) }
        }

        project.plugins.withType(basePlugin) {
            extension.conventionMapping.sourceSets = { project.sourceSets }
        }
    }

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
        project.plugins.withType(basePlugin) {
            project.sourceSets.all { SourceSet sourceSet ->
                T task = project.tasks.create(sourceSet.getTaskName(taskBaseName, null), taskType)
                configureForSourceSet(sourceSet, task)
            }
        }
    }

    protected void configureForSourceSet(SourceSet sourceSet, T task) {
    }

    private void configureCheckTask() {
        project.plugins.withType(basePlugin) {
            project.tasks['check'].dependsOn { extension.sourceSets.collect { it.getTaskName(taskBaseName, null) }}
        }
    }
}
