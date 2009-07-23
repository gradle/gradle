/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaPlugin implements Plugin {
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";
    public static final String COMPILE_TASK_NAME = "compile";
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";
    public static final String COMPILE_TESTS_TASK_NAME = "compileTests";
    public static final String TEST_TASK_NAME = "test";
    public static final String JAR_TASK_NAME = "jar";
    public static final String LIBS_TASK_NAME = "libs";
    public static final String DISTS_TASK_NAME = "dists";
    public static final String JAVADOC_TASK_NAME = "javadoc";

    public static final String COMPILE_CONFIGURATION_NAME = "compile";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";
    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";
    public static final String DISTS_CONFIGURATION_NAME = "dists";

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(BasePlugin.class, project);
        projectPluginsHandler.usePlugin(ReportingBasePlugin.class, project);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project, Collections.emptyMap());
        Convention convention = project.getConvention();
        convention.getPlugins().put("java", javaConvention);

        configureConfigurations(project);

        configureJavaDoc(project);

        configureProcessResources(project);
        configureCompile(project);

        configureTest(project);

        configureProcessTestResources(project);
        configureTestCompile(project);

        configureArchives(project);
    }

    private void configureTestCompile(Project project) {
        configureCompileTests(project.getTasks().add(COMPILE_TESTS_TASK_NAME, Compile.class),
                (Compile) project.getTasks().getByName(COMPILE_TASK_NAME), DefaultConventionsToPropertiesMapping.TEST_COMPILE,
                project.getConfigurations()).setDescription("Compiles the Java test source code.");
    }

    private void configureCompile(final Project project) {
        project.getTasks().withType(Compile.class).allTasks(new Action<Compile>() {
            public void execute(Compile compile) {
                compile.setClasspath(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                compile.conventionMapping(DefaultConventionsToPropertiesMapping.COMPILE);
                addDependsOnProjectDependencies(compile, COMPILE_CONFIGURATION_NAME);
            }
        });

        project.getTasks().add(COMPILE_TASK_NAME, Compile.class).setDescription("Compiles the Java source code.");
    }

    private void configureProcessResources(Project project) {
        Copy processResources = project.getTasks().add(PROCESS_RESOURCES_TASK_NAME, Copy.class);
        processResources.conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);
        processResources.setDescription(
                "Process and copy the resources into the binary directory of the compiled sources.");
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().withType(Javadoc.class).allTasks(new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC);
                javadoc.setConfiguration(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                // todo - not sure we want this
                addDependsOnProjectDependencies(javadoc, COMPILE_CONFIGURATION_NAME);
            }
        });
        project.getTasks().add(JAVADOC_TASK_NAME, Javadoc.class).setDescription("Generates the javadoc for the source code.");
    }

    private void configureProcessTestResources(Project project) {
        ConventionTask processTestResources = project.getTasks().add(PROCESS_TEST_RESOURCES_TASK_NAME, Copy.class);
        processTestResources.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST_TASK_NAME);
        processTestResources.conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
        processTestResources.setDescription(
                "Process and copy the test resources into the binary directory of the compiled test sources.");
    }

    private void configureArchives(final Project project) {
        project.getTasks().withType(AbstractArchiveTask.class).allTasks(new Action<AbstractArchiveTask>() {
            public void execute(AbstractArchiveTask task) {
                if (task instanceof Jar) {
                    task.conventionMapping(DefaultConventionsToPropertiesMapping.JAR);
                    task.dependsOn(TEST_TASK_NAME);
                    task.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                    task.dependsOn(COMPILE_TASK_NAME);
                }
                else if (task instanceof Tar) {
                    task.conventionMapping(DefaultConventionsToPropertiesMapping.TAR);
                    task.dependsOn(LIBS_TASK_NAME);
                }
                else if (task instanceof Zip) {
                    task.conventionMapping(DefaultConventionsToPropertiesMapping.ZIP);
                    task.dependsOn(LIBS_TASK_NAME);
                }
            }
        });

        final Spec<Task> isLib = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element instanceof Jar;
            }
        };
        Task libsTask = project.getTasks().add(LIBS_TASK_NAME);
        libsTask.setDescription("Builds all Jar and War archives");
        libsTask.dependsOn(new TaskDependency(){
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().findAll(isLib);
            }
        });

        final Spec<Task> isDist = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element instanceof Zip && !isLib.isSatisfiedBy(element);
            }
        };
        Task distsTask = project.getTasks().add(DISTS_TASK_NAME);
        distsTask.setDescription("Builds all Jar, War, Zip, and Tar archives");
        distsTask.dependsOn(LIBS_TASK_NAME);
        distsTask.dependsOn(new TaskDependency(){
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().findAll(isDist);
            }
        });

        Jar jar = project.getTasks().add(JAR_TASK_NAME, Jar.class);
        jar.setDescription("Generates a jar archive with all the compiled classes.");
        jar.conventionMapping("resourceCollections", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return WrapUtil.toList((Object) new FileSet(convention.getPlugin(JavaPluginConvention.class).getClassesDir()));
            }
        });
        project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).addArtifact(new ArchivePublishArtifact(jar));
    }

    private void configureTest(final Project project) {
        project.getTasks().withType(Test.class).allTasks(new Action<Test>() {
            public void execute(Test test) {
                test.dependsOn(COMPILE_TESTS_TASK_NAME);
                test.dependsOn(PROCESS_TEST_RESOURCES_TASK_NAME);
                test.dependsOn(COMPILE_TASK_NAME);
                test.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                test.conventionMapping(DefaultConventionsToPropertiesMapping.TEST);
                test.setConfiguration(project.getConfigurations().getByName(TEST_RUNTIME_CONFIGURATION_NAME));
                addDependsOnProjectDependencies(test, TEST_RUNTIME_CONFIGURATION_NAME);
            }
        });
        project.getTasks().add(TEST_TASK_NAME, Test.class).setDescription("Runs the tests.");
    }

    void configureConfigurations(final Project project) {
        project.setProperty("status", "integration");
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration compileConfiguration = configurations.add(COMPILE_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("Classpath for compiling the sources.");
        Configuration runtimeConfiguration = configurations.add(RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setDescription("Classpath for running the compiled sources.");

        Configuration compileTestsConfiguration = configurations.add(TEST_COMPILE_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setTransitive(false).setDescription("Classpath for compiling the test sources.");
        project.getDependencies().add(TEST_COMPILE_CONFIGURATION_NAME, new AbstractFileCollection() {
            public String getDisplayName() {
                return "classes dir";
            }
            public Set<File> getFiles() {
                File classesDir = project.getConvention().getPlugin(JavaPluginConvention.class).getClassesDir();
                return Collections.singleton(classesDir);
            }
        });
        
        configurations.add(TEST_RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(runtimeConfiguration, compileTestsConfiguration).
                setDescription("Classpath for running the test sources.");
        
        Configuration archivesConfiguration = configurations.add(Dependency.ARCHIVES_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");
        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(runtimeConfiguration, archivesConfiguration).
                setDescription("Configuration the default artifacts and its dependencies.");
        configurations.add(DISTS_CONFIGURATION_NAME);
    }

    protected Compile configureCompileTests(Compile compileTests, final Compile compile, Map propertyMapping, ConfigurationContainer configurations) {
        compileTests.setDependsOn(WrapUtil.toSet(COMPILE_TASK_NAME));
        compileTests.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST_TASK_NAME);
        configureCompileInternal(compileTests, propertyMapping);
        compileTests.setClasspath(configurations.getByName(TEST_COMPILE_CONFIGURATION_NAME));
        addDependsOnProjectDependencies(compileTests, TEST_COMPILE_CONFIGURATION_NAME);
        return compileTests;
    }

    protected Compile configureCompileInternal(Compile compile, Map propertyMapping) {
        compile.conventionMapping(propertyMapping);
        return compile;
    }

    private void addDependsOnProjectDependencies(final Task task, String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getBuildDependencies());
    }

    protected JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }
}
