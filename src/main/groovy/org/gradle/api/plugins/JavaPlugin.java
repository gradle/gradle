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
import org.gradle.api.artifacts.PublishInstruction;
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
        configureBuildConfigurationRule(project);

        project.getTasks().add(INIT_TASK_NAME).setDescription("The first task of the Java plugin tasks to be excuted. Does nothing if not customized.");

        project.getTasks().add(CLEAN_TASK_NAME, Clean.class).
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
        configureCompileTests(project.getTasks().add(COMPILE_TESTS_TASK_NAME, Compile.class),
                (Compile) project.task(COMPILE_TASK_NAME), DefaultConventionsToPropertiesMapping.TEST_COMPILE,
                project.getConfigurations()).setDescription("Compiles the Java test source code.");
    }

    private void configureCompile(final Project project) {
        project.getTasks().whenTaskAdded(Compile.class, new Action<Compile>() {
            public void execute(Compile compile) {
                compile.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                compile.setConfiguration(project.getConfigurations().get(COMPILE_CONFIGURATION_NAME));
                compile.conventionMapping(DefaultConventionsToPropertiesMapping.COMPILE);
                addDependsOnProjectDependencies(compile, COMPILE_CONFIGURATION_NAME);
            }
        });

        project.getTasks().add(COMPILE_TASK_NAME, Compile.class).setDescription("Compiles the Java source code.");
    }

    private void configureProcessResources(Project project) {
        project.getTasks().whenTaskAdded(Copy.class, new Action<Copy>() {
            public void execute(Copy processResources) {
                processResources.dependsOn(INIT_TASK_NAME);
                processResources.conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);
            }
        });
        project.getTasks().add(PROCESS_RESOURCES_TASK_NAME, Copy.class).setDescription(
                "Process and copy the resources into the binary directory of the compiled sources.");
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().whenTaskAdded(Javadoc.class, new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC);
                javadoc.setConfiguration(project.getConfigurations().get(COMPILE_CONFIGURATION_NAME));
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

    private EclipseClasspath configureEclipseClasspath(Project project) {
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
        ConventionTask processTestResources = project.getTasks().add(PROCESS_TEST_RESOURCES_TASK_NAME, Copy.class);
        processTestResources.setDependsOn(WrapUtil.toSet(COMPILE_TASK_NAME));
        processTestResources.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST_TASK_NAME);
        processTestResources.conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
        processTestResources.setDescription(
                "Process and copy the test resources into the binary directory of the compiled test sources.");
    }

    private void configureBuildConfigurationRule(final Project project) {
        final String prefix = "build";
        project.getTasks().addRule(new Rule() {
            public String getDescription() {
                return String.format("Pattern: %s<ConfigurationName>: Builds the artifacts belonging to the configuration.", prefix);
            }

            public void apply(String taskName) {
                if (taskName.startsWith(prefix)) {
                    Configuration configuration = project.getConfigurations().find(taskName.substring(prefix.length()).toLowerCase());
                    if (configuration != null) {
                        project.getTasks().add(taskName).dependsOn(configuration.getBuildArtifacts());
                    }
                }
            }
        });
    }

    private void configureUploadRules(final Project project) {
        project.getTasks().addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>Internal: Upload the project artifacts of a configuration to the internal Gradle repository.";
            }

            public void apply(String taskName) {
                Set<Configuration> configurations = project.getConfigurations().getAll();
                for (Configuration configuration : configurations) {
                    if (taskName.equals(configuration.getUploadInternalTaskName())) {
                        Upload uploadInternal = createUploadTask(configuration.getUploadInternalTaskName(), configuration, project);
                        uploadInternal.getRepositories().add(project.getBuild().getInternalRepository());
                    }
                }
            }
        });

        project.getTasks().addRule(new Rule() {
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
        Upload upload = project.getTasks().add(name, Upload.class);
        PublishInstruction publishInstruction = new PublishInstruction();
        publishInstruction.setIvyFileParentDir(project.getBuildDir());
        upload.setConfiguration(configuration);
        upload.setPublishInstruction(publishInstruction);
        upload.dependsOn(configuration.getBuildArtifacts());
        upload.setDescription(String.format("Uploads all artifacts belonging to the %s configuration",
                configuration.getName()));
        return upload;
    }

    private void configureLibs(Project project, final JavaPluginConvention javaConvention) {
        Bundle libsBundle = project.getTasks().add(LIBS_TASK_NAME, Bundle.class);
        libsBundle.dependsOn(TEST_TASK_NAME);
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
        Bundle distsBundle = project.getTasks().add(DISTS_TASK_NAME, Bundle.class);
        distsBundle.dependsOn(LIBS_TASK_NAME);
        distsBundle.setDefaultConfigurations(WrapUtil.toList(DISTS_TASK_NAME));
        distsBundle.setDefaultDestinationDir(javaPluginConvention.getDistsDir());
        distsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.DIST);
    }

    private void configureTest(final Project project) {
        project.getTasks().whenTaskAdded(Test.class, new Action<Test>() {
            public void execute(Test test) {
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
