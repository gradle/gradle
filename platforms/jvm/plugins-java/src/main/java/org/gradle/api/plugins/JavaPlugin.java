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

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.component.SoftwareComponentContainerInternal;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent;
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;

import javax.inject.Inject;
import java.util.Collections;


/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * This plugin creates a built-in {@link JvmTestSuite test suite} named {@code test} that represents the {@link Test} task for Java projects.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/java_plugin.html">Java plugin reference</a>
 * @see <a href="https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html">JVM test suite plugin reference</a>
 */
public abstract class JavaPlugin implements Plugin<Project> {
    /**
     * The name of the task that processes resources.
     */
    public static final String PROCESS_RESOURCES_TASK_NAME = JvmConstants.PROCESS_RESOURCES_TASK_NAME;

    /**
     * The name of the lifecycle task which outcome is that all the classes of a component are generated.
     */
    public static final String CLASSES_TASK_NAME = JvmConstants.CLASSES_TASK_NAME;

    /**
     * The name of the task which compiles Java sources.
     */
    public static final String COMPILE_JAVA_TASK_NAME = JvmConstants.COMPILE_JAVA_TASK_NAME;

    /**
     * The name of the task which processes the test resources.
     */
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = JvmConstants.PROCESS_TEST_RESOURCES_TASK_NAME;

    /**
     * The name of the lifecycle task which outcome is that all test classes of a component are generated.
     */
    public static final String TEST_CLASSES_TASK_NAME = JvmConstants.TEST_CLASSES_TASK_NAME;

    /**
     * The name of the task which compiles the test Java sources.
     */
    public static final String COMPILE_TEST_JAVA_TASK_NAME = JvmConstants.COMPILE_TEST_JAVA_TASK_NAME;

    /**
     * The name of the task which triggers execution of tests.
     */
    public static final String TEST_TASK_NAME = JvmConstants.TEST_TASK_NAME;

    /**
     * The name of the task which generates the component main jar.
     */
    public static final String JAR_TASK_NAME = JvmConstants.JAR_TASK_NAME;

    /**
     * The name of the task which generates the component javadoc.
     */
    public static final String JAVADOC_TASK_NAME = JvmConstants.JAVADOC_TASK_NAME;

    /**
     * The name of the API configuration, where dependencies exported by a component at compile time should
     * be declared.
     *
     * @since 3.4
     */
    public static final String API_CONFIGURATION_NAME = JvmConstants.API_CONFIGURATION_NAME;

    /**
     * The name of the implementation configuration, where dependencies that are only used internally by
     * a component should be declared.
     *
     * @since 3.4
     */
    public static final String IMPLEMENTATION_CONFIGURATION_NAME = JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME;

    /**
     * The name of the configuration to define the API elements of a component.
     * That is, the dependencies which are required to compile against that component.
     *
     * @since 3.4
     */
    public static final String API_ELEMENTS_CONFIGURATION_NAME = JvmConstants.API_ELEMENTS_CONFIGURATION_NAME;

    /**
     * The name of the configuration that is used to declare dependencies which are only required to compile a component,
     * but not at runtime.
     */
    public static final String COMPILE_ONLY_CONFIGURATION_NAME = JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME;

    /**
     * The name of the configuration to define the API elements of a component that are required to compile a component,
     * but not at runtime.
     *
     * @since 6.7
     */
    public static final String COMPILE_ONLY_API_CONFIGURATION_NAME = JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME;

    /**
     * The name of the runtime only dependencies configuration, used to declare dependencies
     * that should only be found at runtime.
     *
     * @since 3.4
     */
    public static final String RUNTIME_ONLY_CONFIGURATION_NAME = JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME;

    /**
     * The name of the runtime classpath configuration, used by a component to query its own runtime classpath.
     *
     * @since 3.4
     */
    public static final String RUNTIME_CLASSPATH_CONFIGURATION_NAME = JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME;

    /**
     * The name of the runtime elements configuration, that should be used by consumers
     * to query the runtime dependencies of a component.
     *
     * @since 3.4
     */
    public static final String RUNTIME_ELEMENTS_CONFIGURATION_NAME = JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME;

    /**
     * The name of the javadoc elements configuration.
     *
     * @since 6.0
     */
    public static final String JAVADOC_ELEMENTS_CONFIGURATION_NAME = JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME;

    /**
     * The name of the sources elements configuration.
     *
     * @since 6.0
     */
    public static final String SOURCES_ELEMENTS_CONFIGURATION_NAME = JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME;

    /**
     * The name of the compile classpath configuration.
     *
     * @since 3.4
     */
    public static final String COMPILE_CLASSPATH_CONFIGURATION_NAME = JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME;

    /**
     * The name of the annotation processor configuration.
     *
     * @since 4.6
     */
    public static final String ANNOTATION_PROCESSOR_CONFIGURATION_NAME = JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME;

    /**
     * The name of the test implementation dependencies configuration.
     *
     * @since 3.4
     */
    public static final String TEST_IMPLEMENTATION_CONFIGURATION_NAME = JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME;

    /**
     * The name of the configuration that should be used to declare dependencies which are only required
     * to compile the tests, but not when running them.
     */
    public static final String TEST_COMPILE_ONLY_CONFIGURATION_NAME = JvmConstants.TEST_COMPILE_ONLY_CONFIGURATION_NAME;

    /**
     * The name of the test runtime only dependencies configuration.
     *
     * @since 3.4
     */
    public static final String TEST_RUNTIME_ONLY_CONFIGURATION_NAME = JvmConstants.TEST_RUNTIME_ONLY_CONFIGURATION_NAME;

    /**
     * The name of the test compile classpath configuration.
     *
     * @since 3.4
     */
    public static final String TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME = JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME;

    /**
     * The name of the test annotation processor configuration.
     *
     * @since 4.6
     */
    public static final String TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME = JvmConstants.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME;

    /**
     * The name of the test runtime classpath configuration.
     *
     * @since 3.4
     */
    public static final String TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME = JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME;

    private final ObjectFactory objectFactory;

    @Inject
    public JavaPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final Project project) {
        if (project.getPluginManager().hasPlugin("java-platform")) {
            throw new IllegalStateException("The \"java\" or \"java-library\" plugin cannot be applied together with the \"java-platform\" plugin. " +
                "A project is either a platform or a library but cannot be both at the same time.");
        }
        final ProjectInternal projectInternal = (ProjectInternal) project;

        project.getPluginManager().apply(JavaBasePlugin.class);
        project.getPluginManager().apply("org.gradle.jvm-test-suite"); // TODO: change to reference plugin class by name after project dependency cycles untangled; this will affect ApplyPluginBuildOperationIntegrationTest (will have to remove id)

        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        DefaultJvmSoftwareComponent javaComponent = createJavaComponent(project, javaExtension);
        configurePublishing(project.getPlugins(), project.getExtensions(), javaComponent.getMainFeature().getSourceSet());

        // Set the 'java' component as the project's default.
        Configuration defaultConfiguration = project.getConfigurations().getByName(Dependency.DEFAULT_CONFIGURATION);
        defaultConfiguration.extendsFrom(javaComponent.getMainFeature().getRuntimeElementsConfiguration());
        ((SoftwareComponentContainerInternal) project.getComponents()).getMainComponent().convention(javaComponent);

        BuildOutputCleanupRegistry buildOutputCleanupRegistry = projectInternal.getServices().get(BuildOutputCleanupRegistry.class);
        configureSourceSets(javaExtension, buildOutputCleanupRegistry);

        configureTestTaskOrdering(project.getTasks());
        configureDiagnostics(project, javaComponent);
        configureBuild(project);
    }

    private static DefaultJvmSoftwareComponent createJavaComponent(Project project, JavaPluginExtension javaExtension) {
        // Create the 'java' component - create sourceset first
        SourceSet sourceSet = createSourceSet(SourceSet.MAIN_SOURCE_SET_NAME, javaExtension.getSourceSets());

        // Supply the sourceSet to the feature
        JvmFeatureInternal feature = new DefaultJvmFeature(
            JvmConstants.MAIN_FEATURE_NAME, sourceSet, Collections.emptyList(),
            (ProjectInternal) project, false, false);
        // Create a source directories variant for the feature
        feature.withSourceElements();

        // Build the main jar when running `assemble`.
        DefaultArtifactPublicationSet publicationSet = project.getExtensions().getByType(DefaultArtifactPublicationSet.class);
        publicationSet.addCandidate(feature.getRuntimeElementsConfiguration().getArtifacts().iterator().next());

        // And supply main feature to the component
        DefaultJvmSoftwareComponent component = project.getObjects().newInstance(
            DefaultJvmSoftwareComponent.class,
            JvmConstants.MAIN_COMPONENT_NAME,
            project,
            feature
        );
        project.getComponents().add(component);

        return component;
    }

    private static SourceSet createSourceSet(String name, SourceSetContainer sourceSets) {
        if (sourceSets.findByName(name) != null) {
            throw new GradleException("Cannot create multiple source sets with name '" + name +"'.");
        }

        return sourceSets.create(name);
    }

    // TODO: This approach is not necessarily correct for non-main features. All publications will attempt to use the main feature's
    // compile and runtime classpaths for version mapping, even if a non-main feature is being published.
    private static void configurePublishing(PluginContainer plugins, ExtensionContainer extensions, SourceSet sourceSet) {
        plugins.withType(PublishingPlugin.class, plugin -> {
            PublishingExtension publishing = extensions.getByType(PublishingExtension.class);

            // Set up the default configurations used when mapping to resolved versions
            publishing.getPublications().withType(IvyPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((PublicationInternal<?>) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.getCompileClasspathConfigurationName());
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.getRuntimeClasspathConfigurationName());
            });
            publishing.getPublications().withType(MavenPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((PublicationInternal<?>) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.getCompileClasspathConfigurationName());
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.getRuntimeClasspathConfigurationName());
            });
        });
    }

    private static void configureSourceSets(JavaPluginExtension pluginExtension, final BuildOutputCleanupRegistry buildOutputCleanupRegistry) {
        // Register the project's source set output directories
        pluginExtension.getSourceSets().all(sourceSet -> buildOutputCleanupRegistry.registerOutputs(sourceSet.getOutput()));
    }

    /**
     * Unless there are other concerns, we'd prefer to run jar tasks prior to test tasks, as this might offer a small performance improvement
     * for common usage.  In practice, running test tasks tends to take longer than building a jar; especially as a project matures. If tasks
     * in downstream projects require the jar from this project, and the jar and test tasks in this project are available to be run in either order,
     * running jar first so that other projects can continue executing tasks in parallel while this project runs its tests could be an improvement.
     * However, while we want to prioritize cross-project dependencies to maximize parallelism if possible, we don't want to add an explicit
     * dependsOn() relationship between the jar task and the test task, so that any projects which need to run test tasks first will not need modification.
     */
    private static void configureTestTaskOrdering(TaskContainer tasks) {
        TaskCollection<Jar> jarTasks = tasks.withType(Jar.class);
        tasks.withType(Test.class).configureEach(test -> test.shouldRunAfter(jarTasks));
    }

    private static void configureDiagnostics(Project project, JvmSoftwareComponentInternal component) {
        project.getTasks().withType(DependencyInsightReportTask.class).configureEach(task -> {
            new DslObject(task).getConventionMapping().map("configuration", component.getMainFeature()::getCompileClasspathConfiguration);
        });
    }

    private static void configureBuild(Project project) {
        project.getTasks().named(JavaBasePlugin.BUILD_NEEDED_TASK_NAME, task -> addDependsOnTaskInOtherProjects(task, true,
            JavaBasePlugin.BUILD_NEEDED_TASK_NAME, JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        project.getTasks().named(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, task -> addDependsOnTaskInOtherProjects(task, false,
            JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME));
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
    private static void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn, String otherProjectTaskName, String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(useDependedOn, otherProjectTaskName));
    }
}
