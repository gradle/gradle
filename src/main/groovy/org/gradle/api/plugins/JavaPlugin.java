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
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.specs.DependencyTypeSpec;
import org.gradle.api.artifacts.specs.Type;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.Clean;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Resources;
import org.gradle.api.tasks.Upload;
import org.gradle.api.tasks.bundling.Bundle;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.ide.eclipse.*;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.ForkMode;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaPlugin implements Plugin {
    public static final String RESOURCES = "resources";
    public static final String COMPILE = "compile";
    public static final String TEST_RESOURCES = "testResources";
    public static final String TEST_COMPILE = "testCompile";
    public static final String TEST = "test";
    public static final String LIBS = "libs";
    public static final String DISTS = "dists";
    public static final String UPLOAD_INTERNAL_LIBS = "uploadInternalLibs";
    public static final String UPLOAD_LIBS = "uploadLibs";
    public static final String UPLOAD = "upload";
    public static final String CLEAN = "clean";
    public static final String INIT = "init";
    public static final String JAVADOC = "javadoc";

    public static final String RUNTIME = "runtime";
    public static final String TEST_RUNTIME = "testRuntime";
    public static final String UPLOAD_DISTS = "uploadDists";
    public static final String ECLIPSE = "eclipse";
    public static final String ECLIPSE_CLEAN = "eclipseClean";
    public static final String ECLIPSE_PROJECT = "eclipseProject";
    public static final String ECLIPSE_CP = "eclipseCp";
    public static final String ECLIPSE_WTP_MODULE = "eclipseWtpModule";
    public static final int COMPILE_PRIORITY = 300;
    public static final int RUNTIME_PRIORITY = 200;
    public static final int TEST_COMPILE_PRIORITY = 150;
    public static final int TEST_RUNTIME_PRIORITY = 100;

    public void apply(Project project, PluginRegistry pluginRegistry) {
        apply(project, pluginRegistry, new HashMap<String, Object>());
    }

    public void apply(final Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(ReportingBasePlugin.class, project, customValues);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project, customValues);
        Convention convention = project.getConvention();
        convention.getPlugins().put("java", javaConvention);

        configureDependencyManager(project, javaConvention);
        configureUploadRules(project);

        project.createTask(INIT);

        ((ConventionTask) project.createTask(GUtil.map("type", Clean.class), CLEAN)).
                conventionMapping(GUtil.map(
                        "dir", new ConventionValue() {
                            public Object getValue(Convention convention, Task task) {
                                return project.getBuildDir();
                            }
                        }));

        configureJavaDoc(project);

        ((ConventionTask) project.createTask(GUtil.map("type", Resources.class, "dependsOn", INIT), RESOURCES)).
                conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);

        configureCompile((Compile) project.createTask(GUtil.map("type", Compile.class, "dependsOn", RESOURCES),
                COMPILE), DefaultConventionsToPropertiesMapping.COMPILE);

        configureTestResources(project);

        configureTestCompile((Compile) project.createTask(GUtil.map("type", Compile.class, "dependsOn", TEST_RESOURCES),
                TEST_COMPILE), (Compile) project.task(COMPILE), DefaultConventionsToPropertiesMapping.TEST_COMPILE);

        configureTest(project);

        configureLibs(project, javaConvention);

        configureDists(project, javaConvention);

        project.createTask(UPLOAD);

        configureEclipse(project);
        configureEclipseWtpModule(project);
    }

    private void configureJavaDoc(Project project) {
        Javadoc javadoc = (Javadoc) project.createTask(GUtil.map("type", Javadoc.class), JAVADOC);
        javadoc.conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC);
        javadoc.setResolveInstruction(new ConfigurationResolveInstructionModifier(COMPILE));
    }

    private void configureEclipse(Project project) {
        Task eclipse = project.createTask(ECLIPSE).dependsOn(
                configureEclipseProject(project),
                configureEclipseClasspath(project)
        );
        project.createTask(WrapUtil.toMap("type", EclipseClean.class), ECLIPSE_CLEAN);
    }

    private EclipseProject configureEclipseProject(Project project) {
        EclipseProject eclipseProject = (EclipseProject) project.createTask(GUtil.map("type", EclipseProject.class), ECLIPSE_PROJECT);
        eclipseProject.setProjectName(project.getName());
        eclipseProject.setProjectType(ProjectType.JAVA);
        return eclipseProject;
    }

    private void configureEclipseWtpModule(Project project) {
        EclipseWtpModule eclipseWtpModule = (EclipseWtpModule) project.createTask(GUtil.map("type", EclipseWtpModule.class), ECLIPSE_WTP_MODULE);


        eclipseWtpModule.conventionMapping(GUtil.map(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return GUtil.addLists(java(convention).getSrcDirs(), java(convention).getResourceDirs());
                    }
                }));
    }

    private EclipseClasspath configureEclipseClasspath(Project project) {
        EclipseClasspath eclipseClasspath = (EclipseClasspath) project.createTask(GUtil.map("type", EclipseClasspath.class), ECLIPSE_CP);
        eclipseClasspath.conventionMapping(GUtil.map(
                "srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return GUtil.addLists(java(convention).getSrcDirs(), java(convention).getResourceDirs());
                    }
                },
                "testSrcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return GUtil.addLists(java(convention).getTestSrcDirs(), java(convention).getTestResourceDirs());
                    }
                },
                "outputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return java(convention).getClassesDir();
                    }
                },
                "testOutputDirectory", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return java(convention).getTestClassesDir();
                    }
                },
                "classpathLibs", new ConventionValue() {
                    public Object getValue(Convention convention, final Task task) {
                        return task.getProject().getDependencies().configuration(TEST_RUNTIME).resolve(new ResolveInstructionModifier() {
                            public ResolveInstruction modify(ResolveInstruction resolveInstruction) {
                                return new ResolveInstruction(resolveInstruction).
                                        setDependencySpec(new DependencyTypeSpec(Type.EXTERNAL)).
                                        setFailOnResolveError(((EclipseClasspath) task).getFailForMissingDependencies());
                            }
                        });
                    }
                },
                "projectDependencies", new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        /*
                        * todo We return all project dependencies here, not just the one for runtime. We can't use Ivy here, as we
                        * request the project dependencies not via a resolve. We would have to filter the project dependencies
                        * ourselfes. This is not completely trivial due to configuration inheritance.
                        */
                        return task.getProject().getDependencies().getDependencies(new DependencyTypeSpec(Type.PROJECT));
                    }
                }));
        return eclipseClasspath;
    }

    private void configureTestResources(Project project) {
        ConventionTask testResources = (ConventionTask) project.createTask(GUtil.map("type", Resources.class, "dependsOn", COMPILE), TEST_RESOURCES);
        testResources.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST);
        testResources.conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
    }

    private void configureUploadRules(final Project project) {
        project.addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>Internal: Upload the project artifacts of a configuration to the internal Gradle repository.";
            }

            public void apply(String taskName) {
                List<ConfigurationResolver> configurations = project.getDependencies().getConfigurations();
                for (ConfigurationResolver configuration : configurations) {
                    if (taskName.equals(configuration.getUploadInternalTaskName())) {
                        Upload uploadInternal = createUploadTask(configuration.getUploadInternalTaskName(), configuration, project);
                        uploadInternal.getPublishInstruction().getModuleDescriptor().setPublish(true);
                        uploadInternal.getUploadResolvers().add(project.getDependencies().getBuildResolver(), null);
                    }
                }
            }
        });

        project.addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>: Upload the project artifacts of a configuration to a public Gradle repository.";
            }

            public void apply(String taskName) {
                List<ConfigurationResolver> configurations = project.getDependencies().getConfigurations();
                for (ConfigurationResolver configuration : configurations) {
                    if (taskName.equals(configuration.getUploadTaskName())) {
                        createUploadTask(configuration.getUploadTaskName(), configuration, project);
                    }
                }
            }
        });
    }

    private Upload createUploadTask(String name, final ConfigurationResolver configuration, Project project) {
        final Upload upload = (Upload) project.createTask(GUtil.map("type", Upload.class), name);
        ConfigurationPublishInstruction publishInstruction = new ConfigurationPublishInstruction(configuration.getName());
        publishInstruction.getModuleDescriptor().setIvyFileParentDir(project.getBuildDir());
        upload.setPublishInstruction(publishInstruction);
        upload.dependsOn(configuration.getBuildArtifactDependencies());
        return upload;
    }

    private void configureLibs(Project project, final JavaPluginConvention javaConvention) {
        Bundle libsBundle = (Bundle) project.createTask(GUtil.map("type", Bundle.class, "dependsOn", TEST), LIBS);
        libsBundle.setDefaultConfigurations(WrapUtil.toList(Dependency.MASTER_CONFIGURATION));
        libsBundle.setDefaultDestinationDir(project.getBuildDir());
        libsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.LIB);
        Jar jar = libsBundle.jar();
        jar.conventionMapping(WrapUtil.<String, ConventionValue>toMap("resourceCollections",
                new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return WrapUtil.toList(new FileSet(javaConvention.getClassesDir()));
                    }
                }));
    }

    private void configureDists(Project project, JavaPluginConvention javaPluginConvention) {
        Bundle distsBundle = (Bundle) project.createTask(GUtil.map("type", Bundle.class, "dependsOn", LIBS), DISTS);
        distsBundle.setDefaultConfigurations(WrapUtil.toList(DISTS));
        distsBundle.setDefaultDestinationDir(javaPluginConvention.getDistsDir());
        distsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.DIST);
    }

    private void configureTest(Project project) {
        final Test test = (Test) project.createTask(GUtil.map("type", Test.class, "dependsOn", TEST_COMPILE), TEST);
        test.conventionMapping(DefaultConventionsToPropertiesMapping.TEST);
        test.setResolveInstruction(new ConfigurationResolveInstructionModifier(TEST_RUNTIME));
        addDependsOnProjectDependencies(test, TEST_RUNTIME);
        test.doFirst(new TaskAction() {
            public void execute(Task task) {
                Test test = (Test) task;
                List unmanagedClasspathFromTestCompile = ((Compile) test.getProject().task(TEST_COMPILE)).getUnmanagedClasspath();
                test.unmanagedClasspath(unmanagedClasspathFromTestCompile.toArray(new Object[unmanagedClasspathFromTestCompile.size()]));
            }
        });
    }

    void configureDependencyManager(Project project, JavaPluginConvention javaPluginConvention) {
        project.setProperty("status", "integration");
        DependencyManager dependencies = project.getDependencies();
        dependencies.addConfiguration(COMPILE).setVisible(false).setTransitive(false);
        dependencies.addConfiguration(RUNTIME).setVisible(false).extendsFrom(COMPILE);
        dependencies.addConfiguration(TEST_COMPILE).setVisible(false).extendsFrom(COMPILE).setTransitive(false);
        dependencies.addConfiguration(TEST_RUNTIME).setVisible(false).extendsFrom(RUNTIME, TEST_COMPILE);
        dependencies.addConfiguration(Dependency.MASTER_CONFIGURATION);
        dependencies.addConfiguration(Dependency.DEFAULT_CONFIGURATION).extendsFrom(RUNTIME, Dependency.MASTER_CONFIGURATION);
        dependencies.addConfiguration(DISTS);

        configureMavenScopeMappings(dependencies);
    }

    private void configureMavenScopeMappings(DependencyManager dependencyManager) {
        dependencyManager.getDefaultMavenScopeMapping().addMapping(COMPILE_PRIORITY, COMPILE, Conf2ScopeMappingContainer.COMPILE);
        dependencyManager.getDefaultMavenScopeMapping().addMapping(RUNTIME_PRIORITY, RUNTIME, Conf2ScopeMappingContainer.RUNTIME);
        dependencyManager.getDefaultMavenScopeMapping().addMapping(TEST_COMPILE_PRIORITY, TEST_COMPILE, Conf2ScopeMappingContainer.TEST);
        dependencyManager.getDefaultMavenScopeMapping().addMapping(TEST_RUNTIME_PRIORITY, TEST_RUNTIME, Conf2ScopeMappingContainer.TEST);
    }

    protected Compile configureTestCompile(Compile testCompile, final Compile compile, Map propertyMapping) {
        testCompile.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST);
        configureCompileInternal(testCompile, propertyMapping);
        testCompile.setResolveInstruction(new ConfigurationResolveInstructionModifier(TEST_COMPILE));
        addDependsOnProjectDependencies(testCompile, TEST_COMPILE);
        return (Compile) testCompile.doFirst(new TaskAction() {
            public void execute(Task task) {
                Compile testCompile = (Compile) task;
                if (compile.getUnmanagedClasspath() != null) {
                    testCompile.unmanagedClasspath((Object[]) compile.getUnmanagedClasspath().toArray(new Object[compile.getUnmanagedClasspath().size()]));
                }
            }
        });
    }

    protected Compile configureCompile(Compile compile, Map propertyMapping) {
        compile.setResolveInstruction(new ConfigurationResolveInstructionModifier(COMPILE));
        addDependsOnProjectDependencies(compile, COMPILE);
        configureCompileInternal(compile, propertyMapping);
        return compile;
    }

    protected Compile configureCompileInternal(Compile compile, Map propertyMapping) {
        compile.conventionMapping(propertyMapping);
        return compile;
    }

    private void addDependsOnProjectDependencies(final Task task, String configurationName) {
        Project project = task.getProject();
        DependencyManager dependencyManager = project.getDependencies();
        final ConfigurationResolver configurationResolver = dependencyManager.configuration(configurationName);
        task.dependsOn(configurationResolver.getBuildProjectDependencies());
    }

    protected JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }
}
