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
        project.pluginManager.apply(ReportingBasePlugin)
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
            description = "The ${this.toolName} libraries to be used for this project."
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
            reportsDir = { this.project.extensions.getByType(ReportingExtension).file(this.reportName) }
        }

        project.plugins.withType(basePlugin) {
            this.extension.conventionMapping.sourceSets = {  this.project.sourceSets }
        }
    }

    private void configureTaskRule() {
        project.tasks.withType(taskType) { T task ->
            def prunedName = (task.name - this.taskBaseName ?: task.name)
            prunedName = prunedName[0].toLowerCase() + prunedName.substring(1)
            this.configureTaskDefaults(task, prunedName)
        }
    }

    protected void configureTaskDefaults(T task, String baseName) {
    }

    private void configureSourceSetRule() {
        project.plugins.withType(basePlugin) {
            this.project.sourceSets.all { SourceSet sourceSet ->
                T task = this.project.tasks.create(sourceSet.getTaskName(this.taskBaseName, null), this.taskType)
                this.configureForSourceSet(sourceSet, task)
            }
        }
    }

    protected void configureForSourceSet(SourceSet sourceSet, T task) {
    }

    private void configureCheckTask() {
        project.plugins.withType(basePlugin) {
            this.project.tasks['check'].dependsOn { this.extension.sourceSets.collect { it.getTaskName(this.taskBaseName, null) } }
        }
    }
}
