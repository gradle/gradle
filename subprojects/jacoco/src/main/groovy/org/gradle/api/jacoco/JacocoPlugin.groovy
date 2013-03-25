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
package org.gradle.api.jacoco

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.sonar.SonarPlugin
import org.gradle.api.sonar.runner.SonarRunnerPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * Plugin that provides support for generating Jacoco coverage data.
 */
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
    @Inject JacocoPlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    void apply(Project project) {
        this.project = project
        configureJacocoConfigurations(project)
        JacocoAgentJar agent = instantiator.newInstance(JacocoAgentJar, project)
        JacocoPluginExtension extension = project.extensions.create(PLUGIN_EXTENSION_NAME, JacocoPluginExtension, project, agent)
        configureAgentDependencies(agent, extension)
        configureTaskClasspathDefaults(project, extension)
        applyToDefaultTasks(project, extension)
        addDefaultReportTasks(project, extension)
        configureSonarPlugin(project, extension)
    }

    /**
     * Creates the configurations used by plugin.
     * @param project the project to add the configurations to
     */
    private void configureJacocoConfigurations(Project project) {
        project.configurations.add(AGENT_CONFIGURATION_NAME).with {
            visible = false
            transitive = true
            description = 'The Jacoco agent to use to get coverage data.'
        }
        project.configurations.add(ANT_CONFIGURATION_NAME).with {
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
        jacocoAgentJar.conventionMapping.with {
            agentConf = {
                def config = project.configurations[AGENT_CONFIGURATION_NAME]
                if (config.dependencies.empty) {
                    project.dependencies {
                        jacocoAgent "org.jacoco:org.jacoco.agent:${extension.toolVersion}"
                    }
                }
                config
            }
        }
    }

    /**
     * Configures the classpath for Jacoco tasks using the 'jacocoAnt' configuration.
     * Uses the version declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly declared.
     * @param project the project to configure tasks for
     * @param extension the JacocoPluginExtension
     */
    private void configureTaskClasspathDefaults(Project project, JacocoPluginExtension extension) {
        project.tasks.withType(JacocoBase) { task ->
            task.conventionMapping.with {
                jacocoClasspath = {
                    def config = project.configurations[ANT_CONFIGURATION_NAME]
                    if (config.dependencies.empty) {
                        project.dependencies {
                            jacocoAnt "org.jacoco:org.jacoco.ant:${extension.toolVersion}"
                        }
                    }
                    config
                }
            }
        }
    }

    /**
     * Applies the Jacoco agent to all tasks of type {@code Test}.
     * @param project the project with the tasks to configure
     * @param extension the extension to apply Jacoco with
     */
    private void applyToDefaultTasks(Project project, JacocoPluginExtension extension) {
        extension.applyTo(project.tasks.withType(Test))
    }

    /**
     * Adds report tasks for specific default test tasks.
     * @param project the project to add default tasks to
     * @param extension the extension describing the test task names
     */
    private void addDefaultReportTasks(Project project, JacocoPluginExtension extension) {
        project.plugins.withType(JavaPlugin) {
            project.tasks.withType(Test) { task ->
                if (task.name in [extension.unitTestTaskName, extension.integrationTestTaskName]) {
                    JacocoReport reportTask = project.tasks.add("jacoco${task.name.capitalize()}Report", JacocoReport)
                    reportTask.executionData task
                    reportTask.sourceSets project.sourceSets.main
                }
            }
        }
    }

    /**
     * Configures default paths to Jacoco execution data for unit and
     * integration tests. Only configures them if tasks of the default
     * names exist. This is {@code test} for unit tests and either
     * {@code integTest} or {@code intTest} for integration tests.
     * @param currentProject the project to configure Sonar for
     * @param extension the extension describing the tes task names
     */
    private void configureSonarPlugin(Project currentProject, JacocoPluginExtension extension) {
        def configureTasks = { propertySetter ->
            currentProject.tasks.withType(Test) { task ->
                if (task.name == extension.unitTestTaskName) {
                    propertySetter('sonar.jacoco.reportPath', task.jacoco.destFile)
                } else if (task.name == extension.integrationTestTaskName) {
                    propertySetter('sonar.jacoco.itReportPath', task.jacoco.destFile)
                }
            }
        }

        // look for a project with a sonar plugin applied
        currentProject.rootProject.allprojects {
            project.plugins.withType(SonarPlugin) {
                currentProject.afterEvaluate {
                    currentProject.sonar.project.withProjectProperties { props ->
                        configureTasks { name, value -> props[name] = value }
                    }
                }
            }

            project.plugins.withType(SonarRunnerPlugin) {
                currentProject.afterEvaluate {
                    currentProject.sonarRunner.sonarProperties {
                        configureTasks { name, value -> property name, value }
                    }
                }
            }
        }
    }
}
