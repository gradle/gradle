/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency

/**
 * <p>A  {@link org.gradle.api.Plugin}  which defines a basic project lifecycle and some common convention properties.</p>
 */
class BasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = "clean"
    public static final String ASSEMBLE_TASK_NAME = "assemble"

    public void use(Project project) {
        project.convention.plugins.base = new BasePluginConvention(project)

        configureBuildConfigurationRule(project)
        configureUploadRules(project)
        configureArchiveDefaults(project, project.convention.plugins.base)
        configureConfigurations(project)
        
        addClean(project)
        addAssemble(project);
    }

    private Task addAssemble(Project project) {
        Task assembleTask = project.tasks.add(ASSEMBLE_TASK_NAME);
        assembleTask.description = "Builds all Jar, War, Zip, and Tar archives.";
        assembleTask.dependsOn { project.tasks.withType(AbstractArchiveTask.class).all }
    }

    private void configureArchiveDefaults(Project project, BasePluginConvention pluginConvention) {
        project.tasks.withType(AbstractArchiveTask).allTasks {AbstractArchiveTask task ->
            if (task instanceof Jar) {
                task.conventionMapping.destinationDir = { pluginConvention.libsDir }
            } else {
                task.conventionMapping.destinationDir = { pluginConvention.distsDir }
            }
            task.conventionMapping.version = { project.version == Project.DEFAULT_VERSION ? null : project.version.toString() }
            task.conventionMapping.baseName = { pluginConvention.archivesBaseName }
        }
    }

    private void addClean(final Project project) {
        Delete clean = project.tasks.add(CLEAN_TASK_NAME, Delete.class)
        clean.description = "Deletes the build directory.";
        clean.from { project.buildDir }
    }

    private void configureBuildConfigurationRule(final Project project) {
        final String prefix = "build";
        Rule rule = [
                getDescription: {
                    String.format("Pattern: %s<ConfigurationName>: Builds the artifacts belonging to the configuration.", prefix)
                },
                apply: {String taskName ->
                    if (taskName.startsWith(prefix)) {
                        Configuration configuration = project.configurations.findByName(taskName.substring(prefix.length()).toLowerCase())
                        if (configuration != null) {
                            project.tasks.add(taskName).dependsOn(configuration.getBuildArtifacts()).setDescription(String.format("Builds the artifacts belonging to %s.", configuration))
                        }
                    }
                }
        ] as Rule

        project.configurations.allObjects {
            if (!project.tasks.rules.contains(rule)) {
                project.tasks.addRule(rule)
            }
        }
    }

    private void configureUploadRules(final Project project) {
        Rule rule = [
                getDescription: {
                    "Pattern: upload<ConfigurationName>: Uploads the project artifacts of a configuration to a public Gradle repository."
                },
                apply: {String taskName ->
                    Set<Configuration> configurations = project.configurations.all
                    for (Configuration configuration: configurations) {
                        if (taskName.equals(configuration.uploadTaskName)) {
                            createUploadTask(configuration.uploadTaskName, configuration, project)
                        }
                    }
                }
        ] as Rule

        project.configurations.allObjects {
            if (!project.tasks.rules.contains(rule)) {
                project.tasks.addRule(rule)
            }
        }
    }

    private Upload createUploadTask(String name, final Configuration configuration, Project project) {
        Upload upload = project.getTasks().add(name, Upload.class)
        upload.configuration = configuration
        upload.uploadDescriptor = true
        upload.descriptorDestination = new File(project.getBuildDir(), "ivy.xml")
        upload.description = String.format("Uploads all artifacts belonging to %s.", configuration)
        return upload
    }

    private void configureConfigurations(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        project.setProperty("status", "integration");

        Configuration archivesConfiguration = configurations.add(Dependency.ARCHIVES_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");

        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(archivesConfiguration).
                setDescription("Configuration the default artifacts and its dependencies.");
    }


}
