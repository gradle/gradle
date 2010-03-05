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
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.plugins.EmbeddableJavaProject;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;


/**
 * <p>A  {@link Plugin}  which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaPlugin implements Plugin<Project> {
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";
    public static final String CLASSES_TASK_NAME = "classes";
    public static final String COMPILE_JAVA_TASK_NAME = "compileJava";
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";
    public static final String TEST_CLASSES_TASK_NAME = "testClasses";
    public static final String COMPILE_TEST_JAVA_TASK_NAME = "compileTestJava";
    public static final String TEST_TASK_NAME = "test";
    public static final String JAR_TASK_NAME = "jar";
    public static final String JAVADOC_TASK_NAME = "javadoc";

    public static final String COMPILE_CONFIGURATION_NAME = "compile";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";
    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";

    public void use(Project project) {
        project.getPlugins().usePlugin(JavaBasePlugin.class);

        JavaPluginConvention javaConvention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        project.getConvention().getPlugins().put("embeddedJavaProject", new EmbeddableJavaProjectImpl(javaConvention));

        configureConfigurations(project);
        configureSourceSetDefaults(javaConvention);

        configureSourceSets(javaConvention);

        configureJavaDoc(project);
        configureTest(project);
        configureArchives(project, javaConvention);
        configureBuild(project);
    }

    private void configureSourceSets(final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();

        final SourceSet main = pluginConvention.getSourceSets().add(SourceSet.MAIN_SOURCE_SET_NAME);

        final SourceSet test = pluginConvention.getSourceSets().add(SourceSet.TEST_SOURCE_SET_NAME);
        ConventionMapping conventionMapping = ((IConventionAware) test).getConventionMapping();
        conventionMapping.map("compileClasspath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return project.files(main.getClasses(), project.getConfigurations().getByName(
                        TEST_COMPILE_CONFIGURATION_NAME));
            }
        });
        conventionMapping.map("runtimeClasspath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return project.files(test.getClasses(), main.getClasses(), project.getConfigurations().getByName(
                        TEST_RUNTIME_CONFIGURATION_NAME));
            }
        });
    }

    private void configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        pluginConvention.getSourceSets().allObjects(new Action<SourceSet>() {
            public void execute(final SourceSet sourceSet) {
                final Project project = pluginConvention.getProject();
                ConventionMapping conventionMapping = ((IConventionAware) sourceSet).getConventionMapping();

                conventionMapping.map("compileClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME);
                    }
                });
                conventionMapping.map("runtimeClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return sourceSet.getClasses().plus(project.getConfigurations().getByName(
                                RUNTIME_CONFIGURATION_NAME));
                    }
                });
            }
        });
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().withType(Javadoc.class).allTasks(new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map("classpath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        SourceSet mainSourceSet = convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                        return mainSourceSet.getClasses().plus(mainSourceSet.getCompileClasspath());
                    }
                });
                javadoc.getConventionMapping().map("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(
                                SourceSet.MAIN_SOURCE_SET_NAME).getAllJava();
                    }
                });
                addDependsOnTaskInOtherProjects(javadoc, true, JAVADOC_TASK_NAME, COMPILE_CONFIGURATION_NAME);
            }
        });
        project.getTasks().add(JAVADOC_TASK_NAME, Javadoc.class).setDescription(
                "Generates the javadoc for the source code.");
    }

    private void configureArchives(final Project project, final JavaPluginConvention pluginConvention) {
        project.getTasks().getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(TEST_TASK_NAME);

        Jar jar = project.getTasks().add(JAR_TASK_NAME, Jar.class);
        jar.setDescription("Generates a jar archive with all the compiled classes.");
        jar.from(pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getClasses());
        project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).addArtifact(new ArchivePublishArtifact(
                jar));
    }

    private void configureBuild(Project project) {
        addDependsOnTaskInOtherProjects(project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), 
                true, JavaBasePlugin.BUILD_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
        addDependsOnTaskInOtherProjects(project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME),
                false, JavaBasePlugin.BUILD_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureTest(final Project project) {
        project.getTasks().withType(AbstractTestTask.class).allTasks(new Action<AbstractTestTask>() {
            public void execute(AbstractTestTask test) {
                test.getConventionMapping().map(GUtil.map("testClassesDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(
                                SourceSet.TEST_SOURCE_SET_NAME).getClassesDir();
                    }
                }, "classpath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(
                                SourceSet.TEST_SOURCE_SET_NAME).getRuntimeClasspath();
                    }
                }, "testSrcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        Set<File> srcDirs = convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(
                                SourceSet.TEST_SOURCE_SET_NAME).getJava().getSrcDirs();
                        return new ArrayList<File>(srcDirs);
                    }
                }));
            }
        });
        project.getTasks().add(TEST_TASK_NAME, Test.class).setDescription("Runs the unit tests.");
    }

    void configureConfigurations(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration compileConfiguration = configurations.add(COMPILE_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("Classpath for compiling the sources.");
        Configuration runtimeConfiguration = configurations.add(RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setDescription("Classpath for running the compiled sources.");

        Configuration compileTestsConfiguration = configurations.add(TEST_COMPILE_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setTransitive(false).setDescription("Classpath for compiling the test sources.");

        configurations.add(TEST_RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(runtimeConfiguration,
                compileTestsConfiguration).
                setDescription("Classpath for running the test sources.");

        Configuration archivesConfiguration = configurations.add(Dependency.ARCHIVES_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");

        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(runtimeConfiguration, archivesConfiguration).
                setDescription("Configuration the default artifacts and its dependencies.");
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

    private static class EmbeddableJavaProjectImpl implements EmbeddableJavaProject {
        private final JavaPluginConvention convention;

        public EmbeddableJavaProjectImpl(JavaPluginConvention convention) {
            this.convention = convention;
        }

        public Collection<String> getRebuildTasks() {
            return Arrays.asList(BasePlugin.CLEAN_TASK_NAME, JavaBasePlugin.BUILD_TASK_NAME);
        }

        public FileCollection getRuntimeClasspath() {
            return convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();
        }
    }


}
