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

package org.gradle.plugin.devel.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * A plugin for validating java gradle plugins during the jar task.  Emits warnings for common error conditions.
 */
@Incubating
public class JavaGradlePluginPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JavaGradlePluginPlugin.class);
    static final String COMPILE_CONFIGURATION = "compile";
    static final String JAR_TASK = "jar";
    static final String GRADLE_PLUGINS = "gradle-plugins";
    static final String PLUGIN_DESCRIPTOR_PATTERN = "META-INF/" + GRADLE_PLUGINS + "/*.properties";
    static final String CLASSES_PATTERN = "**/*.class";
    static final String BAD_IMPL_CLASS_WARNING_MESSAGE = "A valid plugin descriptor was found for %s but the implementation class %s was not found in the jar.";
    static final String INVALID_DESCRIPTOR_WARNING_MESSAGE = "A plugin descriptor was found for %s but it was invalid.";
    static final String NO_DESCRIPTOR_WARNING_MESSAGE = "No valid plugin descriptors were found in META-INF/" + GRADLE_PLUGINS + "";

    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        applyDependencies(project);
        configureJarTask(project);
    }

    private void applyDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(COMPILE_CONFIGURATION, dependencies.gradleApi());
    }

    private void configureJarTask(Project project) {
        Jar jarTask = (Jar) project.getTasks().findByName(JAR_TASK);
        List<PluginDescriptor> descriptors = new ArrayList<PluginDescriptor>();
        Set<String> classList = new HashSet<String>();
        PluginDescriptorCollectorAction pluginDescriptorCollector = new PluginDescriptorCollectorAction(descriptors);
        ClassManifestCollectorAction classManifestCollector = new ClassManifestCollectorAction(classList);
        PluginValidationAction pluginValidationAction = new PluginValidationAction(descriptors, classList);

        jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorCollector);
        jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector);
        jarTask.doLast(pluginValidationAction);
    }

    /**
     * Implements plugin validation tasks to validate that a proper plugin jar is produced.
     */
    static class PluginValidationAction implements Action<Task>  {
        Collection<PluginDescriptor> descriptors;
        Set<String> classes;

        PluginValidationAction(Collection<PluginDescriptor> descriptors, Set<String> classes) {
            this.descriptors = descriptors;
            this.classes = classes;
        }

        public void execute(Task task) {
            if (descriptors == null || descriptors.isEmpty()) {
                LOGGER.warn(NO_DESCRIPTOR_WARNING_MESSAGE);
            } else {
                for (PluginDescriptor descriptor : descriptors) {
                    URI descriptorURI = null;
                    try {
                        descriptorURI = descriptor.getPropertiesFileUrl().toURI();
                    } catch (URISyntaxException e) {
                        // Do nothing since the only side effect is that we wouldn't
                        // be able to log the plugin descriptor file name.  Shouldn't
                        // be a reasonable scenario where this occurs since these
                        // descriptors should be generated from real files.
                    }
                    String pluginFileName = descriptorURI != null ? new File(descriptorURI).getName() : "UNKNOWN";
                    String pluginImplementation = descriptor.getImplementationClassName();
                    if (pluginImplementation.length() == 0) {
                        LOGGER.warn(String.format(INVALID_DESCRIPTOR_WARNING_MESSAGE, pluginFileName));
                    } else if (!hasFullyQualifiedClass(pluginImplementation)) {
                        LOGGER.warn(String.format(BAD_IMPL_CLASS_WARNING_MESSAGE, pluginFileName, pluginImplementation));
                    }
                }
            }
        }

        boolean hasFullyQualifiedClass(String fqClass) {
            return classes.contains(fqClass.replaceAll("\\.", "/") + ".class");
        }
    }

    /**
     * A file copy action that collects plugin descriptors as they are added to the jar.
     */
    static class PluginDescriptorCollectorAction implements Action<FileCopyDetails> {
        List<PluginDescriptor> descriptors;

        PluginDescriptorCollectorAction(List<PluginDescriptor> descriptors) {
            this.descriptors = descriptors;
        }

        public void execute(FileCopyDetails fileCopyDetails) {
            PluginDescriptor descriptor;
            try {
                descriptor = new PluginDescriptor(fileCopyDetails.getFile().toURI().toURL());
            } catch (MalformedURLException e) {
                // Not sure under what scenario (if any) this would occur,
                // but there's no sense in collecting the descriptor if it does.
                return;
            }
            if (descriptor.getImplementationClassName() != null) {
                descriptors.add(descriptor);
            }
        }
    }

    /**
     * A file copy action that collects class file paths as they are added to the jar.
     */
    static class ClassManifestCollectorAction implements Action<FileCopyDetails> {
        Set<String> classList;

        ClassManifestCollectorAction(Set<String> classList) {
            this.classList = classList;
        }

        public void execute(FileCopyDetails fileCopyDetails) {
            classList.add(fileCopyDetails.getRelativePath().toString());
        }
    }
}
