/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.internal.reflect.Instantiator
import org.gradle.testing.jacoco.tasks.JacocoBase
import org.gradle.testing.jacoco.tasks.JacocoMerge
import org.gradle.testing.jacoco.tasks.JacocoReport

import javax.inject.Inject

/**
 * Plugin that provides support for generating Jacoco coverage data.
 */
@Incubating
class JacocoPlugin implements Plugin<Project> {
    static final String AGENT_CONFIGURATION_NAME = 'jacocoAgent'
    static final String ANT_CONFIGURATION_NAME = 'jacocoAnt'
    static final String PLUGIN_EXTENSION_NAME = 'jacoco'
    private final Instantiator instantiator

    private Project project

    /**
     * Applies the plugin to the given project.
     * @param project the project to apply to
     */
    @Inject
    JacocoPlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    void apply(Project project) {
        project.plugins.apply(ReportingBasePlugin)
        this.project = project
        addJacocoConfigurations()
        JacocoAgentJar agent = instantiator.newInstance(JacocoAgentJar, project)
        JacocoPluginExtension extension = project.extensions.create(PLUGIN_EXTENSION_NAME, JacocoPluginExtension, project, agent)
        ReportingExtension reportingExtension = project.extensions.getByName(ReportingExtension.NAME)
        extension.conventionMapping.reportsDir = { reportingExtension.file("jacoco") }

        configureAgentDependencies(agent, extension)
        configureTaskClasspathDefaults(extension)
        applyToDefaultTasks(extension)
        configureDefaultOutputPathForJacocoMerge()
        configureJacocoReportDefaults(project, extension)
        addDefaultReportTasks(extension)
    }

    private void configureJacocoReportDefaults(Project project, extension) {
        project.tasks.withType(JacocoReport) { reportTask ->
            reportTask.reports.all { report ->
                report.conventionMapping.with {
                    enabled = report.name == "html"
                    if (report.outputType == Report.OutputType.DIRECTORY) {
                        destination = { new File(extension.reportsDir, "${reportTask.name}/${report.name}") }
                    } else {
                        destination = { new File(extension.reportsDir, "${reportTask.name}/${reportTask.name}.${report.name}") }
                    }
                }
            }
        }
    }

    def configureDefaultOutputPathForJacocoMerge() {
        project.tasks.withType(JacocoMerge) { task ->
            task.conventionMapping.destinationFile = { new File(project.buildDir, "/jacoco/${task.name}.exec") }
        }
    }

    /**
     * Creates the configurations used by plugin.
     * @param project the project to add the configurations to
     */
    private void addJacocoConfigurations() {
        this.project.configurations.create(AGENT_CONFIGURATION_NAME).with {
            visible = false
            transitive = true
            description = 'The Jacoco agent to use to get coverage data.'
        }
        this.project.configurations.create(ANT_CONFIGURATION_NAME).with {
            visible = false
            transitive = true
            description = 'The Jacoco ant tasks to use to get execute Gradle tasks.'
        }
    }

    /**
     * Configures the agent dependencies using the 'jacocoAnt' configuration.
     * Uses the version declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly declared.
     * @param project the project to add the dependencies to
     * @param extension the extension that has the tool version to use
     */
    private void configureAgentDependencies(JacocoAgentJar jacocoAgentJar, JacocoPluginExtension extension) {
        def config = this.project.configurations[AGENT_CONFIGURATION_NAME]
        jacocoAgentJar.conventionMapping.agentConf = { config }
        config.incoming.beforeResolve {
            if (config.dependencies.empty) {
                config.dependencies.add(this.project.dependencies.create("org.jacoco:org.jacoco.agent:${extension.toolVersion}"))
            }
        }
    }

    /**
     * Configures the classpath for Jacoco tasks using the 'jacocoAnt' configuration.
     * Uses the version information declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly declared.
     * @param extension the JacocoPluginExtension
     */
    private void configureTaskClasspathDefaults(JacocoPluginExtension extension) {
        def config = this.project.configurations[ANT_CONFIGURATION_NAME]
        this.project.tasks.withType(JacocoBase) { task ->
            task.conventionMapping.jacocoClasspath = { config }
        }
        config.incoming.beforeResolve {
            if (config.dependencies.empty) {
                config.dependencies.add(this.project.dependencies.create("org.jacoco:org.jacoco.ant:${extension.toolVersion}"))
            }
        }
    }

    /**
     * Applies the Jacoco agent to all tasks of type {@code Test}.
     * @param extension the extension to apply Jacoco with
     */
    private void applyToDefaultTasks(JacocoPluginExtension extension) {
        project.tasks.withType(Test) {
            extension.applyTo(it)
        }
    }

    /**
     * Adds report tasks for specific default test tasks.
     * @param extension the extension describing the test task names
     */
    private void addDefaultReportTasks(JacocoPluginExtension extension) {
        this.project.plugins.withType(JavaPlugin) {
            this.project.tasks.withType(Test) { task ->
                if (task.name == JavaPlugin.TEST_TASK_NAME) {
                    JacocoReport reportTask = this.project.tasks.create("jacoco${task.name.capitalize()}Report", JacocoReport)
                    reportTask.executionData task
                    reportTask.sourceSets(this.project.sourceSets.main)
                    reportTask.conventionMapping.with {
                        reportTask.reports.all { report ->
                            report.conventionMapping.with {
                                enabled = { true }
                                if (report.outputType == Report.OutputType.DIRECTORY) {
                                    destination = { new File(extension.reportsDir, "${task.name}/${report.name}") }
                                } else {
                                    destination = { new File(extension.reportsDir, "${task.name}/${reportTask.name}.${report.name}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
