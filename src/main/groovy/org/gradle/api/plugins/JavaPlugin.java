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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishInstruction;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.specs.DependencySpecs;
import org.gradle.api.artifacts.specs.Type;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.Clean;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Upload;
import org.gradle.api.tasks.bundling.Bundle;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.ide.eclipse.*;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.*;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaPlugin implements Plugin {
    public static final String CLEAN_TASK_NAME = "clean";
    public static final String INIT_TASK_NAME = "init";
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";
    public static final String COMPILE_TASK_NAME = "compile";
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";
    public static final String COMPILE_TESTS_TASK_NAME = "compileTests";
    public static final String TEST_TASK_NAME = "test";
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
        pluginRegistry.apply(ReportingBasePlugin.class, project, customValues);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project, customValues);
        Convention convention = project.getConvention();
        convention.getPlugins().put("java", javaConvention);

        configureConfigurations(project);
        configureUploadRules(project);

        project.createTask(INIT_TASK_NAME).setDescription("The first task of the Java plugin tasks to be excuted. Does nothing if not customized.");

        ((ConventionTask) project.createTask(GUtil.map("type", Clean.class), CLEAN_TASK_NAME)).
                conventionMapping(GUtil.map(
                        "dir", new ConventionValue() {
                            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                                return project.getBuildDir();
                            }
                        })).setDescription("Deletes the build directory.");

        configureJavaDoc(project);

        configureProcessResources(project);

        configureCompile(project);

        configureTest(project);

        configureProcessTestResources(project);
        configureTestCompile(project);

        configureLibs(project, javaConvention);
        configureDists(project, javaConvention);

        configureEclipse(project);
        configureEclipseWtpModule(project);
    }

    private void configureTestCompile(Project project) {
        configureCompileTests((Compile) project.createTask(GUtil.map("type", Compile.class), COMPILE_TESTS_TASK_NAME),
                (Compile) project.task(COMPILE_TASK_NAME), DefaultConventionsToPropertiesMapping.TEST_COMPILE,
                project.getConfigurations()).setDescription("Compiles the Java test source code.");
    }

    private void configureCompile(final Project project) {
        project.addTaskLifecycleListener(Compile.class, new TaskLifecycleListener<Compile>() {
            public void taskAdded(Compile compile) {
                compile.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                compile.setConfiguration(project.getConfigurations().get(COMPILE_CONFIGURATION_NAME));
                compile.conventionMapping(DefaultConventionsToPropertiesMapping.COMPILE);
                addDependsOnProjectDependencies(compile, COMPILE_TASK_NAME);
            }
        });

        project.createTask(GUtil.map("type", Compile.class), COMPILE_TASK_NAME).setDescription("Compiles the Java source code.");
    }

    private void configureProcessResources(Project project) {
        project.addTaskLifecycleListener(Copy.class, new TaskLifecycleListener<Copy>() {
            public void taskAdded(Copy processResources) {
                processResources.dependsOn(INIT_TASK_NAME);
                processResources.conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);
            }
        });
        project.createTask(GUtil.map("type", Copy.class), PROCESS_RESOURCES_TASK_NAME).setDescription(
                "Process and copy the resources into the binary directory of the compiled sources.");
    }

    private void configureJavaDoc(final Project project) {
        project.addTaskLifecycleListener(Javadoc.class, new TaskLifecycleListener<Javadoc>() {
            public void taskAdded(Javadoc javadoc) {
                javadoc.conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC);
                javadoc.setConfiguration(project.getConfigurations().get(COMPILE_CONFIGURATION_NAME));
            }
        });
        project.createTask(GUtil.map("type", Javadoc.class), JAVADOC_TASK_NAME).setDescription("Generates the javadoc for the source code.");
    }

    private void configureEclipse(Project project) {
        project.createTask(ECLIPSE_TASK_NAME).dependsOn(
                configureEclipseProject(project),
                configureEclipseClasspath(project)
        ).setDescription("Generates an Eclipse .project and .classpath file.");
        project.createTask(WrapUtil.toMap("type", EclipseClean.class), ECLIPSE_CLEAN_TASK_NAME).setDescription(
                "Deletes the Eclipse .project and .classpath files.");
    }

    private EclipseProject configureEclipseProject(Project project) {
        EclipseProject eclipseProject = (EclipseProject) project.createTask(GUtil.map("type", EclipseProject.class), ECLIPSE_PROJECT_TASK_NAME);
        eclipseProject.setProjectName(project.getName());
        eclipseProject.setProjectType(ProjectType.JAVA);
        eclipseProject.setDescription("Generates an Eclipse .project file.");
        return eclipseProject;
    }

    private void configureEclipseWtpModule(Project project) {
        EclipseWtpModule eclipseWtpModule = (EclipseWtpModule) project.createTask(GUtil.map("type", EclipseWtpModule.class), ECLIPSE_WTP_MODULE_TASK_NAME);

        eclipseWtpModule.conventionMapping(GUtil.map(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return GUtil.addLists(java(convention).getSrcDirs(), java(convention).getResourceDirs());
                    }
                }));
        eclipseWtpModule.setDescription("Generates the Eclipse Wtp files.");
    }

    private EclipseClasspath configureEclipseClasspath(Project project) {
        EclipseClasspath eclipseClasspath = (EclipseClasspath) project.createTask(GUtil.map("type", EclipseClasspath.class), ECLIPSE_CP_TASK_NAME);
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
                        return new ArrayList(configurationContainer.get(TEST_RUNTIME_CONFIGURATION_NAME).copyRecursive(DependencySpecs.type(Type.EXTERNAL)).resolve());
                    }
                },
                "projectDependencies", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new ArrayList(((Task) conventionAwareObject).getProject().getConfigurations().get(TEST_RUNTIME_CONFIGURATION_NAME).getAllProjectDependencies());
                    }
                }));
        eclipseClasspath.setDescription("Generates an Eclipse .classpath file.");
        return eclipseClasspath;
    }

    private void configureProcessTestResources(Project project) {
        ConventionTask processTestResources = (ConventionTask) project.createTask(GUtil.map("type", Copy.class), PROCESS_TEST_RESOURCES_TASK_NAME);
        processTestResources.setDependsOn(WrapUtil.toSet(COMPILE_TASK_NAME));
        processTestResources.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST_TASK_NAME);
        processTestResources.conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
        processTestResources.setDescription(
                "Process and copy the test resources into the binary directory of the compiled test sources.");
    }

    private void configureUploadRules(final Project project) {
        project.addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>Internal: Upload the project artifacts of a configuration to the internal Gradle repository.";
            }

            public void apply(String taskName) {
                Set<Configuration> configurations = project.getConfigurations().getAll();
                for (Configuration configuration : configurations) {
                    if (taskName.equals(configuration.getUploadInternalTaskName())) {
                        Upload uploadInternal = createUploadTask(configuration.getUploadInternalTaskName(), configuration, project);
                        uploadInternal.getRepositories().add(project.getInternalRepository());
                    }
                }
            }
        });

        project.addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>: Upload the project artifacts of a configuration to a public Gradle repository.";
            }

            public void apply(String taskName) {
                Set<Configuration> configurations = project.getConfigurations().getAll();
                for (Configuration configuration : configurations) {
                    if (taskName.equals(configuration.getUploadTaskName())) {
                        createUploadTask(configuration.getUploadTaskName(), configuration, project);
                    }
                }
            }
        });
    }

    private Upload createUploadTask(String name, final Configuration configuration, Project project) {
        final Upload upload = (Upload) project.createTask(GUtil.map("type", Upload.class), name);
        PublishInstruction publishInstruction = new PublishInstruction();
        publishInstruction.setIvyFileParentDir(project.getBuildDir());
        upload.setConfiguration(configuration);
        upload.setPublishInstruction(publishInstruction);
        upload.dependsOn(configuration.getBuildArtifacts());
        upload.setDescription(String.format("Uploads all artifacts belonging to the %s configuration", configuration.getName()));
        return upload;
    }

    private void configureLibs(Project project, final JavaPluginConvention javaConvention) {
        Bundle libsBundle = (Bundle) project.createTask(GUtil.map("type", Bundle.class, "dependsOn", TEST_TASK_NAME), LIBS_TASK_NAME);
        libsBundle.setDefaultConfigurations(WrapUtil.toList(Dependency.MASTER_CONFIGURATION));
        libsBundle.setDefaultDestinationDir(project.getBuildDir());
        libsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.LIB);
        Jar jar = libsBundle.jar();
        jar.conventionMapping(WrapUtil.<String, ConventionValue>toMap("resourceCollections",
                new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return WrapUtil.toList(new FileSet(javaConvention.getClassesDir()));
                    }
                }));
        jar.setDescription("Generates a jar archive with all the compiled classes.");
        project.getConfigurations().get(Dependency.MASTER_CONFIGURATION).addArtifact(new ArchivePublishArtifact(jar));
    }

    private void configureDists(Project project, JavaPluginConvention javaPluginConvention) {
        Bundle distsBundle = (Bundle) project.createTask(GUtil.map("type", Bundle.class, "dependsOn", LIBS_TASK_NAME), DISTS_TASK_NAME);
        distsBundle.setDefaultConfigurations(WrapUtil.toList(DISTS_TASK_NAME));
        distsBundle.setDefaultDestinationDir(javaPluginConvention.getDistsDir());
        distsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.DIST);
    }

    private void configureTest(final Project project) {
        project.addTaskLifecycleListener(Test.class, new TaskLifecycleListener<Test>() {
            public void taskAdded(Test test) {
                test.dependsOn(COMPILE_TESTS_TASK_NAME);
                test.conventionMapping(DefaultConventionsToPropertiesMapping.TEST);
                test.setConfiguration(project.getConfigurations().get(TEST_RUNTIME_CONFIGURATION_NAME));
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
        project.createTask(GUtil.map("type", Test.class), TEST_TASK_NAME).setDescription("Runs the tests.");
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
        compileTests.setConfiguration(configurations.get(TEST_COMPILE_CONFIGURATION_NAME));
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
        final Configuration configuration = project.getConfigurations().get(configurationName);
        task.dependsOn(configuration.getBuildDependencies());
    }

    protected JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }
}
