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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.model.Model;
import org.gradle.model.RuleSource;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.gradle.plugin.devel.tasks.GeneratePluginDescriptors;
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata;
import org.gradle.plugin.devel.tasks.ValidateTaskProperties;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin for building java gradle plugins. Automatically generates plugin descriptors. Emits warnings for common error conditions. <p> Provides a direct integration with TestKit by declaring the
 * {@code gradleTestKit()} dependency for the test compile configuration and a dependency on the plugin classpath manifest generation task for the test runtime configuration. Default conventions can
 * be customized with the help of {@link GradlePluginDevelopmentExtension}.
 *
 * Integrates with the 'maven-publish' and 'ivy-publish' plugins to automatically publish the plugins so they can be resolved using the `pluginRepositories` and `plugins` DSL.
 */
@Incubating
public class JavaGradlePluginPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JavaGradlePluginPlugin.class);
    static final String COMPILE_CONFIGURATION = "compile";
    static final String JAR_TASK = "jar";
    static final String PROCESS_RESOURCES_TASK = "processResources";
    static final String GRADLE_PLUGINS = "gradle-plugins";
    static final String PLUGIN_DESCRIPTOR_PATTERN = "META-INF/" + GRADLE_PLUGINS + "/*.properties";
    static final String CLASSES_PATTERN = "**/*.class";
    static final String BAD_IMPL_CLASS_WARNING_MESSAGE = "%s: A valid plugin descriptor was found for %s but the implementation class %s was not found in the jar.";
    static final String INVALID_DESCRIPTOR_WARNING_MESSAGE = "%s: A plugin descriptor was found for %s but it was invalid.";
    static final String NO_DESCRIPTOR_WARNING_MESSAGE = "%s: No valid plugin descriptors were found in META-INF/" + GRADLE_PLUGINS + "";
    static final String DECLARED_PLUGIN_MISSING_MESSAGE = "%s: Could not find plugin descriptor of %s at META-INF/" + GRADLE_PLUGINS + "/%s.properties";
    static final String DECLARATION_MISSING_ID_MESSAGE = "Missing id for %s";
    static final String DECLARATION_MISSING_IMPLEMENTATION_MESSAGE = "Missing implementationClass for %s";
    static final String EXTENSION_NAME = "gradlePlugin";
    static final String PLUGIN_UNDER_TEST_METADATA_TASK_NAME = "pluginUnderTestMetadata";
    static final String GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME = "pluginDescriptors";
    static final String VALIDATE_TASK_PROPERTIES_TASK_NAME = "validateTaskProperties";

    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        applyDependencies(project);
        GradlePluginDevelopmentExtension extension = createExtension(project);
        configureJarTask(project, extension);
        configureTestKit(project, extension);
        configurePublishing(project);
        configureDescriptorGeneration(project, extension);
        validatePluginDeclarations(project, extension);
        configureTaskPropertiesValidation(project);
    }

    private void applyDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(COMPILE_CONFIGURATION, dependencies.gradleApi());
    }

    private void configureJarTask(Project project, GradlePluginDevelopmentExtension extension) {
        Jar jarTask = (Jar) project.getTasks().findByName(JAR_TASK);
        List<PluginDescriptor> descriptors = new ArrayList<PluginDescriptor>();
        Set<String> classList = new HashSet<String>();
        PluginDescriptorCollectorAction pluginDescriptorCollector = new PluginDescriptorCollectorAction(descriptors);
        ClassManifestCollectorAction classManifestCollector = new ClassManifestCollectorAction(classList);
        PluginValidationAction pluginValidationAction = new PluginValidationAction(extension.getPlugins(), descriptors, classList);

        jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorCollector);
        jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector);
        jarTask.appendParallelSafeAction(pluginValidationAction);
    }

    private GradlePluginDevelopmentExtension createExtension(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet defaultPluginSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet defaultTestSourceSet = javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        return project.getExtensions().create(EXTENSION_NAME, GradlePluginDevelopmentExtension.class, project, defaultPluginSourceSet, defaultTestSourceSet);
    }

    private void configureTestKit(Project project, GradlePluginDevelopmentExtension extension) {
        PluginUnderTestMetadata pluginUnderTestMetadataTask = createAndConfigurePluginUnderTestMetadataTask(project, extension);
        establishTestKitAndPluginClasspathDependencies(project, extension, pluginUnderTestMetadataTask);
    }

    private PluginUnderTestMetadata createAndConfigurePluginUnderTestMetadataTask(final Project project, final GradlePluginDevelopmentExtension extension) {
        final PluginUnderTestMetadata pluginUnderTestMetadataTask = project.getTasks().create(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata.class);
        final Configuration gradlePluginConfiguration = project.getConfigurations().detachedConfiguration(project.getDependencies().gradleApi());

        ConventionMapping conventionMapping = new DslObject(pluginUnderTestMetadataTask).getConventionMapping();
        conventionMapping.map("pluginClasspath", new Callable<Object>() {
            public Object call() throws Exception {
                FileCollection gradleApi = gradlePluginConfiguration.getIncoming().getFiles();
                return extension.getPluginSourceSet().getRuntimeClasspath().minus(gradleApi);
            }
        });
        conventionMapping.map("outputDirectory", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), pluginUnderTestMetadataTask.getName());
            }
        });

        return pluginUnderTestMetadataTask;
    }

    private void establishTestKitAndPluginClasspathDependencies(Project project, GradlePluginDevelopmentExtension extension, PluginUnderTestMetadata pluginClasspathTask) {
        project.afterEvaluate(new TestKitAndPluginClasspathDependenciesAction(extension, pluginClasspathTask));
    }

    private void configurePublishing(final Project project) {
        project.getPluginManager().withPlugin("maven-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                project.getPluginManager().apply(MavenPluginPublishingRules.class);
            }
        });
        project.getPluginManager().withPlugin("ivy-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                project.getPluginManager().apply(IvyPluginPublishingRules.class);
            }
        });
    }

    private void configureDescriptorGeneration(final Project project, final GradlePluginDevelopmentExtension extension) {
        final GeneratePluginDescriptors generatePluginDescriptors = project.getTasks().create(GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME, GeneratePluginDescriptors.class);
        generatePluginDescriptors.conventionMapping("declarations", new Callable<List<PluginDeclaration>>() {
            @Override
            public List<PluginDeclaration> call() throws Exception {
                return Lists.newArrayList(extension.getPlugins());
            }
        });
        generatePluginDescriptors.conventionMapping("outputDirectory", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return new File(project.getBuildDir(), generatePluginDescriptors.getName());
            }
        });
        Copy processResources = (Copy) project.getTasks().getByName(PROCESS_RESOURCES_TASK);
        CopySpec copyPluginDescriptors = processResources.getRootSpec().addChild();
        copyPluginDescriptors.into("META-INF/gradle-plugins");
        copyPluginDescriptors.from(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return generatePluginDescriptors.getOutputDirectory();
            }
        });
        processResources.dependsOn(generatePluginDescriptors);
    }

    private void validatePluginDeclarations(Project project, final GradlePluginDevelopmentExtension extension) {
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                for (PluginDeclaration declaration : extension.getPlugins()) {
                    if (declaration.getId() == null) {
                        throw new IllegalArgumentException(String.format(DECLARATION_MISSING_ID_MESSAGE, declaration.getName()));
                    }
                    if (declaration.getImplementationClass() == null) {
                        throw new IllegalArgumentException(String.format(DECLARATION_MISSING_IMPLEMENTATION_MESSAGE, declaration.getName()));
                    }
                }
            }
        });
    }

    private void configureTaskPropertiesValidation(Project project) {
        ValidateTaskProperties validator = project.getTasks().create(VALIDATE_TASK_PROPERTIES_TASK_NAME, ValidateTaskProperties.class);

        File reportsDir = new File(project.getBuildDir(), "reports");
        File validatorReportsDir = new File(reportsDir, "task-properties");
        validator.setOutputFile(new File(validatorReportsDir, "report.txt"));

        SourceSet mainSourceSet = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        validator.setClasspath(mainSourceSet.getCompileClasspath());
        validator.setClassesDir(mainSourceSet.getOutput().getClassesDir());
        validator.dependsOn(mainSourceSet.getOutput());

        project.getTasks().getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(validator);
    }

    /**
     * Implements plugin validation tasks to validate that a proper plugin jar is produced.
     */
    static class PluginValidationAction implements Action<Task> {
        private final Collection<PluginDeclaration> plugins;
        private final Collection<PluginDescriptor> descriptors;
        private final Set<String> classes;

        PluginValidationAction(Collection<PluginDeclaration> plugins, Collection<PluginDescriptor> descriptors, Set<String> classes) {
            this.plugins = plugins;
            this.descriptors = descriptors;
            this.classes = classes;
        }

        public void execute(Task task) {
            if (descriptors == null || descriptors.isEmpty()) {
                LOGGER.warn(String.format(NO_DESCRIPTOR_WARNING_MESSAGE, task.getPath()));
            } else {
                Set<String> pluginFileNames = Sets.newHashSet();
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
                    pluginFileNames.add(pluginFileName);
                    String pluginImplementation = descriptor.getImplementationClassName();
                    if (pluginImplementation.length() == 0) {
                        LOGGER.warn(String.format(INVALID_DESCRIPTOR_WARNING_MESSAGE, task.getPath(), pluginFileName));
                    } else if (!hasFullyQualifiedClass(pluginImplementation)) {
                        LOGGER.warn(String.format(BAD_IMPL_CLASS_WARNING_MESSAGE, task.getPath(), pluginFileName, pluginImplementation));
                    }
                }
                for (PluginDeclaration declaration : plugins) {
                    if (!pluginFileNames.contains(declaration.getId() + ".properties")) {
                        LOGGER.warn(String.format(DECLARED_PLUGIN_MISSING_MESSAGE, task.getPath(), declaration.getName(), declaration.getId()));
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

    /**
     * An action that automatically declares the TestKit dependency for the test compile configuration and a dependency
     * on the plugin classpath manifest generation task for the test runtime configuration.
     */
    static class TestKitAndPluginClasspathDependenciesAction implements Action<Project> {
        private final GradlePluginDevelopmentExtension extension;
        private final PluginUnderTestMetadata pluginClasspathTask;

        private TestKitAndPluginClasspathDependenciesAction(GradlePluginDevelopmentExtension extension, PluginUnderTestMetadata pluginClasspathTask) {
            this.extension = extension;
            this.pluginClasspathTask = pluginClasspathTask;
        }

        @Override
        public void execute(Project project) {
            DependencyHandler dependencies = project.getDependencies();
            Set<SourceSet> testSourceSets = extension.getTestSourceSets();

            for (SourceSet testSourceSet : testSourceSets) {
                String compileConfigurationName = testSourceSet.getCompileConfigurationName();
                dependencies.add(compileConfigurationName, dependencies.gradleTestKit());
                String runtimeConfigurationName = testSourceSet.getRuntimeConfigurationName();
                dependencies.add(runtimeConfigurationName, project.files(pluginClasspathTask));
            }
        }
    }

    static class Rules extends RuleSource {
        @Model
        public GradlePluginDevelopmentExtension gradlePluginDevelopmentExtension(ExtensionContainer extensionContainer) {
            return extensionContainer.getByType(GradlePluginDevelopmentExtension.class);
        }
    }

}
