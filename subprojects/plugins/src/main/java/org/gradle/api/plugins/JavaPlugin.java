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

package org.gradle.api.plugins;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;
import static org.gradle.api.plugins.JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME;
import static org.gradle.api.plugins.internal.JvmPluginsHelper.configureJavaDocTask;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * This plugin creates a built-in {@link JvmTestSuite test suite} named {@code test} that represents the {@link Test} task for Java projects.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/java_plugin.html">Java plugin reference</a>
 * @see <a href="https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html">JVM test suite plugin reference</a>
 */
public class JavaPlugin implements Plugin<Project> {
    /**
     * The name of the task that processes resources.
     */
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";

    /**
     * The name of the lifecycle task which outcome is that all the classes of a component are generated.
     */
    public static final String CLASSES_TASK_NAME = "classes";

    /**
     * The name of the task which compiles Java sources.
     */
    public static final String COMPILE_JAVA_TASK_NAME = "compileJava";

    /**
     * The name of the task which processes the test resources.
     */
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";

    /**
     * The name of the lifecycle task which outcome is that all test classes of a component are generated.
     */
    public static final String TEST_CLASSES_TASK_NAME = "testClasses";

    /**
     * The name of the task which compiles the test Java sources.
     */
    public static final String COMPILE_TEST_JAVA_TASK_NAME = "compileTestJava";

    /**
     * The name of the task which triggers execution of tests.
     */
    public static final String TEST_TASK_NAME = "test";

    /**
     * The name of the task which generates the component main jar.
     */
    public static final String JAR_TASK_NAME = "jar";

    /**
     * The name of the task which generates the component javadoc.
     */
    public static final String JAVADOC_TASK_NAME = "javadoc";

    /**
     * The name of the API configuration, where dependencies exported by a component at compile time should
     * be declared.
     *
     * @since 3.4
     */
    public static final String API_CONFIGURATION_NAME = "api";

    /**
     * The name of the implementation configuration, where dependencies that are only used internally by
     * a component should be declared.
     *
     * @since 3.4
     */
    public static final String IMPLEMENTATION_CONFIGURATION_NAME = "implementation";

    /**
     * The name of the configuration to define the API elements of a component.
     * That is, the dependencies which are required to compile against that component.
     *
     * @since 3.4
     */
    public static final String API_ELEMENTS_CONFIGURATION_NAME = "apiElements";

    /**
     * The name of the configuration that is used to declare dependencies which are only required to compile a component,
     * but not at runtime.
     */
    public static final String COMPILE_ONLY_CONFIGURATION_NAME = "compileOnly";

    /**
     * The name of the configuration to define the API elements of a component that are required to compile a component,
     * but not at runtime.
     *
     * @since 6.7
     */
    public static final String COMPILE_ONLY_API_CONFIGURATION_NAME = "compileOnlyApi";

    /**
     * The name of the runtime only dependencies configuration, used to declare dependencies
     * that should only be found at runtime.
     *
     * @since 3.4
     */
    public static final String RUNTIME_ONLY_CONFIGURATION_NAME = "runtimeOnly";

    /**
     * The name of the runtime classpath configuration, used by a component to query its own runtime classpath.
     *
     * @since 3.4
     */
    public static final String RUNTIME_CLASSPATH_CONFIGURATION_NAME = "runtimeClasspath";

    /**
     * The name of the runtime elements configuration, that should be used by consumers
     * to query the runtime dependencies of a component.
     *
     * @since 3.4
     */
    public static final String RUNTIME_ELEMENTS_CONFIGURATION_NAME = "runtimeElements";

    /**
     * The name of the javadoc elements configuration.
     *
     * @since 6.0
     */
    public static final String JAVADOC_ELEMENTS_CONFIGURATION_NAME = "javadocElements";

    /**
     * The name of the sources elements configuration.
     *
     * @since 6.0
     */
    public static final String SOURCES_ELEMENTS_CONFIGURATION_NAME = "sourcesElements";

    /**
     * The name of the compile classpath configuration.
     *
     * @since 3.4
     */
    public static final String COMPILE_CLASSPATH_CONFIGURATION_NAME = "compileClasspath";

    /**
     * The name of the annotation processor configuration.
     *
     * @since 4.6
     */
    public static final String ANNOTATION_PROCESSOR_CONFIGURATION_NAME = "annotationProcessor";

    /**
     * The name of the test implementation dependencies configuration.
     *
     * @since 3.4
     */
    public static final String TEST_IMPLEMENTATION_CONFIGURATION_NAME = "testImplementation";

    /**
     * The name of the configuration that should be used to declare dependencies which are only required
     * to compile the tests, but not when running them.
     */
    public static final String TEST_COMPILE_ONLY_CONFIGURATION_NAME = "testCompileOnly";

    /**
     * The name of the test runtime only dependencies configuration.
     *
     * @since 3.4
     */
    public static final String TEST_RUNTIME_ONLY_CONFIGURATION_NAME = "testRuntimeOnly";

    /**
     * The name of the test compile classpath configuration.
     *
     * @since 3.4
     */
    public static final String TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME = "testCompileClasspath";

    /**
     * The name of the test annotation processor configuration.
     *
     * @since 4.6
     */
    public static final String TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME = "testAnnotationProcessor";

    /**
     * The name of the test runtime classpath configuration.
     *
     * @since 3.4
     */
    public static final String TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME = "testRuntimeClasspath";

    private final ObjectFactory objectFactory;
    private final SoftwareComponentFactory softwareComponentFactory;
    private final JvmPluginServices jvmServices;

    @Inject
    public JavaPlugin(ObjectFactory objectFactory,
                      SoftwareComponentFactory softwareComponentFactory,
                      JvmPluginServices jvmServices) {
        this.objectFactory = objectFactory;
        this.softwareComponentFactory = softwareComponentFactory;
        this.jvmServices = jvmServices;
    }

    @Override
    public void apply(final Project project) {
        if (project.getPluginManager().hasPlugin("java-platform")) {
            throw new IllegalStateException("The \"java\" or \"java-library\" plugin cannot be applied together with the \"java-platform\" plugin. " +
                "A project is either a platform or a library but cannot be both at the same time.");
        }
        final ProjectInternal projectInternal = (ProjectInternal) project;

        project.getPluginManager().apply(JavaBasePlugin.class);
        project.getPluginManager().apply("org.gradle.jvm-test-suite");

        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        projectInternal.getServices().get(ComponentRegistry.class).setMainComponent(new BuildableJavaComponentImpl(project, javaExtension));
        BuildOutputCleanupRegistry buildOutputCleanupRegistry = projectInternal.getServices().get(BuildOutputCleanupRegistry.class);

        configureSourceSets(project, javaExtension, buildOutputCleanupRegistry);
        configureConfigurations(project, javaExtension);

        configureJavadocTask(project, javaExtension);
        configureArchivesAndComponent(project, javaExtension);
        configureBuild(project);
    }

    private void configureSourceSets(Project project, JavaPluginExtension pluginExtension, final BuildOutputCleanupRegistry buildOutputCleanupRegistry) {
        SourceSetContainer sourceSets = pluginExtension.getSourceSets();

        sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME);

        // The built-in test suite must be configured after the main source set is available due to some
        // special handling in the IntelliJ model builder
        configureBuiltInTest(project, project.getExtensions().getByType(TestingExtension.class), pluginExtension);

        // Register the project's source set output directories
        sourceSets.all(sourceSet ->
            buildOutputCleanupRegistry.registerOutputs(sourceSet.getOutput())
        );
    }

    private void configureBuiltInTest(Project project, TestingExtension testing, JavaPluginExtension java) {
        final NamedDomainObjectProvider<JvmTestSuite> testSuite = testing.getSuites().register(DEFAULT_TEST_SUITE_NAME, JvmTestSuite.class, suite -> {
            final FileCollection mainSourceSetOutput = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
            final FileCollection testSourceSetOutput = suite.getSources().getOutput();

            suite.getSources().setCompileClasspath(project.getObjects().fileCollection().from(mainSourceSetOutput, project.getConfigurations().getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
            suite.getSources().setRuntimeClasspath(project.getObjects().fileCollection().from(testSourceSetOutput, mainSourceSetOutput, project.getConfigurations().getByName(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)));
        });

        // Force the realization of this test suite, targets and task
        testSuite.get();

        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(testSuite));
    }

    private void configureArchivesAndComponent(Project project, final JavaPluginExtension pluginExtension) {
        PublishArtifact jarArtifact = new LazyPublishArtifact(registerJarTaskFor(project, pluginExtension));
        Configuration apiElementConfiguration = project.getConfigurations().getByName(API_ELEMENTS_CONFIGURATION_NAME);
        Configuration runtimeElementsConfiguration = project.getConfigurations().getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME);

        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(jarArtifact);

        Provider<ProcessResources> processResources = project.getTasks().named(PROCESS_RESOURCES_TASK_NAME, ProcessResources.class);
        addJar(apiElementConfiguration, jarArtifact);
        addRuntimeVariants(runtimeElementsConfiguration, jarArtifact, mainSourceSetOf(pluginExtension), processResources);

        registerSoftwareComponents(project);
    }

    private TaskProvider<Jar> registerJarTaskFor(Project project, JavaPluginExtension pluginExtension) {
        return project.getTasks().register(JAR_TASK_NAME, Jar.class, jar -> {
            jar.setDescription("Assembles a jar archive containing the main classes.");
            jar.setGroup(BasePlugin.BUILD_GROUP);
            jar.from(mainSourceSetOf(pluginExtension).getOutput());
        });
    }

    private static SourceSet mainSourceSetOf(JavaPluginExtension pluginExtension) {
        return sourceSetOf(pluginExtension, SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private static SourceSet sourceSetOf(JavaPluginExtension pluginExtension, String mainSourceSetName) {
        return pluginExtension.getSourceSets().getByName(mainSourceSetName);
    }

    private void configureJavadocTask(Project project, JavaPluginExtension javaPluginExtension) {
        SourceSet main = mainSourceSetOf(javaPluginExtension);
        configureJavaDocTask(null, main, project.getTasks(), javaPluginExtension);
    }

    private void registerSoftwareComponents(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        // the main "Java" component
        AdhocComponentWithVariants java = softwareComponentFactory.adhoc("java");
        java.addVariantsFromConfiguration(configurations.getByName(API_ELEMENTS_CONFIGURATION_NAME), new JavaConfigurationVariantMapping("compile", false));
        java.addVariantsFromConfiguration(configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME), new JavaConfigurationVariantMapping("runtime", false));
        project.getComponents().add(java);
    }

    private void addJar(Configuration configuration, PublishArtifact jarArtifact) {
        ConfigurationPublications publications = configuration.getOutgoing();

        // Configure an implicit variant
        publications.getArtifacts().add(jarArtifact);
        publications.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
    }

    private void addRuntimeVariants(Configuration configuration, PublishArtifact jarArtifact, final SourceSet sourceSet, final Provider<ProcessResources> processResources) {
        ConfigurationPublications publications = configuration.getOutgoing();

        // Configure an implicit variant
        publications.getArtifacts().add(jarArtifact);
        publications.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);

        // Define some additional variants
        jvmServices.configureClassesDirectoryVariant(sourceSet.getRuntimeElementsConfigurationName(), sourceSet);
        NamedDomainObjectContainer<ConfigurationVariant> runtimeVariants = publications.getVariants();
        ConfigurationVariant resourcesVariant = runtimeVariants.create("resources");
        resourcesVariant.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        resourcesVariant.getAttributes().attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.RESOURCES));
        resourcesVariant.artifact(new JvmPluginsHelper.IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY, processResources) {
            @Override
            public File getFile() {
                return processResources.get().getDestinationDir();
            }
        });
    }

    private void configureBuild(Project project) {
        project.getTasks().named(JavaBasePlugin.BUILD_NEEDED_TASK_NAME, task -> addDependsOnTaskInOtherProjects(task, true,
            JavaBasePlugin.BUILD_NEEDED_TASK_NAME, TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        project.getTasks().named(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, task -> addDependsOnTaskInOtherProjects(task, false,
            JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME));
    }


    private void configureConfigurations(Project project, JavaPluginExtension extension) {
        ConfigurationContainer configurations = project.getConfigurations();

        Configuration defaultConfiguration = configurations.getByName(Dependency.DEFAULT_CONFIGURATION);
        Configuration implementationConfiguration = configurations.getByName(IMPLEMENTATION_CONFIGURATION_NAME);
        Configuration runtimeOnlyConfiguration = configurations.getByName(RUNTIME_ONLY_CONFIGURATION_NAME);
        Configuration testImplementationConfiguration = configurations.getByName(TEST_IMPLEMENTATION_CONFIGURATION_NAME);
        Configuration testRuntimeOnlyConfiguration = configurations.getByName(TEST_RUNTIME_ONLY_CONFIGURATION_NAME);

        testImplementationConfiguration.extendsFrom(implementationConfiguration);
        testRuntimeOnlyConfiguration.extendsFrom(runtimeOnlyConfiguration);

        SourceSet main = extension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        final DeprecatableConfiguration apiElementsConfiguration = (DeprecatableConfiguration) jvmServices.createOutgoingElements(API_ELEMENTS_CONFIGURATION_NAME,
            builder -> builder.fromSourceSet(main)
                .providesApi()
                .withDescription("API elements for main."));

        final DeprecatableConfiguration runtimeElementsConfiguration = (DeprecatableConfiguration) jvmServices.createOutgoingElements(RUNTIME_ELEMENTS_CONFIGURATION_NAME,
            builder -> builder.fromSourceSet(main)
                .providesRuntime()
                .withDescription("Elements of runtime for main.")
                .extendsFrom(implementationConfiguration, runtimeOnlyConfiguration));
        defaultConfiguration.extendsFrom(runtimeElementsConfiguration);

        apiElementsConfiguration.deprecateForDeclaration(IMPLEMENTATION_CONFIGURATION_NAME, COMPILE_ONLY_CONFIGURATION_NAME);
        runtimeElementsConfiguration.deprecateForDeclaration(IMPLEMENTATION_CONFIGURATION_NAME, COMPILE_ONLY_CONFIGURATION_NAME, RUNTIME_ONLY_CONFIGURATION_NAME);
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects are determined from
     * project lib dependencies using the specified configuration name. These may be projects this project depends on or
     * projects that depend on this project based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn, String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(useDependedOn, otherProjectTaskName));
    }

    /**
     * This is only used by buildSrc to add to the buildscript classpath.
     */
    private static class BuildableJavaComponentImpl implements BuildableJavaComponent {
        private final Project project;
        private final JavaPluginExtension extension;

        public BuildableJavaComponentImpl(Project project, JavaPluginExtension extension) {
            this.project = project;
            this.extension = extension;
        }

        @Override
        public Collection<String> getBuildTasks() {
            return Collections.singleton(JavaBasePlugin.BUILD_TASK_NAME);
        }

        @Override
        public FileCollection getRuntimeClasspath() {
            SourceSet mainSourceSet = mainSourceSetOf(extension);
            Configuration runtimeClasspath = project.getConfigurations().getByName(mainSourceSet.getRuntimeClasspathConfigurationName());
            ArtifactView view = runtimeClasspath.getIncoming().artifactView(config -> {
                config.componentFilter(componentId -> {
                    if (componentId instanceof OpaqueComponentIdentifier) {
                        DependencyFactory.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
                        return classPathNotation != DependencyFactory.ClassPathNotation.GRADLE_API && classPathNotation != DependencyFactory.ClassPathNotation.LOCAL_GROOVY;
                    }
                    return true;
                });
            });
            Configuration runtimeElements = project.getConfigurations().getByName(mainSourceSet.getRuntimeElementsConfigurationName());
            return runtimeElements.getOutgoing().getArtifacts().getFiles().plus(view.getFiles());
        }

        @Override
        public Configuration getCompileDependencies() {
            return project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        }
    }

}
