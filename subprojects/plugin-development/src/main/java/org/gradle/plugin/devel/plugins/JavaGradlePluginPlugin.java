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

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.gradle.plugin.devel.tasks.GeneratePluginDescriptors;
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata;
import org.gradle.plugin.devel.tasks.ValidateTaskProperties;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.plugin.use.resolve.internal.local.PluginPublication;

import javax.annotation.Nullable;
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
@NonNullApi
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

    /**
     * The task group used for tasks created by the Java Gradle plugin development plugin.
     *
     * @since 4.0
     */
    static final String PLUGIN_DEVELOPMENT_GROUP = "Plugin development";

    /**
     * The description for the task generating metadata for plugin functional tests.
     *
     * @since 4.0
     */
    static final String PLUGIN_UNDER_TEST_METADATA_TASK_DESCRIPTION = "Generates the metadata for plugin functional tests.";

    /**
     * The description for the task generating plugin descriptors from plugin declarations.
     *
     * @since 4.0
     */
    static final String GENERATE_PLUGIN_DESCRIPTORS_TASK_DESCRIPTION = "Generates plugin descriptors from plugin declarations.";

    /**
     * The description for the task validating task property annotations for the plugin.
     *
     * @since 4.0
     */
    static final String VALIDATE_TASK_PROPERTIES_TASK_DESCRIPTION = "Validates task property annotations for the plugin.";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        applyDependencies(project);
        GradlePluginDevelopmentExtension extension = createExtension(project);
        configureJarTask(project, extension);
        configureTestKit(project, extension);
        configurePublishing(project);
        registerPlugins(project, extension);
        configureDescriptorGeneration(project, extension);
        validatePluginDeclarations(project, extension);
        configureTaskPropertiesValidation(project);
    }

    private void registerPlugins(Project project, GradlePluginDevelopmentExtension extension) {
        ProjectInternal projectInternal = (ProjectInternal) project;
        ProjectPublicationRegistry registry = projectInternal.getServices().get(ProjectPublicationRegistry.class);
        extension.getPlugins().all(new Action<PluginDeclaration>() {
            @Override
            public void execute(PluginDeclaration pluginDeclaration) {
                registry.registerPublication(projectInternal, new LocalPluginPublication(pluginDeclaration));
            }
        });
    }

    private void applyDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(COMPILE_CONFIGURATION, dependencies.gradleApi());
    }

    private void configureJarTask(Project project, final GradlePluginDevelopmentExtension extension) {
        project.getTasks().named(JAR_TASK, Jar.class, new Action<Jar>() {
            @Override
            public void execute(Jar jarTask) {
                List<PluginDescriptor> descriptors = new ArrayList<PluginDescriptor>();
                Set<String> classList = new HashSet<String>();
                PluginDescriptorCollectorAction pluginDescriptorCollector = new PluginDescriptorCollectorAction(descriptors);
                ClassManifestCollectorAction classManifestCollector = new ClassManifestCollectorAction(classList);
                PluginValidationAction pluginValidationAction = new PluginValidationAction(extension.getPlugins(), descriptors, classList);

                jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorCollector);
                jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector);
                jarTask.appendParallelSafeAction(pluginValidationAction);
            }
        });
    }

    private GradlePluginDevelopmentExtension createExtension(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet defaultPluginSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet defaultTestSourceSet = javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        return project.getExtensions().create(EXTENSION_NAME, GradlePluginDevelopmentExtension.class, project, defaultPluginSourceSet, defaultTestSourceSet);
    }

    private void configureTestKit(Project project, GradlePluginDevelopmentExtension extension) {
        TaskProvider<PluginUnderTestMetadata> pluginUnderTestMetadataTask = createAndConfigurePluginUnderTestMetadataTask(project, extension);
        establishTestKitAndPluginClasspathDependencies(project, extension, pluginUnderTestMetadataTask);
    }

    private TaskProvider<PluginUnderTestMetadata> createAndConfigurePluginUnderTestMetadataTask(final Project project, final GradlePluginDevelopmentExtension extension) {
        return project.getTasks().register(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata.class, new Action<PluginUnderTestMetadata>() {
            @Override
            public void execute(final PluginUnderTestMetadata pluginUnderTestMetadataTask) {
                pluginUnderTestMetadataTask.setGroup(PLUGIN_DEVELOPMENT_GROUP);
                pluginUnderTestMetadataTask.setDescription(PLUGIN_UNDER_TEST_METADATA_TASK_DESCRIPTION);

                pluginUnderTestMetadataTask.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(pluginUnderTestMetadataTask.getName()));
                pluginUnderTestMetadataTask.getPluginClasspath().from(new Callable<Object>() {
                    @Override
                    public Object call() {
                        final Configuration gradlePluginConfiguration = project.getConfigurations().detachedConfiguration(project.getDependencies().gradleApi());
                        FileCollection gradleApi = gradlePluginConfiguration.getIncoming().getFiles();
                        return extension.getPluginSourceSet().getRuntimeClasspath().minus(gradleApi);
                    }
                });
            }
        });
    }

    private void establishTestKitAndPluginClasspathDependencies(Project project, GradlePluginDevelopmentExtension extension, TaskProvider<PluginUnderTestMetadata> pluginClasspathTask) {
        project.afterEvaluate(new TestKitAndPluginClasspathDependenciesAction(extension, pluginClasspathTask));
    }

    private void configurePublishing(final Project project) {
        project.getPluginManager().withPlugin("maven-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                project.getPluginManager().apply(MavenPluginPublishPlugin.class);
            }
        });
        project.getPluginManager().withPlugin("ivy-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                project.getPluginManager().apply(IvyPluginPublishingPlugin.class);
            }
        });
    }

    private void configureDescriptorGeneration(final Project project, final GradlePluginDevelopmentExtension extension) {
        final TaskProvider<GeneratePluginDescriptors> generatePluginDescriptors = project.getTasks().register(GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME, GeneratePluginDescriptors.class, new Action<GeneratePluginDescriptors>() {
            @Override
            public void execute(final GeneratePluginDescriptors generatePluginDescriptors) {
                generatePluginDescriptors.setGroup(PLUGIN_DEVELOPMENT_GROUP);
                generatePluginDescriptors.setDescription(GENERATE_PLUGIN_DESCRIPTORS_TASK_DESCRIPTION);
                generatePluginDescriptors.getDeclarations().set(extension.getPlugins());
                generatePluginDescriptors.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(generatePluginDescriptors.getName()));
            }
        });
        project.getTasks().named(PROCESS_RESOURCES_TASK, Copy.class, new Action<Copy>() {
            @Override
            public void execute(Copy processResources) {
                CopySpec copyPluginDescriptors = processResources.getRootSpec().addChild();
                copyPluginDescriptors.into("META-INF/gradle-plugins");
                copyPluginDescriptors.from(generatePluginDescriptors);
            }
        });
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

    private void configureTaskPropertiesValidation(final Project project) {
        final SourceSet mainSourceSet = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final TaskProvider<ValidateTaskProperties> validator = project.getTasks().register(VALIDATE_TASK_PROPERTIES_TASK_NAME, ValidateTaskProperties.class, new Action<ValidateTaskProperties>() {
            @Override
            public void execute(ValidateTaskProperties validator) {
                validator.setGroup(PLUGIN_DEVELOPMENT_GROUP);
                validator.setDescription(VALIDATE_TASK_PROPERTIES_TASK_DESCRIPTION);

                validator.getOutputFile().set(project.getLayout().getBuildDirectory().file("reports/task-properties/report.txt"));

                validator.getClasses().setFrom(mainSourceSet.getOutput().getClassesDirs());
                validator.getClasspath().setFrom(mainSourceSet.getCompileClasspath());
                validator.dependsOn(mainSourceSet.getOutput());
            }
        });

        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task check) {
                check.dependsOn(validator);
            }
        });
    }

    /**
     * Implements plugin validation tasks to validate that a proper plugin jar is produced.
     */
    static class PluginValidationAction implements Action<Task> {
        private final Collection<PluginDeclaration> plugins;
        private final Collection<PluginDescriptor> descriptors;
        private final Set<String> classes;

        PluginValidationAction(Collection<PluginDeclaration> plugins, @Nullable Collection<PluginDescriptor> descriptors, Set<String> classes) {
            this.plugins = plugins;
            this.descriptors = descriptors;
            this.classes = classes;
        }

        @Override
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

        @Override
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

        @Override
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
        private final TaskProvider<PluginUnderTestMetadata> pluginClasspathTask;

        private TestKitAndPluginClasspathDependenciesAction(GradlePluginDevelopmentExtension extension, TaskProvider<PluginUnderTestMetadata> pluginClasspathTask) {
            this.extension = extension;
            this.pluginClasspathTask = pluginClasspathTask;
        }

        @Override
        public void execute(Project project) {
            DependencyHandler dependencies = project.getDependencies();
            Set<SourceSet> testSourceSets = extension.getTestSourceSets();
            project.getNormalization().getRuntimeClasspath().ignore(PluginUnderTestMetadata.METADATA_FILE_NAME);

            project.getTasks().withType(Test.class).configureEach(new Action<Test>() {
                @Override
                public void execute(Test test) {
                    test.getInputs().files(pluginClasspathTask.get().getPluginClasspath())
                        .withPropertyName("pluginClasspath")
                        .withNormalizer(ClasspathNormalizer.class);
                }
            });

            for (SourceSet testSourceSet : testSourceSets) {
                String compileConfigurationName = testSourceSet.getCompileConfigurationName();
                dependencies.add(compileConfigurationName, dependencies.gradleTestKit());
                String runtimeConfigurationName = testSourceSet.getRuntimeConfigurationName();
                dependencies.add(runtimeConfigurationName, project.getLayout().files(pluginClasspathTask));
            }
        }
    }

    private static class LocalPluginPublication implements PluginPublication {
        private final PluginDeclaration pluginDeclaration;

        LocalPluginPublication(PluginDeclaration pluginDeclaration) {
            this.pluginDeclaration = pluginDeclaration;
        }

        @Override
        public DisplayName getDisplayName() {
            return Describables.withTypeAndName("plugin", pluginDeclaration.getName());
        }

        @Override
        public PluginId getPluginId() {
            return DefaultPluginId.of(pluginDeclaration.getId());
        }
    }
}
