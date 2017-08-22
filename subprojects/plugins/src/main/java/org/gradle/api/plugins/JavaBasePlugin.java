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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.execution.TaskExecutionGraph;
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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.SourceSetUtil;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
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
import org.gradle.util.SingleMessageLogger;
import org.gradle.util.WrapUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

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
    private final ObjectFactory objectFactory;

    @Inject
    public JavaBasePlugin(Instantiator instantiator, JavaToolChain javaToolChain, ITaskFactory taskFactory, ModelRegistry modelRegistry, ObjectFactory objectFactory) {
        this.instantiator = instantiator;
        this.javaToolChain = javaToolChain;
        this.taskFactory = taskFactory;
        this.modelRegistry = modelRegistry;
        this.objectFactory = objectFactory;
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
        AttributeMatchingStrategy<Usage> matchingStrategy = project.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE);
        matchingStrategy.getCompatibilityRules().add(UsageCompatibilityRules.class);
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules.class, new Action<ActionConfiguration>() {
            @Override
            public void execute(ActionConfiguration actionConfiguration) {
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API_CLASSES));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_JARS));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_CLASSES));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_RESOURCES));
            }
        });

        project.getDependencies().getArtifactTypes().create(ArtifactTypeDefinition.JAR_TYPE).getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_JARS));
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

    private void createCompileJavaTaskForBinary(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target) {
        JavaCompile compileTask = target.getTasks().create(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        compileTask.setDescription("Compiles " + sourceDirectorySet + ".");
        compileTask.setSource(sourceDirectorySet);
        ConventionMapping conventionMapping = compileTask.getConventionMapping();
        conventionMapping.map("classpath", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getCompileClasspath();
            }
        });

        SourceSetUtil.configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, compileTask, target);
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

    private void createBinaryLifecycleTask(final SourceSet sourceSet, Project target) {
        sourceSet.compiledBy(sourceSet.getClassesTaskName());

        Task classesTask = target.task(sourceSet.getClassesTaskName());
        classesTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
        classesTask.setDescription("Assembles " + sourceSet.getOutput() + ".");
        classesTask.dependsOn(sourceSet.getOutput().getDirs());
        classesTask.dependsOn(sourceSet.getCompileJavaTaskName());
        classesTask.dependsOn(sourceSet.getProcessResourcesTaskName());
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
        compileClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));

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
        runtimeClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));

        sourceSet.setCompileClasspath(compileClasspathConfiguration);
        sourceSet.setRuntimeClasspath(sourceSet.getOutput().plus(runtimeClasspathConfiguration));

    }

    @Deprecated
    public void configureForSourceSet(final SourceSet sourceSet, final AbstractCompile compile) {
        SingleMessageLogger.nagUserOfDiscontinuedMethod("configureForSourceSet(SourceSet, AbstractCompile)");
        ConventionMapping conventionMapping;
        compile.setDescription("Compiles the " + sourceSet.getJava() + ".");
        conventionMapping = compile.getConventionMapping();
        compile.setSource(sourceSet.getJava());
        conventionMapping.map("classpath", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getCompileClasspath().plus(compile.getProject().files(sourceSet.getJava().getOutputDir()));
            }
        });
        // TODO: This doesn't really work any more, but configureForSourceSet is a public API.
        // This should allow builds to continue to work, but it will kill build caching for JavaCompile
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
            public void execute(final Test test) {
                configureTestDefaults(test, project, convention);
            }
        });
        project.getGradle().getTaskGraph().whenReady(new Action<TaskExecutionGraph>() {
            @Override
            public void execute(final TaskExecutionGraph taskExecutionGraph) {
                project.getTasks().withType(Test.class, new Action<Test>() {

                    @Override
                    public void execute(Test test) {
                        if (taskExecutionGraph.hasTask(test)) {
                            //TODO we should deprecate and remove these old properties
                            //they can be replaced by --tests and --debug-jvm
                            configureBasedOnSingleProperty(test);
                            overwriteDebugIfDebugPropertyIsSet(test);
                        }
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

    static class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage> {
        final Usage javaApi;
        final Usage javaApiClasses;
        final Usage javaRuntimeJars;
        final Usage javaRuntimeClasses;
        final Usage javaRuntimeResources;

        @Inject
        UsageDisambiguationRules(Usage javaApi, Usage javaApiClasses, Usage javaRuntimeJars, Usage javaRuntimeClasses, Usage javaRuntimeResources) {
            this.javaApi = javaApi;
            this.javaApiClasses = javaApiClasses;
            this.javaRuntimeJars = javaRuntimeJars;
            this.javaRuntimeClasses = javaRuntimeClasses;
            this.javaRuntimeResources = javaRuntimeResources;
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getCandidateValues().equals(ImmutableSet.of(javaApi, javaApiClasses))) {
                details.closestMatch(javaApiClasses);
            } else if (details.getConsumerValue() == null) {
                if (details.getCandidateValues().equals(ImmutableSet.of(javaApi, javaRuntimeJars))) {
                    // Use the Jars when nothing has been requested
                    details.closestMatch(javaRuntimeJars);
                } else if (details.getCandidateValues().equals(ImmutableSet.of(javaRuntimeJars, javaRuntimeClasses, javaRuntimeResources))) {
                    // Use the Jars when nothing has been requested
                    details.closestMatch(javaRuntimeJars);
                }
            } else if (details.getConsumerValue() != null) {
                Usage requested = details.getConsumerValue();
                if ((requested.getName().equals(Usage.JAVA_API) || requested.getName().equals(Usage.JAVA_API_CLASSES)) && details.getCandidateValues().equals(ImmutableSet.of(javaApi, javaRuntimeJars))) {
                    // Prefer the API over the runtime when the API has been requested
                    details.closestMatch(javaApi);
                }
            }
        }
    }

    static class UsageCompatibilityRules implements AttributeCompatibilityRule<Usage> {
        @Override
        public void execute(CompatibilityCheckDetails<Usage> details) {
            if (details.getConsumerValue().getName().equals(Usage.JAVA_API)) {
                if (details.getProducerValue().getName().equals(Usage.JAVA_API_CLASSES)) {
                    details.compatible();
                } else if (details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME_JARS)) {
                    // Can use the runtime Jars if present, but prefer Java API
                    details.compatible();
                }
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_API_CLASSES)) {
                if (details.getProducerValue().getName().equals(Usage.JAVA_API)) {
                    // Can use the Java API if present, but prefer Java API classes
                    details.compatible();
                } else if (details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME_JARS)) {
                    // Can use the Java runtime jars if present, but prefer Java API classes
                    details.compatible();
                }
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_RUNTIME) && details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME_JARS)) {
                details.compatible();
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_RUNTIME_CLASSES) && details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME_JARS)) {
                // Can use the Java runtime jars if present, but prefer Java runtime classes
                details.compatible();
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_RUNTIME_RESOURCES) && details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME_JARS)) {
                // Can use the Java runtime jars if present, but prefer Java runtime resources
                details.compatible();
            }
        }
    }
}
