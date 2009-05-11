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

import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.specs.DependencySpecs;
import org.gradle.api.artifacts.specs.Type;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.ide.eclipse.*;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.*;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaPlugin implements Plugin {
    public static final String INIT_TASK_NAME = "init";
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";
    public static final String COMPILE_TASK_NAME = "compile";
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";
    public static final String COMPILE_TESTS_TASK_NAME = "compileTests";
    public static final String TEST_TASK_NAME = "test";
    public static final String JAR_TASK_NAME = "jar";
    public static final String LIBS_TASK_NAME = "libs";
    public static final String DISTS_TASK_NAME = "dists";
    public static final String JAVADOC_TASK_NAME = "javadoc";

    public static final String ECLIPSE_TASK_NAME = "eclipse";
    public static final String ECLIPSE_CLEAN_TASK_NAME = "eclipseClean";
    public static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject";
    public static final String ECLIPSE_CP_TASK_NAME = "eclipseCp";
    public static final String ECLIPSE_WTP_MODULE_TASK_NAME = "eclipseWtpModule";

    public static final String COMPILE_CONFIGURATION_NAME = "compile";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";
    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";

    public void apply(Project project, PluginRegistry pluginRegistry) {
        apply(project, pluginRegistry, new HashMap<String, Object>());
    }

    public void apply(final Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(BasePlugin.class, project, customValues);
        pluginRegistry.apply(ReportingBasePlugin.class, project, customValues);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project, customValues);
        Convention convention = project.getConvention();
        convention.getPlugins().put("java", javaConvention);

        configureConfigurations(project);

        configureInit(project);

        configureJavaDoc(project);

        configureProcessResources(project);
        configureCompile(project);

        configureTest(project);

        configureProcessTestResources(project);
        configureTestCompile(project);

        configureArchives(project);

        configureEclipse(project);
        configureEclipseWtpModule(project);
    }

    private void configureInit(Project project) {
        project.getTasks().add(INIT_TASK_NAME).setDescription("The first task of the Java plugin tasks to be excuted. Does nothing if not customized.");
    }

    private void configureTestCompile(Project project) {
        configureCompileTests(project.getTasks().add(COMPILE_TESTS_TASK_NAME, Compile.class),
                (Compile) project.task(COMPILE_TASK_NAME), DefaultConventionsToPropertiesMapping.TEST_COMPILE,
                project.getConfigurations()).setDescription("Compiles the Java test source code.");
    }

    private void configureCompile(final Project project) {
        project.getTasks().whenTaskAdded(Compile.class, new Action<Compile>() {
            public void execute(Compile compile) {
                compile.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                compile.setConfiguration(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                compile.conventionMapping(DefaultConventionsToPropertiesMapping.COMPILE);
                addDependsOnProjectDependencies(compile, COMPILE_CONFIGURATION_NAME);
            }
        });

        project.getTasks().add(COMPILE_TASK_NAME, Compile.class).setDescription("Compiles the Java source code.");
    }

    private void configureProcessResources(Project project) {
        Copy processResources = project.getTasks().add(PROCESS_RESOURCES_TASK_NAME, Copy.class);
        processResources.dependsOn(INIT_TASK_NAME);
        processResources.conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);
        processResources.setDescription(
                "Process and copy the resources into the binary directory of the compiled sources.");
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().whenTaskAdded(Javadoc.class, new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC);
                javadoc.setConfiguration(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                addDependsOnProjectDependencies(javadoc, COMPILE_CONFIGURATION_NAME);
            }
        });
        project.getTasks().add(JAVADOC_TASK_NAME, Javadoc.class).setDescription("Generates the javadoc for the source code.");
    }

    private void configureEclipse(Project project) {
        project.getTasks().add(ECLIPSE_TASK_NAME).dependsOn(
                configureEclipseProject(project),
                configureEclipseClasspath(project)
        ).setDescription("Generates an Eclipse .project and .classpath file.");

        project.getTasks().add(ECLIPSE_CLEAN_TASK_NAME, EclipseClean.class).setDescription("Deletes the Eclipse .project and .classpath files.");
    }

    private EclipseProject configureEclipseProject(Project project) {
        EclipseProject eclipseProject = project.getTasks().add(ECLIPSE_PROJECT_TASK_NAME, EclipseProject.class);
        eclipseProject.setProjectName(project.getName());
        eclipseProject.setProjectType(ProjectType.JAVA);
        eclipseProject.setDescription("Generates an Eclipse .project file.");
        return eclipseProject;
    }

    private void configureEclipseWtpModule(Project project) {
        EclipseWtpModule eclipseWtpModule = project.getTasks().add(ECLIPSE_WTP_MODULE_TASK_NAME, EclipseWtpModule.class);

        eclipseWtpModule.conventionMapping(GUtil.map(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return GUtil.addLists(java(convention).getSrcDirs(), java(convention).getResourceDirs());
                    }
                }));
        eclipseWtpModule.setDescription("Generates the Eclipse Wtp files.");
    }

    private EclipseClasspath configureEclipseClasspath(final Project project) {
        EclipseClasspath eclipseClasspath = project.getTasks().add(ECLIPSE_CP_TASK_NAME, EclipseClasspath.class);
        eclipseClasspath.conventionMapping(GUtil.map(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return GUtil.addLists(java(convention).getSrcDirs(), java(convention).getResourceDirs());
                    }
                },
                "testSrcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return GUtil.addLists(java(convention).getTestSrcDirs(), java(convention).getTestResourceDirs());
                    }
                },
                "outputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return java(convention).getClassesDir();
                    }
                },
                "testOutputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return java(convention).getTestClassesDir();
                    }
                },
                "classpathLibs", new ConventionValue() {
                    public Object getValue(Convention convention, final IConventionAware conventionAwareObject) {
                        ConfigurationContainer configurationContainer = ((Task) conventionAwareObject).getProject().getConfigurations();
                        return new ArrayList(configurationContainer.getByName(TEST_RUNTIME_CONFIGURATION_NAME).copyRecursive(DependencySpecs.type(Type.EXTERNAL)).resolve());
                    }
                },
                "projectDependencies", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new ArrayList(project.getConfigurations().getByName(TEST_RUNTIME_CONFIGURATION_NAME).getAllProjectDependencies());
                    }
                }));
        eclipseClasspath.setDescription("Generates an Eclipse .classpath file.");
        return eclipseClasspath;
    }

    private void configureProcessTestResources(Project project) {
        ConventionTask processTestResources = project.getTasks().add(PROCESS_TEST_RESOURCES_TASK_NAME, Copy.class);
        processTestResources.setDependsOn(WrapUtil.toSet(COMPILE_TASK_NAME));
        processTestResources.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST_TASK_NAME);
        processTestResources.conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
        processTestResources.setDescription(
                "Process and copy the test resources into the binary directory of the compiled test sources.");
    }

    private void configureArchives(final Project project) {
        project.getTasks().whenTaskAdded(AbstractArchiveTask.class, new Action<AbstractArchiveTask>() {
            public void execute(AbstractArchiveTask task) {
                if (task instanceof Jar) {
                    task.conventionMapping(DefaultConventionsToPropertiesMapping.JAR);
                    task.dependsOn(TEST_TASK_NAME);
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
        distsTask.dependsOn(LIBS_TASK_NAME);
        distsTask.dependsOn(new TaskDependency(){
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().findAll(isDist);
            }
        });

        Jar jar = project.getTasks().add(JAR_TASK_NAME, Jar.class);
        jar.setDescription("Generates a jar archive with all the compiled classes.");
        project.getConfigurations().getByName(Dependency.MASTER_CONFIGURATION).addArtifact(new ArchivePublishArtifact(jar));
    }

    private void configureTest(final Project project) {
        project.getTasks().whenTaskAdded(Test.class, new Action<Test>() {
            public void execute(Test test) {
                test.dependsOn(COMPILE_TESTS_TASK_NAME);
                test.conventionMapping(DefaultConventionsToPropertiesMapping.TEST);
                test.setConfiguration(project.getConfigurations().getByName(TEST_RUNTIME_CONFIGURATION_NAME));
                addDependsOnProjectDependencies(test, TEST_RUNTIME_CONFIGURATION_NAME);
                test.doFirst(new TaskAction() {
                    public void execute(Task task) {
                        Test test = (Test) task;
                        List unmanagedClasspathFromTestCompile = ((Compile) test.getProject().task(COMPILE_TESTS_TASK_NAME))
                                .getUnmanagedClasspath();
                        test.unmanagedClasspath(unmanagedClasspathFromTestCompile.toArray(
                                new Object[unmanagedClasspathFromTestCompile.size()]));
                    }
                });
            }
        });
        project.getTasks().add(TEST_TASK_NAME, Test.class).setDescription("Runs the tests.");
    }

    void configureConfigurations(Project project) {
        project.setProperty("status", "integration");
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration compileConfiguration = configurations.add(COMPILE_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("Classpath for compiling the sources.");
        Configuration runtimeConfiguration = configurations.add(RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setDescription("Classpath for running the compiled sources.");;
        Configuration compileTestsConfiguration = configurations.add(TEST_COMPILE_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setTransitive(false).setDescription("Classpath for compiling the test sources.");;
        Configuration runTestsConfiguration = configurations.add(TEST_RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(runtimeConfiguration, compileTestsConfiguration).
                setDescription("Classpath for running the test sources.");;
        Configuration masterConfiguration = configurations.add(Dependency.MASTER_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");;
        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(runtimeConfiguration, masterConfiguration).
                setDescription("Configuration the default artifacts and its dependencies.");
        configurations.add(DISTS_TASK_NAME);
    }

    protected Compile configureCompileTests(Compile compileTests, final Compile compile, Map propertyMapping, ConfigurationContainer configurations) {
        compileTests.setDependsOn(WrapUtil.toSet(PROCESS_TEST_RESOURCES_TASK_NAME));
        compileTests.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST_TASK_NAME);
        configureCompileInternal(compileTests, propertyMapping);
        compileTests.setConfiguration(configurations.getByName(TEST_COMPILE_CONFIGURATION_NAME));
        addDependsOnProjectDependencies(compileTests, TEST_COMPILE_CONFIGURATION_NAME);
        return (Compile) compileTests.doFirst(new TaskAction() {
            public void execute(Task task) {
                Compile compileTests = (Compile) task;
                if (compile.getUnmanagedClasspath() != null) {
                    compileTests.unmanagedClasspath((Object[]) compile.getUnmanagedClasspath().toArray(
                            new Object[compile.getUnmanagedClasspath().size()]));
                }
            }
        });
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
