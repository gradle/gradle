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
import org.gradle.api.JavaVersion;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.initialization.buildsrc.GradlePluginApiVersionAttributeConfigurationAction;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;
import org.gradle.plugin.devel.tasks.GeneratePluginDescriptors;
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata;
import org.gradle.plugin.devel.tasks.ValidatePlugins;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.plugin.use.resolve.internal.local.PluginPublication;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * A plugin for building java gradle plugins. Automatically generates plugin descriptors. Emits warnings for common error conditions. <p> Provides a direct integration with TestKit by declaring the
 * {@code gradleTestKit()} dependency for the test compile configuration and a dependency on the plugin classpath manifest generation task for the test runtime configuration. Default conventions can
 * be customized with the help of {@link GradlePluginDevelopmentExtension}.
 *
 * Integrates with the 'maven-publish' and 'ivy-publish' plugins to automatically publish the plugins so they can be resolved using the `pluginRepositories` and `plugins` DSL.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/java_gradle_plugin.html">Gradle plugin development reference</a>
 */
@NonNullApi
public abstract class JavaGradlePluginPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JavaGradlePluginPlugin.class);
    static final String API_CONFIGURATION = JvmConstants.API_CONFIGURATION_NAME;
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
    static final String VALIDATE_PLUGINS_TASK_NAME = "validatePlugins";

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
     * The description for the task validating the plugin.
     *
     * @since 6.0
     */
    static final String VALIDATE_PLUGIN_TASK_DESCRIPTION = "Validates the plugin by checking parameter annotations on task and artifact transform types etc.";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        applyDependencies(project);
        GradlePluginDevelopmentExtension extension = createExtension(project);
        configureJarTask(project, extension);
        configureTestKit(project, extension);
        configurePublishing(project);
        registerPlugins(project, extension);
        configureDescriptorGeneration(project, extension);
        validatePluginDeclarations(project, extension);
        configurePluginValidations(project, extension);
        configureDependencyGradlePluginsResolution(project);
    }

    private void registerPlugins(Project project, GradlePluginDevelopmentExtension extension) {
        ProjectInternal projectInternal = (ProjectInternal) project;
        ProjectPublicationRegistry registry = projectInternal.getServices().get(ProjectPublicationRegistry.class);
        extension.getPlugins().all(pluginDeclaration -> registry.registerPublication(projectInternal, new LocalPluginPublication(pluginDeclaration)));
    }

    private void applyDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(API_CONFIGURATION, dependencies.gradleApi());
    }

    private void configureJarTask(Project project, GradlePluginDevelopmentExtension extension) {
        project.getTasks().named(JAR_TASK, Jar.class, jarTask -> {
            PluginValidationActionsState actionsState = new PluginValidationActionsState();
            PluginDescriptorCollectorAction pluginDescriptorCollector = new PluginDescriptorCollectorAction(actionsState);
            ClassManifestCollectorAction classManifestCollector = new ClassManifestCollectorAction(actionsState);
            Provider<Collection<PluginDeclaration>> pluginsProvider = project.provider(() -> extension.getPlugins().getAsMap().values());
            PluginValidationAction pluginValidationAction = new PluginValidationAction(pluginsProvider, actionsState);

            jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorCollector);
            jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector);
            jarTask.appendParallelSafeAction(pluginValidationAction);
        });
    }

    private GradlePluginDevelopmentExtension createExtension(Project project) {
        SourceSet defaultPluginSourceSet = JavaPluginHelper.getJavaComponent(project).getMainFeature().getSourceSet();
        SourceSet defaultTestSourceSet = JavaPluginHelper.getDefaultTestSuite(project).getSources();
        return project.getExtensions().create(EXTENSION_NAME, GradlePluginDevelopmentExtension.class, project, defaultPluginSourceSet, defaultTestSourceSet);
    }

    private void configureTestKit(Project project, GradlePluginDevelopmentExtension extension) {
        TaskProvider<PluginUnderTestMetadata> pluginUnderTestMetadataTask = createAndConfigurePluginUnderTestMetadataTask(project, extension);
        establishTestKitAndPluginClasspathDependencies(project, extension, pluginUnderTestMetadataTask);
    }

    private TaskProvider<PluginUnderTestMetadata> createAndConfigurePluginUnderTestMetadataTask(Project project, GradlePluginDevelopmentExtension extension) {
        return project.getTasks().register(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata.class, pluginUnderTestMetadataTask -> {
            pluginUnderTestMetadataTask.setGroup(PLUGIN_DEVELOPMENT_GROUP);
            pluginUnderTestMetadataTask.setDescription(PLUGIN_UNDER_TEST_METADATA_TASK_DESCRIPTION);

            pluginUnderTestMetadataTask.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(pluginUnderTestMetadataTask.getName()));

            pluginUnderTestMetadataTask.getPluginClasspath().from(classpathForPlugin(project, extension));
        });
    }

    private static Callable<FileCollection> classpathForPlugin(Project project, GradlePluginDevelopmentExtension extension) {
        return () -> {
            SourceSet sourceSet = extension.getPluginSourceSet();
            Configuration runtimeClasspath = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
            ArtifactView view = runtimeClasspath.getIncoming().artifactView(config ->
                config.componentFilter(spec(JavaGradlePluginPlugin::excludeGradleApi))
            );
            return project.getObjects().fileCollection().from(
                sourceSet.getOutput(),
                view.getFiles().getElements()
            );
        };
    }

    private static boolean excludeGradleApi(ComponentIdentifier componentId) {
        if (componentId instanceof OpaqueComponentIdentifier) {
            DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
            return classPathNotation != DependencyFactoryInternal.ClassPathNotation.GRADLE_API && classPathNotation != DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;
        }
        return true;
    }

    private void establishTestKitAndPluginClasspathDependencies(Project project, GradlePluginDevelopmentExtension extension, TaskProvider<PluginUnderTestMetadata> pluginClasspathTask) {
        project.afterEvaluate(new TestKitAndPluginClasspathDependenciesAction(extension, pluginClasspathTask));
    }

    private void configurePublishing(Project project) {
        project.getPluginManager().withPlugin("maven-publish", appliedPlugin -> project.getPluginManager().apply(MavenPluginPublishPlugin.class));
        project.getPluginManager().withPlugin("ivy-publish", appliedPlugin -> project.getPluginManager().apply(IvyPluginPublishingPlugin.class));
    }

    private void configureDescriptorGeneration(Project project, GradlePluginDevelopmentExtension extension) {
        TaskProvider<GeneratePluginDescriptors> generatePluginDescriptors = project.getTasks().register(GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME, GeneratePluginDescriptors.class, task -> {
            task.setGroup(PLUGIN_DEVELOPMENT_GROUP);
            task.setDescription(GENERATE_PLUGIN_DESCRIPTORS_TASK_DESCRIPTION);
            task.getDeclarations().set(extension.getPlugins());
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(task.getName()));
        });
        project.getTasks().named(PROCESS_RESOURCES_TASK, Copy.class, task -> {
            CopySpec copyPluginDescriptors = task.getRootSpec().addChild();
            copyPluginDescriptors.into("META-INF/gradle-plugins");
            copyPluginDescriptors.from(generatePluginDescriptors);
        });
    }

    private void validatePluginDeclarations(Project project, GradlePluginDevelopmentExtension extension) {
        project.afterEvaluate(evaluatedProject -> {
            for (PluginDeclaration declaration : extension.getPlugins()) {
                if (declaration.getId() == null) {
                    throw new IllegalArgumentException(String.format(DECLARATION_MISSING_ID_MESSAGE, declaration.getName()));
                }
                if (declaration.getImplementationClass() == null) {
                    throw new IllegalArgumentException(String.format(DECLARATION_MISSING_IMPLEMENTATION_MESSAGE, declaration.getName()));
                }
            }
        });
    }

    private void configurePluginValidations(Project project, GradlePluginDevelopmentExtension extension) {
        TaskProvider<ValidatePlugins> validatorTask = project.getTasks().register(VALIDATE_PLUGINS_TASK_NAME, ValidatePlugins.class, task -> {
            task.setGroup(PLUGIN_DEVELOPMENT_GROUP);
            task.setDescription(VALIDATE_PLUGIN_TASK_DESCRIPTION);

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("reports/plugin-development/validation-report.json"));

            task.getClasses().setFrom((Callable<Object>) () -> extension.getPluginSourceSet().getOutput().getClassesDirs());
            task.getClasspath().setFrom((Callable<Object>) () -> extension.getPluginSourceSet().getCompileClasspath());

            task.getLauncher().convention(toolchainLauncher(project));
        });
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(validatorTask));
    }

    private void configureDependencyGradlePluginsResolution(Project project) {
        new GradlePluginApiVersionAttributeConfigurationAction().execute((ProjectInternal) project);
    }

    private Provider<JavaLauncher> toolchainLauncher(Project project) {
        JavaPluginExtension extension = project.getExtensions().findByType(JavaPluginExtension.class);
        JavaToolchainService service = project.getExtensions().findByType(JavaToolchainService.class);
        return service.launcherFor(extension.getToolchain());
    }

    /**
     * A state shared by the validation process.
     * <p>
     * This separate class is required to ensure the shared state remains shared after deserialization of actions from the configuration cache.
     *
     * @see #configureJarTask(Project, GradlePluginDevelopmentExtension)
     */
    static class PluginValidationActionsState {
        private final List<PluginDescriptor> descriptors;
        private final Set<String> classes;

        public PluginValidationActionsState() {
            this(new ArrayList<>(), new HashSet<>());
        }

        public PluginValidationActionsState(List<PluginDescriptor> descriptors, Set<String> classes) {
            this.descriptors = descriptors;
            this.classes = classes;
        }

        public void addPluginClass(String className) {
            classes.add(className);
        }

        public void addPluginDescriptor(PluginDescriptor descriptor) {
            descriptors.add(descriptor);
        }

        public Set<String> getCollectedClasses() {
            return classes;
        }

        public List<PluginDescriptor> getCollectedDescriptors() {
            return descriptors;
        }
    }

    /**
     * Implements plugin validation tasks to validate that a proper plugin jar is produced.
     */
    static class PluginValidationAction implements Action<Task> {
        private final Provider<Collection<PluginDeclaration>> plugins;
        private final PluginValidationActionsState actionsState;

        PluginValidationAction(Provider<Collection<PluginDeclaration>> plugins, PluginValidationActionsState actionsState) {
            this.plugins = plugins;
            this.actionsState = actionsState;
        }

        @Override
        public void execute(Task task) {
            List<PluginDescriptor> descriptors = actionsState.getCollectedDescriptors();
            if (descriptors.isEmpty()) {
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
                for (PluginDeclaration declaration : plugins.get()) {
                    if (!pluginFileNames.contains(declaration.getId() + ".properties")) {
                        LOGGER.warn(String.format(DECLARED_PLUGIN_MISSING_MESSAGE, task.getPath(), declaration.getName(), declaration.getId()));
                    }
                }
            }
        }

        boolean hasFullyQualifiedClass(String fqClass) {
            return actionsState.getCollectedClasses().contains(fqClass.replaceAll("\\.", "/") + ".class");
        }
    }

    /**
     * A file copy action that collects plugin descriptors as they are added to the jar.
     */
    static class PluginDescriptorCollectorAction implements Action<FileCopyDetails> {
        private final PluginValidationActionsState actionsState;

        PluginDescriptorCollectorAction(PluginValidationActionsState actionsState) {
            this.actionsState = actionsState;
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
                actionsState.addPluginDescriptor(descriptor);
            }
        }
    }

    /**
     * A file copy action that collects class file paths as they are added to the jar.
     */
    static class ClassManifestCollectorAction implements Action<FileCopyDetails> {
        private final PluginValidationActionsState actionsState;

        ClassManifestCollectorAction(PluginValidationActionsState actionsState) {
            this.actionsState = actionsState;
        }

        @Override
        public void execute(FileCopyDetails fileCopyDetails) {
            actionsState.addPluginClass(fileCopyDetails.getRelativePath().toString());
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

            project.getTasks().withType(Test.class).configureEach(test -> {
                test.getInputs()
                    .files(pluginClasspathTask.get().getPluginClasspath())
                    .withPropertyName("pluginClasspath")
                    .withNormalizer(ClasspathNormalizer.class);

                test.getJvmArgumentProviders().add(new AddOpensCommandLineArgumentProvider(test));
            });

            for (SourceSet testSourceSet : testSourceSets) {
                String implementationConfigurationName = testSourceSet.getImplementationConfigurationName();
                dependencies.add(implementationConfigurationName, dependencies.gradleTestKit());
                String runtimeOnlyConfigurationName = testSourceSet.getRuntimeOnlyConfigurationName();
                dependencies.add(runtimeOnlyConfigurationName, project.getLayout().files(pluginClasspathTask));
            }
        }
    }

    /**
     * Provides an {@code --add-opens} flag for {@code java.base/java.lang} if the JVM version
     * a given test task is running does not allow reflection of JDK internals by default.
     * Needed when using ProjectBuilder in tests.
     */
    private static class AddOpensCommandLineArgumentProvider implements CommandLineArgumentProvider {
        private final Test test;

        private AddOpensCommandLineArgumentProvider(Test test) {
            this.test = test;
        }

        @Override
        public Iterable<String> asArguments() {
            return test.getJavaVersion().isCompatibleWith(JavaVersion.VERSION_1_9)
                ? Collections.singletonList("--add-opens=java.base/java.lang=ALL-UNNAMED")
                : Collections.emptyList();
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
