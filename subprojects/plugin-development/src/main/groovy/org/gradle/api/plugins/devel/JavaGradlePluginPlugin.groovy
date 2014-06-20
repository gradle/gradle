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
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.plugins.PluginDescriptor
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
    static final String PLUGIN_DESCRIPTOR_PATTERN = "META-INF/${GRADLE_PLUGINS}/*.properties"
    static final String CLASSES_PATTERN = "**/*.class"
    static final String BAD_IMPL_CLASS_WARNING_MESSAGE = "A valid plugin descriptor was found for %s but the implementation class %s was not found in the jar."
    static final String INVALID_DESCRIPTOR_WARNING_MESSAGE = "A plugin descriptor was found for %s but it was invalid."
    static final String NO_DESCRIPTOR_WARNING_MESSAGE = "No valid plugin descriptors were found in META-INF/${GRADLE_PLUGINS}."

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
        def pluginValidationAction = new PluginValidationAction(pluginDescriptorFinder, classManifestCollector)
        jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorFinder)
        jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector)
        jarTask.doLast(pluginValidationAction)
    }

    static class PluginValidationAction implements Action<Task>  {
        FindPluginDescriptorAction pluginDescriptorFinder
        ClassManifestCollectorAction classManifestCollector

        PluginValidationAction(FindPluginDescriptorAction pluginDescriptorFinder, ClassManifestCollectorAction classManifestCollector) {
            this.pluginDescriptorFinder = pluginDescriptorFinder
            this.classManifestCollector = classManifestCollector
        }

        @Override
        void execute(Task task) {
            if (pluginDescriptorFinder.descriptors.isEmpty()) {
                LOGGER.warn(NO_DESCRIPTOR_WARNING_MESSAGE)
            } else {
                pluginDescriptorFinder.descriptors.each { PluginDescriptor descriptor ->
                    URI descriptorURI = new URL(descriptor.toString()).toURI()
                    String pluginName = new File(descriptorURI).getName()
                    String pluginImplementation = descriptor.implementationClassName
                    if (pluginImplementation.length() == 0) {
                        LOGGER.warn(String.format(INVALID_DESCRIPTOR_WARNING_MESSAGE, pluginName))
                    } else if (! classManifestCollector.hasFullyQualifiedClass(pluginImplementation)) {
                        LOGGER.warn(String.format(BAD_IMPL_CLASS_WARNING_MESSAGE, pluginName, pluginImplementation))
                    }
                }
            }
        }
    }

    static class FindPluginDescriptorAction implements Action<FileCopyDetails> {
        List<PluginDescriptor> descriptors = []

        @Override
        void execute(FileCopyDetails fileCopyDetails) {
            PluginDescriptor descriptor = new PluginDescriptor(fileCopyDetails.file.toURI().toURL())
            if (descriptor.implementationClassName != null) {
                descriptors.add(descriptor)
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
