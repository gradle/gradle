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
import org.apache.commons.lang.StringUtils

/**
 * <p>A  {@link org.gradle.api.Plugin}  which defines a basic project lifecycle and some common convention properties.</p>
 */
class BasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = 'clean'
    public static final String ASSEMBLE_TASK_NAME = 'assemble'
    public static final String BUILD_GROUP = 'build'
    public static final String UPLOAD_GROUP = 'upload'

    public void apply(Project project) {
        project.convention.plugins.base = new BasePluginConvention(project)

        configureBuildConfigurationRule(project)
        configureUploadRules(project)
        configureArchiveDefaults(project, project.convention.plugins.base)
        configureConfigurations(project)

        addClean(project)
        addCleanRule(project)
        addAssemble(project);
    }

    private Task addAssemble(Project project) {
        Task assembleTask = project.tasks.add(ASSEMBLE_TASK_NAME);
        assembleTask.description = "Assembles all Jar, War, Zip, and Tar archives.";
        assembleTask.group = BUILD_GROUP
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
        clean.group = BUILD_GROUP
        clean.delete { project.buildDir }
    }

    private void addCleanRule(Project project) {
        String prefix = 'clean'
        String description = "Pattern: ${prefix}<TaskName>: Cleans the output files of a task."
        Rule rule = [
                getDescription: { description },
                apply: {String taskName ->
                    if (!taskName.startsWith(prefix)) {
                        return
                    }
                    Task task = project.tasks.findByName(StringUtils.uncapitalize(taskName.substring(prefix.length())))
                    if (task == null) {
                        return
                    }
                    Delete clean = project.tasks.add(taskName, Delete)
                    clean.delete(task.outputs.files)
                },
                toString: { "Rule: " + description }
        ] as Rule

        project.tasks.addRule(rule)
    }

    private void configureBuildConfigurationRule(Project project) {
        String prefix = "build";
        String description = "Pattern: ${prefix}<ConfigurationName>: Assembles the artifacts of a configuration."
        Rule rule = [
                getDescription: {
                    description
                },
                apply: {String taskName ->
                    if (taskName.startsWith(prefix)) {
                        Configuration configuration = project.configurations.findByName(StringUtils.uncapitalize(taskName.substring(prefix.length())))
                        if (configuration != null) {
                            project.tasks.add(taskName).dependsOn(configuration.getBuildArtifacts()).setDescription(String.format("Builds the artifacts belonging to %s.", configuration))
                        }
                    }
                },
                toString: { "Rule: " + description }
        ] as Rule

        project.configurations.allObjects {
            if (!project.tasks.rules.contains(rule)) {
                project.tasks.addRule(rule)
            }
        }
    }

    private void configureUploadRules(final Project project) {
        String description = "Pattern: upload<ConfigurationName>: Assembles and uploads the artifacts belonging to a configuration."
        Rule rule = [
                getDescription: {
                    description
                },
                apply: {String taskName ->
                    Set<Configuration> configurations = project.configurations.all
                    for (Configuration configuration: configurations) {
                        if (taskName.equals(configuration.uploadTaskName)) {
                            createUploadTask(configuration.uploadTaskName, configuration, project)
                        }
                    }
                },
                toString: { "Rule: " + description }
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
        upload.description = "Uploads all artifacts belonging to $configuration."
        upload.group = UPLOAD_GROUP
        return upload
    }

    private void configureConfigurations(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        project.setProperty("status", "integration");

        Configuration archivesConfiguration = configurations.add(Dependency.ARCHIVES_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");

        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(archivesConfiguration).
                setDescription("Configuration for the default artifacts and their dependencies.");
    }
}
