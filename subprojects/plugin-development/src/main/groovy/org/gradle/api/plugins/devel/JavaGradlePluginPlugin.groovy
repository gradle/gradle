/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.devel

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

@Incubating
class JavaGradlePluginPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JavaGradlePluginPlugin)
    static final String COMPILE_CONFIGURATION = 'compile'
    static final String JAR_TASK = 'jar'
    static final String GRADLE_PLUGINS = 'gradle-plugins'
    static final String IMPLEMENTATION_CLASS = 'implementation-class'
    static final String PLUGIN_DESCRIPTOR_PATTERN = "META-INF/${GRADLE_PLUGINS}/*.properties"
    static final String CLASSES_PATTERN = "**/*.class"
    static final String BAD_DESCRIPTOR_WARNING_MESSAGE = "A plugin descriptor was found at %s but the implementation class %s was not found in the jar."
    static final String NO_DESCRIPTOR_WARNING_MESSAGE = "No plugin descriptor was found in META-INF/${GRADLE_PLUGINS}."

    @Override
    void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        applyDependencies(project)
        configureJarTask(project)
    }

    private void applyDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies()
        dependencies.add(COMPILE_CONFIGURATION, dependencies.gradleApi())
    }

    private void configureJarTask(Project project) {
        def Jar jarTask = project.getTasks().findByName(JAR_TASK)
        def pluginDescriptorFinder = new FindPluginDescriptorAction()
        def classManifestCollector = new ClassManifestCollectorAction()
        jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorFinder)
        jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector)
        jarTask.doLast {
            if (! pluginDescriptorFinder.foundDescriptor) {
                LOGGER.warn(NO_DESCRIPTOR_WARNING_MESSAGE)
            } else if (! classManifestCollector.hasFullyQualifiedClass(pluginDescriptorFinder.pluginImplementation)) {
                LOGGER.warn(String.format(BAD_DESCRIPTOR_WARNING_MESSAGE, pluginDescriptorFinder.descriptorLocation, pluginDescriptorFinder.pluginImplementation))
            }
        }
    }

    static class FindPluginDescriptorAction implements Action<FileCopyDetails> {
        boolean foundDescriptor = false
        String pluginImplementation
        String descriptorLocation

        @Override
        void execute(FileCopyDetails fileCopyDetails) {
            Properties properties = new Properties()
            properties.load(fileCopyDetails.open())
            if (properties.getProperty(IMPLEMENTATION_CLASS, '').length() > 0) {
                foundDescriptor = true
                pluginImplementation = properties.getProperty(IMPLEMENTATION_CLASS)
                descriptorLocation = fileCopyDetails.relativePath.toString()
            }
        }
    }

    static class ClassManifestCollectorAction implements Action<FileCopyDetails> {
        HashSet<String> classList

        ClassManifestCollectorAction() {
            classList = new HashSet<String>()
        }

        ClassManifestCollectorAction(Collection<String> c) {
            classList = new HashSet<String>(c)
        }

        @Override
        void execute(FileCopyDetails fileCopyDetails) {
            classList.add(fileCopyDetails.relativePath.toString())
        }

        boolean hasFullyQualifiedClass(String fqClass) {
            return classList.contains(fqClass.replaceAll('\\.','/') + '.class')
        }
    }
}
