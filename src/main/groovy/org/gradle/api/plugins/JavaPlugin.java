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
import org.gradle.api.dependencies.Filter;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.MavenPomGenerator;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.Clean;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Resources;
import org.gradle.api.tasks.Upload;
import org.gradle.api.tasks.bundling.Bundle;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.ide.eclipse.EclipseClasspath;
import org.gradle.api.tasks.ide.eclipse.EclipseClean;
import org.gradle.api.tasks.ide.eclipse.EclipseProject;
import org.gradle.api.tasks.ide.eclipse.ProjectType;
import org.gradle.api.tasks.ide.eclipse.EclipseWtpModule;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.ForkMode;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
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
        apply(project, pluginRegistry, new HashMap());
    }

    public void apply(Project project, PluginRegistry pluginRegistry, Map customValues) {
        JavaPluginConvention javaConvention = new JavaPluginConvention(project, customValues);
        Convention convention = project.getConvention();
        convention.getPlugins().put("java", javaConvention);

        configureDependencyManager(project, javaConvention);

        project.createTask(INIT);

        ((ConventionTask) project.createTask(GUtil.map("type", Clean.class), CLEAN)).
                conventionMapping(DefaultConventionsToPropertiesMapping.CLEAN);

        ((ConventionTask) project.createTask(GUtil.map("type", Javadoc.class), JAVADOC)).
                conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC);

        ((ConventionTask) project.createTask(GUtil.map("type", Resources.class, "dependsOn", INIT), RESOURCES)).
                conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES);

        configureCompile((Compile) project.createTask(GUtil.map("type", Compile.class, "dependsOn", RESOURCES), COMPILE),
                DefaultConventionsToPropertiesMapping.COMPILE);

        configureTestResources(project);

        configureTestCompile((Compile) project.createTask(GUtil.map("type", Compile.class, "dependsOn", TEST_RESOURCES), TEST_COMPILE),
                (Compile) project.task(COMPILE),
                DefaultConventionsToPropertiesMapping.TEST_COMPILE);

        configureTest(project);

        configureLibs(project, javaConvention);

        configureUploadInternalLibs(project);
        configureUploadLibs(project);

        configureDists(project);

        Upload distsUpload = (Upload) project.createTask(GUtil.map("type", Upload.class, "dependsOn", DISTS), UPLOAD_DISTS);
        distsUpload.getConfigurations().add(DISTS);

        configureEclipse(project);
        configureEclipseWtpModule(project);
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
            public Object getValue(Convention convention, Task task) {
                return task.getProject().getDependencies().resolve(TEST_RUNTIME, ((EclipseClasspath) task).getFailForMissingDependencies(), false);
            }
        },
                "projectDependencies", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                /*
                 * todo We return all project dependencies here, not just the one for runtime. We can't use Ivy here, as we
                 * request the project dependencies not via a resolve. We would have to filter the project dependencies
                 * ourselfes. This is not completely trivial due to configuration inheritance.
                 */
                return task.getProject().getDependencies().getDependencies(Filter.PROJECTS_ONLY);
            }
        }));
        return eclipseClasspath;
    }

    private void configureTestResources(Project project) {
        ConventionTask testResources = (ConventionTask) project.createTask(GUtil.map("type", Resources.class, "dependsOn", COMPILE), TEST_RESOURCES);
        testResources.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST);
        testResources.conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
    }

    private void configureUploadInternalLibs(Project project) {
        Upload uploadInternalLibs = (Upload) project.createTask(GUtil.map("type", Upload.class, "dependsOn", LIBS), UPLOAD_INTERNAL_LIBS);
        uploadInternalLibs.getConfigurations().add(LIBS);
        uploadInternalLibs.getUploadResolvers().add(project.getDependencies().getBuildResolver(), null);
        uploadInternalLibs.setUploadModuleDescriptor(true);
    }

    private void configureUploadLibs(Project project) {
        Upload uploadLibs = (Upload) project.createTask(GUtil.map("type", Upload.class, "dependsOn", LIBS), UPLOAD_LIBS);
        uploadLibs.getConfigurations().add(LIBS);
        uploadLibs.setUploadModuleDescriptor(true);
    }

    private void configureLibs(Project project, final JavaPluginConvention javaConvention) {
        Bundle libsBundle = (Bundle) project.createTask(GUtil.map("type", Bundle.class, "dependsOn", TEST), LIBS);
        libsBundle.setDefaultConfigurations(WrapUtil.toList(LIBS));
        libsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.LIB);
        Jar jar = libsBundle.jar();
        jar.conventionMapping(WrapUtil.<String, ConventionValue>toMap("resourceCollections",
                new ConventionValue() {
                    public Object getValue(Convention convention, Task task) {
                        return WrapUtil.toList(new FileSet(javaConvention.getClassesDir()));
                    }
                }));
    }

    private void configureDists(Project project) {
        Bundle distsBundle = (Bundle) project.createTask(GUtil.map("type", Bundle.class, "dependsOn", UPLOAD_LIBS), DISTS);
        distsBundle.setDefaultConfigurations(WrapUtil.toList(DISTS));
        distsBundle.conventionMapping(DefaultConventionsToPropertiesMapping.DIST);
    }

    private void configureTest(Project project) {
        final Test test = (Test) project.createTask(GUtil.map("type", Test.class, "dependsOn", TEST_COMPILE), TEST);
        test.conventionMapping(DefaultConventionsToPropertiesMapping.TEST);
        test.getOptions().setFork(true);
        test.getOptions().getForkOptions().setForkMode(ForkMode.PER_TEST);
        test.getOptions().getForkOptions().setDir(project.getProjectDir());
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
        dependencies.addConfiguration(TEST_COMPILE).setVisible(false).setTransitive(false).extendsFrom(COMPILE);
        dependencies.addConfiguration(TEST_RUNTIME).setVisible(false).extendsFrom(RUNTIME, TEST_COMPILE);
        dependencies.addConfiguration(LIBS);
        dependencies.addConfiguration(Dependency.DEFAULT_CONFIGURATION).extendsFrom(RUNTIME, Dependency.MASTER_CONFIGURATION);
        dependencies.addConfiguration(Dependency.MASTER_CONFIGURATION);
        dependencies.addConfiguration(DISTS);
        dependencies.setArtifactProductionTaskName(UPLOAD_INTERNAL_LIBS);
        dependencies.getArtifactParentDirs().add(project.getBuildDir());
        dependencies.getArtifactParentDirs().add(javaPluginConvention.getDistsDir());
        dependencies.linkConfWithTask(COMPILE, COMPILE);
        dependencies.linkConfWithTask(COMPILE, JAVADOC);
        dependencies.linkConfWithTask(RUNTIME, TEST);
        dependencies.linkConfWithTask(TEST_COMPILE, TEST_COMPILE);
        dependencies.linkConfWithTask(TEST_RUNTIME, TEST);

        configureMavenScopeMappings(dependencies.getMaven());
    }

    private void configureMavenScopeMappings(MavenPomGenerator mavenPomGenerator) {
        mavenPomGenerator.setPackaging(MavenPomGenerator.JAR_PACKAGING);
        mavenPomGenerator.getScopeMappings().addMapping(COMPILE_PRIORITY, COMPILE, MavenPomGenerator.COMPILE);
        mavenPomGenerator.getScopeMappings().addMapping(RUNTIME_PRIORITY, RUNTIME, MavenPomGenerator.RUNTIME);
        mavenPomGenerator.getScopeMappings().addMapping(TEST_COMPILE_PRIORITY, TEST_COMPILE, MavenPomGenerator.TEST);
        mavenPomGenerator.getScopeMappings().addMapping(TEST_RUNTIME_PRIORITY, TEST_RUNTIME, MavenPomGenerator.TEST);
    }

    protected Compile configureTestCompile(Compile testCompile, final Compile compile, Map propertyMapping) {
        testCompile.getSkipProperties().add(Task.AUTOSKIP_PROPERTY_PREFIX + TEST);
        configureCompile(testCompile, propertyMapping);
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
        compile.conventionMapping(propertyMapping);
        return compile;
    }

    protected JavaPluginConvention java(Convention convention) {
        return (JavaPluginConvention) convention.getPlugins().get("java");
    }
}
