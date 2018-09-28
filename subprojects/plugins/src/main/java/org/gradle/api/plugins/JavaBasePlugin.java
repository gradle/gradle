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
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.ReusableAction;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.DefaultJavaPluginConvention;
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension;
import org.gradle.api.plugins.internal.SourceSetUtil;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.tasks.ProcessResources;

import javax.inject.Inject;
import java.io.File;
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
    private final ObjectFactory objectFactory;

    @Inject
    public JavaBasePlugin(Instantiator instantiator, ObjectFactory objectFactory) {
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
    }

    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply(ReportingBasePlugin.class);

        JavaPluginConvention javaConvention = addExtensions(project);

        configureSourceSetDefaults(javaConvention);
        configureCompileDefaults(project, javaConvention);

        configureJavaDoc(project, javaConvention);
        configureTest(project, javaConvention);
        configureBuildNeeded(project);
        configureBuildDependents(project);
        configureSchema(project);
        bridgeToSoftwareModelIfNecessary(project);
        configureVariantDerivationStrategy(project);
    }

    private void configureVariantDerivationStrategy(ProjectInternal project) {
        ComponentMetadataHandlerInternal metadataHandler = (ComponentMetadataHandlerInternal) project.getDependencies().getComponents();
        metadataHandler.setVariantDerivationStrategy(new JavaEcosystemVariantDerivationStrategy());
    }

    private JavaPluginConvention addExtensions(final ProjectInternal project) {
        JavaPluginConvention javaConvention = new DefaultJavaPluginConvention(project, instantiator);
        project.getConvention().getPlugins().put("java", javaConvention);
        project.getExtensions().add(SourceSetContainer.class, "sourceSets", javaConvention.getSourceSets());
        project.getExtensions().create(JavaPluginExtension.class, "java", DefaultJavaPluginExtension.class, javaConvention);
        return javaConvention;
    }

    private void bridgeToSoftwareModelIfNecessary(ProjectInternal project) {
        project.addRuleBasedPluginListener(new RuleBasedPluginListener() {
            @Override
            public void prepareForRuleBasedPlugins(Project project) {
                project.getPluginManager().apply(JavaBasePluginRules.class);
            }
        });
    }

    private void configureSchema(ProjectInternal project) {
        AttributesSchema attributesSchema = project.getDependencies().getAttributesSchema();
        AttributeMatchingStrategy<Usage> matchingStrategy = attributesSchema.attribute(Usage.USAGE_ATTRIBUTE);
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

    private void configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            public void execute(final SourceSet sourceSet) {
                ConventionMapping outputConventionMapping = ((IConventionAware) sourceSet.getOutput()).getConventionMapping();

                ConfigurationContainer configurations = project.getConfigurations();

                defineConfigurationsForSourceSet(sourceSet, configurations);
                definePathsForSourceSet(sourceSet, outputConventionMapping, project);
                SourceSetUtil.configureOutputDirectoryForSourceSet(sourceSet, sourceSet.getJava(), project);

                createProcessResourcesTask(sourceSet, sourceSet.getResources(), project);
                createCompileJavaTask(sourceSet, sourceSet.getJava(), project);
                createClassesTask(sourceSet, project);
            }
        });
    }

    private void createCompileJavaTask(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target) {
        target.getTasks().register(sourceSet.getCompileJavaTaskName(), JavaCompile.class, new Action<JavaCompile>() {
            @Override
            public void execute(JavaCompile compileTask) {
                compileTask.setDescription("Compiles " + sourceDirectorySet + ".");
                compileTask.setSource(sourceDirectorySet);
                ConventionMapping conventionMapping = compileTask.getConventionMapping();
                conventionMapping.map("classpath", new Callable<Object>() {
                    public Object call() {
                        return sourceSet.getCompileClasspath();
                    }
                });
                SourceSetUtil.configureAnnotationProcessorPath(sourceSet, compileTask.getOptions(), target);
                compileTask.setDestinationDir(target.provider(new Callable<File>() {
                    @Override
                    public File call() {
                        return sourceDirectorySet.getOutputDir();
                    }
                }));
            }
        });
    }

    private void createProcessResourcesTask(final SourceSet sourceSet, final SourceDirectorySet resourceSet, final Project target) {
        target.getTasks().register(sourceSet.getProcessResourcesTaskName(), ProcessResources.class, new Action<ProcessResources>() {
            @Override
            public void execute(ProcessResources resourcesTask) {
                resourcesTask.setDescription("Processes " + resourceSet + ".");
                new DslObject(resourcesTask).getConventionMapping().map("destinationDir", new Callable<File>() {
                    public File call() {
                        return sourceSet.getOutput().getResourcesDir();
                    }
                });
                resourcesTask.from(resourceSet);
            }
        });
    }

    private void createClassesTask(final SourceSet sourceSet, Project target) {
        Provider<Task> classesTask = target.getTasks().register(sourceSet.getClassesTaskName(), new Action<Task>() {
            @Override
            public void execute(Task classesTask) {
                classesTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                classesTask.setDescription("Assembles " + sourceSet.getOutput() + ".");
                classesTask.dependsOn(sourceSet.getOutput().getDirs());
                classesTask.dependsOn(sourceSet.getCompileJavaTaskName());
                classesTask.dependsOn(sourceSet.getProcessResourcesTaskName());
            }
        });
        sourceSet.compiledBy(classesTask);
    }

    private void definePathsForSourceSet(final SourceSet sourceSet, ConventionMapping outputConventionMapping, final Project project) {
        outputConventionMapping.map("resourcesDir", new Callable<Object>() {
            public Object call() {
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
        String annotationProcessorConfigurationName = sourceSet.getAnnotationProcessorConfigurationName();
        String runtimeClasspathConfigurationName = sourceSet.getRuntimeClasspathConfigurationName();
        String sourceSetName = sourceSet.toString();

        Configuration compileConfiguration = configurations.maybeCreate(compileConfigurationName);
        compileConfiguration.setVisible(false);
        compileConfiguration.setDescription("Dependencies for " + sourceSetName + " (deprecated, use '" + implementationConfigurationName + "' instead).");

        Configuration implementationConfiguration = configurations.maybeCreate(implementationConfigurationName);
        implementationConfiguration.setVisible(false);
        implementationConfiguration.setDescription("Implementation only dependencies for " + sourceSetName + ".");
        implementationConfiguration.setCanBeConsumed(false);
        implementationConfiguration.setCanBeResolved(false);
        implementationConfiguration.extendsFrom(compileConfiguration);

        Configuration runtimeConfiguration = configurations.maybeCreate(runtimeConfigurationName);
        runtimeConfiguration.setVisible(false);
        runtimeConfiguration.extendsFrom(compileConfiguration);
        runtimeConfiguration.setDescription("Runtime dependencies for " + sourceSetName + " (deprecated, use '" + runtimeOnlyConfigurationName + "' instead).");

        Configuration compileOnlyConfiguration = configurations.maybeCreate(compileOnlyConfigurationName);
        compileOnlyConfiguration.setVisible(false);
        compileOnlyConfiguration.setDescription("Compile only dependencies for " + sourceSetName + ".");

        Configuration compileClasspathConfiguration = configurations.maybeCreate(compileClasspathConfigurationName);
        compileClasspathConfiguration.setVisible(false);
        compileClasspathConfiguration.extendsFrom(compileOnlyConfiguration, implementationConfiguration);
        compileClasspathConfiguration.setDescription("Compile classpath for " + sourceSetName + ".");
        compileClasspathConfiguration.setCanBeConsumed(false);
        compileClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));

        Configuration annotationProcessorConfiguration = configurations.maybeCreate(annotationProcessorConfigurationName);
        annotationProcessorConfiguration.setVisible(false);
        annotationProcessorConfiguration.setDescription("Annotation processors and their dependencies for " + sourceSetName + ".");
        annotationProcessorConfiguration.setCanBeConsumed(false);
        annotationProcessorConfiguration.setCanBeResolved(true);

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
        sourceSet.setAnnotationProcessorPath(annotationProcessorConfiguration);
    }

    private void configureCompileDefaults(final Project project, final JavaPluginConvention javaConvention) {
        project.getTasks().withType(AbstractCompile.class).configureEach(new Action<AbstractCompile>() {
            public void execute(final AbstractCompile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("sourceCompatibility", new Callable<Object>() {
                    public Object call() {
                        return javaConvention.getSourceCompatibility().toString();
                    }
                });
                conventionMapping.map("targetCompatibility", new Callable<Object>() {
                    public Object call() {
                        return javaConvention.getTargetCompatibility().toString();
                    }
                });
            }
        });
    }

    private void configureJavaDoc(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Javadoc.class).configureEach(new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    public Object call() {
                        return new File(convention.getDocsDir(), "javadoc");
                    }
                });
                javadoc.getConventionMapping().map("title", new Callable<Object>() {
                    public Object call() {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
            }
        });
    }

    private void configureBuildNeeded(Project project) {
        project.getTasks().register(BUILD_NEEDED_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task buildTask) {
                buildTask.setDescription("Assembles and tests this project and all projects it depends on.");
                buildTask.setGroup(BasePlugin.BUILD_GROUP);
                buildTask.dependsOn(BUILD_TASK_NAME);
            }
        });
    }

    private void configureBuildDependents(Project project) {
        project.getTasks().register(BUILD_DEPENDENTS_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task buildTask) {
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
        });
    }

    private void configureTest(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Test.class).configureEach(new Action<Test>() {
            public void execute(final Test test) {
                configureTestDefaults(test, project, convention);
            }
        });
    }

    private void configureTestDefaults(final Test test, Project project, final JavaPluginConvention convention) {
        DslObject htmlReport = new DslObject(test.getReports().getHtml());
        DslObject xmlReport = new DslObject(test.getReports().getJunitXml());

        xmlReport.getConventionMapping().map("destination", new Callable<Object>() {
            public Object call() {
                return new File(convention.getTestResultsDir(), test.getName());
            }
        });
        htmlReport.getConventionMapping().map("destination", new Callable<Object>() {
            public Object call() {
                return new File(convention.getTestReportDir(), test.getName());
            }
        });
        test.getConventionMapping().map("binResultsDir", new Callable<Object>() {
            public Object call() {
                return new File(convention.getTestResultsDir(), test.getName() + "/binary");
            }
        });
        test.workingDir(project.getProjectDir());
    }

    static class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage>, ReusableAction {
        final Usage javaApi;
        final Usage javaApiClasses;
        final Usage javaRuntimeJars;
        final Usage javaRuntimeClasses;
        final Usage javaRuntimeResources;

        final ImmutableSet<Usage> javaApiAndJavaApiClasses;
        final ImmutableSet<Usage> javaApiAndJavaRuntimeJars;
        final ImmutableSet<Usage> javaRuntimeJarsAndJavaRuntimeClassesAndJavaRuntimeResources;

        @Inject
        UsageDisambiguationRules(Usage javaApi, Usage javaApiClasses, Usage javaRuntimeJars, Usage javaRuntimeClasses, Usage javaRuntimeResources) {
            this.javaApi = javaApi;
            this.javaApiClasses = javaApiClasses;
            this.javaRuntimeJars = javaRuntimeJars;
            this.javaRuntimeClasses = javaRuntimeClasses;
            this.javaRuntimeResources = javaRuntimeResources;
            this.javaApiAndJavaApiClasses = ImmutableSet.of(javaApi, javaApiClasses);
            this.javaApiAndJavaRuntimeJars = ImmutableSet.of(javaApi, javaRuntimeJars);
            this.javaRuntimeJarsAndJavaRuntimeClassesAndJavaRuntimeResources = ImmutableSet.of(javaRuntimeJars, javaRuntimeClasses, javaRuntimeResources);
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getCandidateValues().equals(javaApiAndJavaApiClasses)) {
                details.closestMatch(javaApiClasses);
            } else if (details.getConsumerValue() == null) {
                if (details.getCandidateValues().equals(javaApiAndJavaRuntimeJars)) {
                    // Use the Jars when nothing has been requested
                    details.closestMatch(javaRuntimeJars);
                } else if (details.getCandidateValues().equals(javaRuntimeJarsAndJavaRuntimeClassesAndJavaRuntimeResources)) {
                    // Use the Jars when nothing has been requested
                    details.closestMatch(javaRuntimeJars);
                }
            } else if (details.getConsumerValue() != null) {
                Usage requested = details.getConsumerValue();
                if ((requested.getName().equals(Usage.JAVA_API) || requested.getName().equals(Usage.JAVA_API_CLASSES)) && details.getCandidateValues().equals(javaApiAndJavaRuntimeJars)) {
                    // Prefer the API over the runtime when the API has been requested
                    details.closestMatch(javaApi);
                }
            }
        }
    }

    static class UsageCompatibilityRules implements AttributeCompatibilityRule<Usage>, ReusableAction {
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
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_RUNTIME_CLASSES) && details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME)) {
                // Can use the Java runtime if present, but prefer Java runtime classes
                details.compatible();
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_RUNTIME_RESOURCES) && details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME)) {
                // Can use the Java runtime if present, but prefer Java runtime resources
                details.compatible();
            } else if (details.getConsumerValue().getName().equals(Usage.JAVA_RUNTIME_JARS) && details.getProducerValue().getName().equals(Usage.JAVA_RUNTIME)) {
                // Can use the Java runtime if present, but prefer Java runtime jar
                details.compatible();
            }
        }
    }
}
