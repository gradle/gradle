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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.java.DefaultJavaSourceSet;
import org.gradle.api.internal.java.DefaultJvmResourceSet;
import org.gradle.api.internal.jvm.ClassDirectoryBinarySpecInternal;
import org.gradle.api.internal.jvm.DefaultClassDirectoryBinarySpec;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.tasks.SourceSetCompileClasspath;
import org.gradle.api.internal.tasks.testing.NoMatchingTestsReporter;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.Classpath;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.platform.base.plugins.BinaryBasePlugin;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.WrapUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.api.attributes.Usage.*;

/**
 * <p>A {@link org.gradle.api.Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 */
public class JavaBasePlugin implements Plugin<ProjectInternal> {
    public static final String CHECK_TASK_NAME = LifecycleBasePlugin.CHECK_TASK_NAME;

    public static final String VERIFICATION_GROUP = LifecycleBasePlugin.VERIFICATION_GROUP;
    public static final String BUILD_TASK_NAME = LifecycleBasePlugin.BUILD_TASK_NAME;
    public static final String BUILD_DEPENDENTS_TASK_NAME = "buildDependents";
    public static final String BUILD_NEEDED_TASK_NAME = "buildNeeded";
    public static final String DOCUMENTATION_GROUP = "documentation";

    private final Instantiator instantiator;
    private final JavaToolChain javaToolChain;
    private final ITaskFactory taskFactory;
    private final ModelRegistry modelRegistry;

    @Inject
    public JavaBasePlugin(Instantiator instantiator, JavaToolChain javaToolChain, ITaskFactory taskFactory, ModelRegistry modelRegistry) {
        this.instantiator = instantiator;
        this.javaToolChain = javaToolChain;
        this.taskFactory = taskFactory;
        this.modelRegistry = modelRegistry;
    }

    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply(ReportingBasePlugin.class);
        project.getPluginManager().apply(LanguageBasePlugin.class);
        project.getPluginManager().apply(BinaryBasePlugin.class);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project, instantiator);
        project.getConvention().getPlugins().put("java", javaConvention);

        configureCompileDefaults(project, javaConvention);
        BridgedBinaries binaries = configureSourceSetDefaults(javaConvention);

        modelRegistry.register(ModelRegistrations.bridgedInstance(ModelReference.of("bridgedBinaries", BridgedBinaries.class), binaries)
            .descriptor("JavaBasePlugin.apply()")
            .hidden(true)
            .build());

        configureJavaDoc(project, javaConvention);
        configureTest(project, javaConvention);
        configureBuildNeeded(project);
        configureBuildDependents(project);
        configureSchema(project);
    }

    private void configureSchema(ProjectInternal project) {
        project.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE);
    }

    private BridgedBinaries configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();
        final List<ClassDirectoryBinarySpecInternal> binaries = Lists.newArrayList();
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            public void execute(final SourceSet sourceSet) {
                ConventionMapping outputConventionMapping = ((IConventionAware) sourceSet.getOutput()).getConventionMapping();

                ConfigurationContainer configurations = project.getConfigurations();

                defineConfigurationsForSourceSet(sourceSet, configurations);
                definePathsForSourceSet(sourceSet, outputConventionMapping, project);

                createProcessResourcesTaskForBinary(sourceSet, sourceSet.getResources(), project);
                createCompileJavaTaskForBinary(sourceSet, sourceSet.getJava(), project);
                createBinaryLifecycleTask(sourceSet, project);

                DefaultComponentSpecIdentifier binaryId = new DefaultComponentSpecIdentifier(project.getPath(), sourceSet.getName());
                ClassDirectoryBinarySpecInternal binary = instantiator.newInstance(DefaultClassDirectoryBinarySpec.class, binaryId, sourceSet, javaToolChain, DefaultJavaPlatform.current(), instantiator, taskFactory);

                Classpath compileClasspath = new SourceSetCompileClasspath(sourceSet);
                DefaultJavaSourceSet javaSourceSet = instantiator.newInstance(DefaultJavaSourceSet.class, binaryId.child("java"), sourceSet.getJava(), compileClasspath);
                JvmResourceSet resourceSet = instantiator.newInstance(DefaultJvmResourceSet.class, binaryId.child("resources"), sourceSet.getResources());

                binary.addSourceSet(javaSourceSet);
                binary.addSourceSet(resourceSet);

                attachTasksToBinary(binary, sourceSet, project);
                binaries.add(binary);
            }
        });
        return new BridgedBinaries(binaries);
    }

    private void createCompileJavaTaskForBinary(final SourceSet sourceSet, SourceDirectorySet javaSourceSet, Project target) {
        JavaCompile compileTask = target.getTasks().create(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        compileTask.setDescription("Compiles " + javaSourceSet + ".");
        compileTask.setSource(javaSourceSet);
        ConventionMapping conventionMapping = compileTask.getConventionMapping();
        conventionMapping.map("classpath", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getCompileClasspath();
            }
        });
        conventionMapping.map("destinationDir", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getOutput().getClassesDir();
            }
        });
    }

    private void createProcessResourcesTaskForBinary(final SourceSet sourceSet, SourceDirectorySet resourceSet, final Project target) {
        Copy resourcesTask = target.getTasks().create(sourceSet.getProcessResourcesTaskName(), ProcessResources.class);
        resourcesTask.setDescription("Processes " + resourceSet + ".");
        new DslObject(resourcesTask).getConventionMapping().map("destinationDir", new Callable<File>() {
            public File call() throws Exception {
                return sourceSet.getOutput().getResourcesDir();
            }
        });
        resourcesTask.from(resourceSet);
    }

    private void createBinaryLifecycleTask(SourceSet sourceSet, Project target) {
        sourceSet.compiledBy(sourceSet.getClassesTaskName());

        Task binaryLifecycleTask = target.task(sourceSet.getClassesTaskName());
        binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
        binaryLifecycleTask.setDescription("Assembles " + sourceSet.getOutput() + ".");
        binaryLifecycleTask.dependsOn(sourceSet.getOutput().getDirs());
        binaryLifecycleTask.dependsOn(sourceSet.getCompileJavaTaskName());
        binaryLifecycleTask.dependsOn(sourceSet.getProcessResourcesTaskName());
    }

    private void attachTasksToBinary(ClassDirectoryBinarySpecInternal binary, SourceSet sourceSet, Project target) {
        Task compileTask = target.getTasks().getByPath(sourceSet.getCompileJavaTaskName());
        Task resourcesTask = target.getTasks().getByPath(sourceSet.getProcessResourcesTaskName());
        Task classesTask = target.getTasks().getByPath(sourceSet.getClassesTaskName());
        binary.getTasks().add(compileTask);
        binary.getTasks().add(resourcesTask);
        binary.getTasks().add(classesTask);
        binary.setBuildTask(classesTask);
    }

    private void definePathsForSourceSet(final SourceSet sourceSet, ConventionMapping outputConventionMapping, final Project project) {
        outputConventionMapping.map("classesDir", new Callable<Object>() {
            public Object call() throws Exception {
                String classesDirName = "classes/" + sourceSet.getName();
                return new File(project.getBuildDir(), classesDirName);
            }
        });
        outputConventionMapping.map("resourcesDir", new Callable<Object>() {
            public Object call() throws Exception {
                String classesDirName = "resources/" + sourceSet.getName();
                return new File(project.getBuildDir(), classesDirName);
            }
        });

        sourceSet.getJava().srcDir("src/" + sourceSet.getName() + "/java");
        sourceSet.getResources().srcDir("src/" + sourceSet.getName() + "/resources");
    }

    private void defineConfigurationsForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations) {
        String compileConfigurationName = sourceSet.getCompileConfigurationName();
        String implementationConfigurationName = sourceSet.getImplementationConfigurationName();
        String runtimeConfigurationName = sourceSet.getRuntimeConfigurationName();
        String runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        String compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
        String compileClasspathConfigurationName = sourceSet.getCompileClasspathConfigurationName();
        String runtimeClasspathConfigurationName = sourceSet.getRuntimeClasspathConfigurationName();
        String sourceSetName = sourceSet.toString();

        Configuration compileConfiguration = configurations.maybeCreate(compileConfigurationName);
        compileConfiguration.setVisible(false);
        compileConfiguration.setDescription("Dependencies for " + sourceSetName + " (deprecated, use '" + implementationConfigurationName + " ' instead).");

        Configuration implementationConfiguration = configurations.maybeCreate(implementationConfigurationName);
        implementationConfiguration.setVisible(false);
        implementationConfiguration.setDescription("Implementation only dependencies for " + sourceSetName + ".");
        implementationConfiguration.setCanBeConsumed(false);
        implementationConfiguration.setCanBeResolved(false);
        implementationConfiguration.extendsFrom(compileConfiguration);

        Configuration runtimeConfiguration = configurations.maybeCreate(runtimeConfigurationName);
        runtimeConfiguration.setVisible(false);
        runtimeConfiguration.extendsFrom(compileConfiguration);
        runtimeConfiguration.setDescription("Runtime dependencies for " + sourceSetName + " (deprecated, use '" + runtimeOnlyConfigurationName + " ' instead).");

        Configuration compileOnlyConfiguration = configurations.maybeCreate(compileOnlyConfigurationName);
        compileOnlyConfiguration.setVisible(false);
        compileOnlyConfiguration.setDescription("Compile only dependencies for " + sourceSetName + ".");

        Configuration compileClasspathConfiguration = configurations.maybeCreate(compileClasspathConfigurationName);
        compileClasspathConfiguration.setVisible(false);
        compileClasspathConfiguration.extendsFrom(compileOnlyConfiguration, implementationConfiguration);
        compileClasspathConfiguration.setDescription("Compile classpath for " + sourceSetName + ".");
        compileClasspathConfiguration.setCanBeConsumed(false);
        compileClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, FOR_COMPILE);

        Configuration runtimeOnlyConfiguration = configurations.maybeCreate(runtimeOnlyConfigurationName);
        runtimeOnlyConfiguration.setVisible(false);
        runtimeOnlyConfiguration.setCanBeConsumed(false);
        runtimeOnlyConfiguration.setCanBeResolved(false);
        runtimeOnlyConfiguration.setDescription("Runtime only dependencies for " + sourceSetName + ".");

        Configuration runtimeClasspathConfiguration = configurations.maybeCreate(runtimeClasspathConfigurationName);
        runtimeClasspathConfiguration.setVisible(false);
        runtimeClasspathConfiguration.setCanBeConsumed(false);
        runtimeClasspathConfiguration.setCanBeResolved(true);
        runtimeClasspathConfiguration.setDescription("Runtime classpath of " + sourceSetName + ".");
        runtimeClasspathConfiguration.extendsFrom(runtimeOnlyConfiguration, runtimeConfiguration, implementationConfiguration);
        runtimeClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, FOR_RUNTIME);

        sourceSet.setCompileClasspath(compileClasspathConfiguration);
        sourceSet.setRuntimeClasspath(sourceSet.getOutput().plus(runtimeClasspathConfiguration));

    }

    public void configureForSourceSet(final SourceSet sourceSet, AbstractCompile compile) {
        ConventionMapping conventionMapping;
        compile.setDescription("Compiles the " + sourceSet.getJava() + ".");
        conventionMapping = compile.getConventionMapping();
        compile.setSource(sourceSet.getJava());
        conventionMapping.map("classpath", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getCompileClasspath();
            }
        });
        conventionMapping.map("destinationDir", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getOutput().getClassesDir();
            }
        });
    }

    private void configureCompileDefaults(final Project project, final JavaPluginConvention javaConvention) {
        project.getTasks().withType(AbstractCompile.class, new Action<AbstractCompile>() {
            public void execute(final AbstractCompile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("sourceCompatibility", new Callable<Object>() {
                    public Object call() throws Exception {
                        return javaConvention.getSourceCompatibility().toString();
                    }
                });
                conventionMapping.map("targetCompatibility", new Callable<Object>() {
                    public Object call() throws Exception {
                        return javaConvention.getTargetCompatibility().toString();
                    }
                });
            }
        });
        project.getTasks().withType(JavaCompile.class, new Action<JavaCompile>() {
            @Override
            public void execute(final JavaCompile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("dependencyCacheDir", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return DeprecationLogger.whileDisabled(new Factory<Object>() {
                            @Override
                            public Object create() {
                                return javaConvention.getDependencyCacheDir();
                            }
                        });
                    }
                });
            }
        });
    }

    private void configureJavaDoc(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Javadoc.class, new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(convention.getDocsDir(), "javadoc");
                    }
                });
                javadoc.getConventionMapping().map("title", new Callable<Object>() {
                    public Object call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
            }
        });
    }

    private void configureBuildNeeded(Project project) {
        DefaultTask buildTask = project.getTasks().create(BUILD_NEEDED_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project and all projects it depends on.");
        buildTask.setGroup(BasePlugin.BUILD_GROUP);
        buildTask.dependsOn(BUILD_TASK_NAME);
    }

    private void configureBuildDependents(Project project) {
        DefaultTask buildTask = project.getTasks().create(BUILD_DEPENDENTS_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project and all projects that depend on it.");
        buildTask.setGroup(BasePlugin.BUILD_GROUP);
        buildTask.dependsOn(BUILD_TASK_NAME);
        buildTask.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (!task.getProject().getGradle().getIncludedBuilds().isEmpty()) {
                    task.getProject().getLogger().warn("[composite-build] Warning: `" + task.getPath() + "` task does not build included builds.");
                }
            }
        });
    }

    private void configureTest(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Test.class, new Action<Test>() {
            public void execute(Test test) {
                configureTestDefaults(test, project, convention);
            }
        });
        project.afterEvaluate(new Action<Project>() {
            public void execute(Project project) {
                project.getTasks().withType(Test.class, new Action<Test>() {
                    public void execute(Test test) {
                        configureBasedOnSingleProperty(test);
                        overwriteDebugIfDebugPropertyIsSet(test);
                    }
                });
            }
        });
    }

    private void overwriteDebugIfDebugPropertyIsSet(Test test) {
        String debugProp = getTaskPrefixedProperty(test, "debug");
        if (debugProp != null) {
            test.prependParallelSafeAction(new Action<Task>() {
                public void execute(Task task) {
                    task.getLogger().info("Running tests for remote debugging.");
                }
            });
            test.setDebug(true);
        }
    }

    private void configureBasedOnSingleProperty(final Test test) {
        String singleTest = getTaskPrefixedProperty(test, "single");
        if (singleTest == null) {
            //configure inputs so that the test task is skipped when there are no source files.
            //unfortunately, this only applies when 'test.single' is *not* applied
            //We should fix this distinction, the behavior with 'test.single' or without it should be the same
            test.getInputs().files(test.getCandidateClassFiles())
                .withPropertyName("nonEmptyCandidateClassFiles")
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .skipWhenEmpty();
            return;
        }
        test.prependParallelSafeAction(new Action<Task>() {
            public void execute(Task task) {
                test.getLogger().info("Running single tests with pattern: {}", test.getIncludes());
            }
        });
        test.setIncludes(WrapUtil.toSet("**/" + singleTest + "*.class"));
        test.addTestListener(new NoMatchingTestsReporter("Could not find matching test for pattern: " + singleTest));
    }

    private String getTaskPrefixedProperty(Task task, String propertyName) {
        String suffix = '.' + propertyName;
        String value = System.getProperty(task.getPath() + suffix);
        if (value == null) {
            return System.getProperty(task.getName() + suffix);
        }
        return value;
    }

    private void configureTestDefaults(final Test test, Project project, final JavaPluginConvention convention) {
        DslObject htmlReport = new DslObject(test.getReports().getHtml());
        DslObject xmlReport = new DslObject(test.getReports().getJunitXml());

        xmlReport.getConventionMapping().map("destination", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(convention.getTestResultsDir(), test.getName());
            }
        });
        htmlReport.getConventionMapping().map("destination", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(convention.getTestReportDir(), test.getName());
            }
        });
        test.getConventionMapping().map("binResultsDir", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(convention.getTestResultsDir(), test.getName() + "/binary");
            }
        });
        test.workingDir(project.getProjectDir());
    }

    static class BridgedBinaries {
        final List<ClassDirectoryBinarySpecInternal> binaries;

        public BridgedBinaries(List<ClassDirectoryBinarySpecInternal> binaries) {
            this.binaries = binaries;
        }
    }

    static class Rules extends RuleSource {
        @Mutate
        void attachBridgedSourceSets(ProjectSourceSet projectSourceSet, BridgedBinaries bridgedBinaries) {
            for (ClassDirectoryBinarySpecInternal binary : bridgedBinaries.binaries) {
                projectSourceSet.addAll(binary.getInputs());
            }
        }

        @Mutate
        void attachBridgedBinaries(BinaryContainer binaries, BridgedBinaries bridgedBinaries) {
            for (BinarySpecInternal binary : bridgedBinaries.binaries) {
                binaries.put(binary.getProjectScopedName(), binary);
            }
        }
    }

}
