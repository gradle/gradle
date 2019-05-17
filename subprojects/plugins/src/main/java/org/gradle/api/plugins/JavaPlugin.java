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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.java.JavaLibraryPlatform;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.JavaPluginsHelper;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.util.DeprecationLogger;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;
import static org.gradle.api.attributes.Bundling.EXTERNAL;
import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 */
public class JavaPlugin implements Plugin<ProjectInternal> {
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
     * The name of the configuration used by consumers to get the API elements of a component, that is to say
     * the dependencies which are required to compile against that component.
     *
     * @since 3.4
     */
    public static final String API_ELEMENTS_CONFIGURATION_NAME = "apiElements";

    /**
     * The name of the configuration that is used to declare API or implementation dependencies. This configuration
     * is deprecated.
     *
     * @deprecated Users should prefer {@link #API_CONFIGURATION_NAME} or {@link #IMPLEMENTATION_CONFIGURATION_NAME}.
     */
    public static final String COMPILE_CONFIGURATION_NAME = "compile";

    /**
     * The name of the configuration that is used to declare dependencies which are only required to compile a component,
     * but not at runtime.
     */
    public static final String COMPILE_ONLY_CONFIGURATION_NAME = "compileOnly";

    /**
     * The name of the "runtime" configuration. This configuration is deprecated and doesn't represent a correct view of
     * the runtime dependencies of a component.
     *
     * @deprecated Consumers should use {@link #RUNTIME_ELEMENTS_CONFIGURATION_NAME} instead.
     */
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";

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
    @Incubating
    public static final String ANNOTATION_PROCESSOR_CONFIGURATION_NAME = "annotationProcessor";

    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";

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
     * The name of the configuration that represents the component runtime classpath. This configuration doesn't
     * represent the exact runtime dependencies and therefore is deprecated.
     *
     * @deprecated Use {@link #TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME} instead.
     */
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";

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
    @Incubating
    public static final String TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME = "testAnnotationProcessor";

    /**
     * The name of the test runtime classpath configuration.
     *
     * @since 3.4
     */
    public static final String TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME = "testRuntimeClasspath";

    private final ObjectFactory objectFactory;
    private final SoftwareComponentFactory softwareComponentFactory;

    @Inject
    public JavaPlugin(ObjectFactory objectFactory, SoftwareComponentFactory softwareComponentFactory) {
        this.objectFactory = objectFactory;
        this.softwareComponentFactory = softwareComponentFactory;
    }

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        project.getServices().get(ComponentRegistry.class).setMainComponent(new BuildableJavaComponentImpl(javaConvention));
        BuildOutputCleanupRegistry buildOutputCleanupRegistry = project.getServices().get(BuildOutputCleanupRegistry.class);

        configureSourceSets(javaConvention, buildOutputCleanupRegistry);
        configureConfigurations(project, javaConvention);

        configureJavaDoc(javaConvention);
        configureTest(project, javaConvention);
        configureArchivesAndComponent(project, javaConvention);
        configureBuild(project);

        if (project.getPluginManager().hasPlugin("java-platform")) {
            DeprecationLogger.nagUserOfDeprecatedBehaviour("The \"java\" (or \"java-library\") plugin cannot be used together with the \"java-platform\" plugin on a given project. " +
                "A project is either a platform or a library but cannot be both at the same time.");
        }
    }

    private void configureSourceSets(JavaPluginConvention pluginConvention, final BuildOutputCleanupRegistry buildOutputCleanupRegistry) {
        Project project = pluginConvention.getProject();

        SourceSet main = pluginConvention.getSourceSets().create(SourceSet.MAIN_SOURCE_SET_NAME);

        SourceSet test = pluginConvention.getSourceSets().create(SourceSet.TEST_SOURCE_SET_NAME);
        test.setCompileClasspath(project.getObjects().fileCollection().from(main.getOutput(), project.getConfigurations().getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
        test.setRuntimeClasspath(project.getObjects().fileCollection().from(test.getOutput(), main.getOutput(), project.getConfigurations().getByName(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)));

        // Register the project's source set output directories
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(SourceSet sourceSet) {
                buildOutputCleanupRegistry.registerOutputs(sourceSet.getOutput());
            }
        });
    }

    private void configureJavaDoc(final JavaPluginConvention pluginConvention) {
        Project project = pluginConvention.getProject();
        project.getTasks().register(JAVADOC_TASK_NAME, Javadoc.class, new Action<Javadoc>() {
            @Override
            public void execute(Javadoc javadoc) {
                final SourceSet mainSourceSet = pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                javadoc.setDescription("Generates Javadoc API documentation for the main source code.");
                javadoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
                javadoc.setClasspath(mainSourceSet.getOutput().plus(mainSourceSet.getCompileClasspath()));
                javadoc.setSource(mainSourceSet.getAllJava());
            }
        });
    }

    private void configureArchivesAndComponent(Project project, final JavaPluginConvention pluginConvention) {
        TaskProvider<Jar> jar = project.getTasks().register(JAR_TASK_NAME, Jar.class, new Action<Jar>() {
            @Override
            public void execute(Jar jar) {
                jar.setDescription("Assembles a jar archive containing the main classes.");
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput());
            }
        });
        // TODO: Allow this to be added lazily
        PublishArtifact jarArtifact = new LazyPublishArtifact(jar);
        Configuration apiElementConfiguration = project.getConfigurations().getByName(API_ELEMENTS_CONFIGURATION_NAME);
        Configuration runtimeConfiguration = project.getConfigurations().getByName(RUNTIME_CONFIGURATION_NAME);
        Configuration runtimeElementsConfiguration = project.getConfigurations().getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME);

        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(jarArtifact);

        Provider<JavaCompile> javaCompile = project.getTasks().named(COMPILE_JAVA_TASK_NAME, JavaCompile.class);
        Provider<ProcessResources> processResources = project.getTasks().named(PROCESS_RESOURCES_TASK_NAME, ProcessResources.class);

        addJar(apiElementConfiguration, jarArtifact);
        addJar(runtimeConfiguration, jarArtifact);
        addRuntimeVariants(runtimeElementsConfiguration, jarArtifact, javaCompile, processResources);

        registerSoftwareComponents(project);
        project.getComponents().add(objectFactory.newInstance(JavaLibraryPlatform.class, project.getConfigurations()));
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
        publications.getAttributes().attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE);
    }

    private void addRuntimeVariants(Configuration configuration, PublishArtifact jarArtifact, final Provider<JavaCompile> javaCompile, final Provider<ProcessResources> processResources) {
        ConfigurationPublications publications = configuration.getOutgoing();

        // Configure an implicit variant
        publications.getArtifacts().add(jarArtifact);
        publications.getAttributes().attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE);

        // Define some additional variants
        NamedDomainObjectContainer<ConfigurationVariant> runtimeVariants = publications.getVariants();
        ConfigurationVariant classesVariant = runtimeVariants.create("classes");
        classesVariant.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_CLASSES));
        classesVariant.artifact(new JavaPluginsHelper.IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, javaCompile) {
            @Override
            public File getFile() {
                return javaCompile.get().getDestinationDir();
            }
        });
        ConfigurationVariant resourcesVariant = runtimeVariants.create("resources");
        resourcesVariant.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_RESOURCES));
        resourcesVariant.artifact(new JavaPluginsHelper.IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY, processResources) {
            @Override
            public File getFile() {
                return processResources.get().getDestinationDir();
            }
        });
    }

    private void configureBuild(Project project) {
        project.getTasks().named(JavaBasePlugin.BUILD_NEEDED_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task task) {
                addDependsOnTaskInOtherProjects(task, true,
                        JavaBasePlugin.BUILD_NEEDED_TASK_NAME, TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            }
        });
        project.getTasks().named(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task task) {
                addDependsOnTaskInOtherProjects(task, false,
                        JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
            }
        });
    }

    private void configureTest(final Project project, final JavaPluginConvention pluginConvention) {
        project.getTasks().withType(Test.class).configureEach(new Action<Test>() {
            @Override
            public void execute(final Test test) {
                test.getConventionMapping().map("testClassesDirs", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDirs();
                    }
                });
                test.getConventionMapping().map("classpath", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getRuntimeClasspath();
                    }
                });
            }
        });

        final Provider<Test> test = project.getTasks().register(TEST_TASK_NAME, Test.class, new Action<Test>() {
            @Override
            public void execute(Test test) {
                test.setDescription("Runs the unit tests.");
                test.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            }
        });
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(test);
            }
        });
    }

    private void configureConfigurations(Project project, final JavaPluginConvention convention) {
        ConfigurationContainer configurations = project.getConfigurations();

        Configuration defaultConfiguration = configurations.getByName(Dependency.DEFAULT_CONFIGURATION);
        Configuration compileConfiguration = configurations.getByName(COMPILE_CONFIGURATION_NAME);
        Configuration implementationConfiguration = configurations.getByName(IMPLEMENTATION_CONFIGURATION_NAME);
        Configuration runtimeConfiguration = configurations.getByName(RUNTIME_CONFIGURATION_NAME);
        Configuration runtimeOnlyConfiguration = configurations.getByName(RUNTIME_ONLY_CONFIGURATION_NAME);
        Configuration compileTestsConfiguration = configurations.getByName(TEST_COMPILE_CONFIGURATION_NAME);
        Configuration testImplementationConfiguration = configurations.getByName(TEST_IMPLEMENTATION_CONFIGURATION_NAME);
        Configuration testRuntimeConfiguration = configurations.getByName(TEST_RUNTIME_CONFIGURATION_NAME);
        Configuration testRuntimeOnlyConfiguration = configurations.getByName(TEST_RUNTIME_ONLY_CONFIGURATION_NAME);

        compileTestsConfiguration.extendsFrom(compileConfiguration);
        testImplementationConfiguration.extendsFrom(implementationConfiguration);
        testRuntimeConfiguration.extendsFrom(runtimeConfiguration);
        testRuntimeOnlyConfiguration.extendsFrom(runtimeOnlyConfiguration);

        final Configuration apiElementsConfiguration = configurations.maybeCreate(API_ELEMENTS_CONFIGURATION_NAME);
        apiElementsConfiguration.setVisible(false);
        apiElementsConfiguration.setDescription("API elements for main.");
        apiElementsConfiguration.setCanBeResolved(false);
        apiElementsConfiguration.setCanBeConsumed(true);
        apiElementsConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API_JARS));
        apiElementsConfiguration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, EXTERNAL));
        apiElementsConfiguration.getAttributes().attribute(CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        apiElementsConfiguration.extendsFrom(runtimeConfiguration);

        final Configuration runtimeElementsConfiguration = configurations.maybeCreate(RUNTIME_ELEMENTS_CONFIGURATION_NAME);
        runtimeElementsConfiguration.setVisible(false);
        runtimeElementsConfiguration.setCanBeConsumed(true);
        runtimeElementsConfiguration.setCanBeResolved(false);
        runtimeElementsConfiguration.setDescription("Elements of runtime for main.");
        runtimeElementsConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_JARS));
        runtimeElementsConfiguration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, EXTERNAL));
        runtimeElementsConfiguration.getAttributes().attribute(CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        runtimeElementsConfiguration.extendsFrom(implementationConfiguration, runtimeOnlyConfiguration, runtimeConfiguration);

        defaultConfiguration.extendsFrom(runtimeElementsConfiguration);


        configureTargetPlatform(apiElementsConfiguration, convention);
        configureTargetPlatform(runtimeElementsConfiguration, convention);
    }

    /**
     * Configures the target platform for an outgoing configuration.
     */
    private void configureTargetPlatform(Configuration outgoing, final JavaPluginConvention convention) {
        ((ConfigurationInternal)outgoing).beforeLocking(new Action<ConfigurationInternal>() {
            @Override
            public void execute(ConfigurationInternal configuration) {
                JavaEcosystemSupport.configureDefaultTargetPlatform(configuration, convention.getTargetCompatibility());
            }
        });
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
        private final JavaPluginConvention convention;

        public BuildableJavaComponentImpl(JavaPluginConvention convention) {
            this.convention = convention;
        }

        @Override
        public Collection<String> getBuildTasks() {
            return Collections.singleton(JavaBasePlugin.BUILD_TASK_NAME);
        }

        @Override
        public FileCollection getRuntimeClasspath() {
            ProjectInternal project = convention.getProject();
            SourceSet mainSourceSet = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            FileCollection runtimeClasspath = mainSourceSet.getRuntimeClasspath();
            FileCollection gradleApi = project.getConfigurations().detachedConfiguration(project.getDependencies().gradleApi(), project.getDependencies().localGroovy());
            Configuration runtimeElements = project.getConfigurations().getByName(mainSourceSet.getRuntimeElementsConfigurationName());
            FileCollection mainSourceSetArtifact = runtimeElements.getOutgoing().getArtifacts().getFiles();
            return mainSourceSetArtifact.plus(runtimeClasspath.minus(mainSourceSet.getOutput()).minus(gradleApi));
        }

        @Override
        public Configuration getCompileDependencies() {
            return convention.getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
        }
    }

}
