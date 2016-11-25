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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.java.JavaLibrary;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.gradle.api.plugins.JavaBasePlugin.Usage.FOR_COMPILE;
import static org.gradle.api.plugins.JavaBasePlugin.Usage.FOR_RUNTIME;
import static org.gradle.api.plugins.JavaBasePlugin.Usage.USAGE_ATTRIBUTE;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 */
public class JavaPlugin implements Plugin<ProjectInternal> {
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";
    public static final String CLASSES_TASK_NAME = "classes";
    public static final String COMPILE_JAVA_TASK_NAME = "compileJava";
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";
    public static final String TEST_CLASSES_TASK_NAME = "testClasses";
    public static final String COMPILE_TEST_JAVA_TASK_NAME = "compileTestJava";
    public static final String TEST_TASK_NAME = "test";
    public static final String JAR_TASK_NAME = "jar";
    public static final String JAVADOC_TASK_NAME = "javadoc";

    public static final String API_CONFIGURATION_NAME = "api";
    public static final String IMPLEMENTATION_CONFIGURATION_NAME = "implementation";
    public static final String API_COMPILE_CONFIGURATION_NAME = "apiCompile";
    public static final String COMPILE_CONFIGURATION_NAME = "compile";
    public static final String COMPILE_ONLY_CONFIGURATION_NAME = "compileOnly";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String RUNTIME_ONLY_CONFIGURATION_NAME = "runtimeOnly";
    public static final String RUNTIME_CLASSPATH_CONFIGURATION_NAME = "runtimeClasspath";
    public static final String RUNTIME_ELEMENTS_CONFIGURATION_NAME = "runtimeElements";
    public static final String COMPILE_CLASSPATH_CONFIGURATION_NAME = "compileClasspath";
    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";
    public static final String TEST_IMPLEMENTATION_CONFIGURATION_NAME = "testImplementation";
    public static final String TEST_COMPILE_ONLY_CONFIGURATION_NAME = "testCompileOnly";
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";
    public static final String TEST_RUNTIME_ONLY_CONFIGURATION_NAME = "testRuntimeOnly";
    public static final String TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME = "testCompileClasspath";
    public static final String TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME = "testRuntimeClasspath";

    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        project.getServices().get(ComponentRegistry.class).setMainComponent(new BuildableJavaComponentImpl(javaConvention));

        configureSourceSets(javaConvention);
        configureConfigurations(project);

        configureJavaDoc(javaConvention);
        configureTest(project, javaConvention);
        configureArchivesAndComponent(project, javaConvention);
        configureBuild(project);
    }

    private void configureSourceSets(final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();

        SourceSet main = pluginConvention.getSourceSets().create(SourceSet.MAIN_SOURCE_SET_NAME);

        SourceSet test = pluginConvention.getSourceSets().create(SourceSet.TEST_SOURCE_SET_NAME);
        test.setCompileClasspath(project.files(main.getOutput(), project.getConfigurations().getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
        test.setRuntimeClasspath(project.files(test.getOutput(), main.getOutput(), project.getConfigurations().getByName(TEST_RUNTIME_CONFIGURATION_NAME)));
    }

    private void configureJavaDoc(final JavaPluginConvention pluginConvention) {
        Project project = pluginConvention.getProject();

        SourceSet mainSourceSet = pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Javadoc javadoc = project.getTasks().create(JAVADOC_TASK_NAME, Javadoc.class);
        javadoc.setDescription("Generates Javadoc API documentation for the main source code.");
        javadoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
        javadoc.setClasspath(mainSourceSet.getOutput().plus(mainSourceSet.getCompileClasspath()));
        javadoc.setSource(mainSourceSet.getAllJava());
        addDependsOnTaskInOtherProjects(javadoc, true, JAVADOC_TASK_NAME, COMPILE_CONFIGURATION_NAME);
    }

    private void configureArchivesAndComponent(final Project project, final JavaPluginConvention pluginConvention) {
        Jar jar = project.getTasks().create(JAR_TASK_NAME, Jar.class);
        jar.setDescription("Assembles a jar archive containing the main classes.");
        jar.setGroup(BasePlugin.BUILD_GROUP);
        jar.from(pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput());

        ArchivePublishArtifact jarArtifact = new ArchivePublishArtifact(jar);
        Configuration runtimeConfiguration = project.getConfigurations().getByName(RUNTIME_CONFIGURATION_NAME);

        runtimeConfiguration.getArtifacts().add(jarArtifact);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(jarArtifact);
        project.getComponents().add(new JavaLibrary(jarArtifact, runtimeConfiguration.getAllDependencies()));
    }

    private void configureBuild(Project project) {
        addDependsOnTaskInOtherProjects(project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
        addDependsOnTaskInOtherProjects(project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureTest(final Project project, final JavaPluginConvention pluginConvention) {
        project.getTasks().withType(Test.class, new Action<Test>() {
            public void execute(final Test test) {
                test.getConventionMapping().map("testClassesDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDir();
                    }
                });
                test.getConventionMapping().map("classpath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getRuntimeClasspath();
                    }
                });
            }
        });
        Test test = project.getTasks().create(TEST_TASK_NAME, Test.class);
        project.getTasks().getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(test);
        test.setDescription("Runs the unit tests.");
        test.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
    }

    void configureConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration implementationConfiguration = configurations.getByName(IMPLEMENTATION_CONFIGURATION_NAME);
        Configuration testImplementationConfiguration = configurations.getByName(TEST_IMPLEMENTATION_CONFIGURATION_NAME);
        // the following is not strictly required now, but it will once we remove the deprecated configurations. More work today, less later!
        testImplementationConfiguration.extendsFrom(implementationConfiguration);
        Configuration runtimeConfiguration = configurations.getByName(RUNTIME_CONFIGURATION_NAME);

        Configuration compileTestsConfiguration = configurations.getByName(TEST_COMPILE_CONFIGURATION_NAME);
        compileTestsConfiguration.extendsFrom(implementationConfiguration);

        Configuration runtimeElementsConfiguration = configurations.maybeCreate(RUNTIME_ELEMENTS_CONFIGURATION_NAME);
        runtimeElementsConfiguration.setVisible(false);
        runtimeElementsConfiguration.setCanBeConsumed(true);
        runtimeElementsConfiguration.setCanBeResolved(false);
        runtimeElementsConfiguration.setDescription("Elements of runtime for main.");
        runtimeElementsConfiguration.extendsFrom(implementationConfiguration);

        configurations.getByName(TEST_RUNTIME_CONFIGURATION_NAME).extendsFrom(runtimeConfiguration, compileTestsConfiguration, testImplementationConfiguration);

        configurations.getByName(Dependency.DEFAULT_CONFIGURATION).extendsFrom(runtimeConfiguration);
        configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME).attribute(USAGE_ATTRIBUTE, FOR_COMPILE);
        configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME).attribute(USAGE_ATTRIBUTE, FOR_RUNTIME);
        configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME).attribute(USAGE_ATTRIBUTE, FOR_RUNTIME);

        Configuration runtimeClasspathConfiguration = configurations.maybeCreate(RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        runtimeClasspathConfiguration.extendsFrom(runtimeElementsConfiguration);

        // the following is not strictly required now, but it will once we remove the deprecated configurations. More work today, less later!
        Configuration testRuntimeClasspathConfiguration = configurations.maybeCreate(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        testRuntimeClasspathConfiguration.extendsFrom(testImplementationConfiguration);
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects are determined from
     * project lib dependencies using the specified configuration name. These may be projects this project depends on or
     * projects that depend on this project based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise use projects that depend
     * on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn, String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(useDependedOn, otherProjectTaskName));
    }

    private static class BuildableJavaComponentImpl implements BuildableJavaComponent {
        private final JavaPluginConvention convention;

        public BuildableJavaComponentImpl(JavaPluginConvention convention) {
            this.convention = convention;
        }

        public Collection<String> getRebuildTasks() {
            return Arrays.asList(BasePlugin.CLEAN_TASK_NAME, JavaBasePlugin.BUILD_TASK_NAME);
        }

        public Collection<String> getBuildTasks() {
            return Arrays.asList(JavaBasePlugin.BUILD_TASK_NAME);
        }

        public FileCollection getRuntimeClasspath() {
            FileCollection runtimeClasspath = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();
            ProjectInternal project = convention.getProject();
            FileCollection gradleApi = project.getConfigurations().detachedConfiguration(project.getDependencies().gradleApi(), project.getDependencies().localGroovy());
            return runtimeClasspath.minus(gradleApi);
        }

        public Configuration getCompileDependencies() {
            return convention.getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME);
        }
    }
}
