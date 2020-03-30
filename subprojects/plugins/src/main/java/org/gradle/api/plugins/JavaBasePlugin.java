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
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CompilationSourceDirs;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.DefaultJavaPluginConvention;
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.JUnitXmlReport;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.jpms.JavaModuleDetector;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.tasks.ProcessResources;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

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

    /**
     * Set this property to use JARs build from subprojects, instead of the classes folder from these project, on the compile classpath.
     * The main use case for this is to mitigate performance issues on very large multi-projects building on Windows.
     * Setting this property will cause the 'jar' task of all subprojects in the dependency tree to always run during compilation.
     *
     * @since 5.6
     */
    @Incubating
    public static final String COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY = "org.gradle.java.compile-classpath-packaging";

    /**
     * A list of known artifact types which are known to prevent from
     * publication.
     *
     * @since 5.3
     */
    public static final Set<String> UNPUBLISHABLE_VARIANT_ARTIFACTS = ImmutableSet.of(
            ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
            ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY,
            ArtifactTypeDefinition.DIRECTORY_TYPE
    );

    private final ObjectFactory objectFactory;
    private final JavaInstallationRegistry javaInstallationRegistry;
    private final boolean javaClasspathPackaging;

    @Inject
    public JavaBasePlugin(ObjectFactory objectFactory, JavaInstallationRegistry javaInstallationRegistry) {
        this.objectFactory = objectFactory;
        this.javaInstallationRegistry = javaInstallationRegistry;
        this.javaClasspathPackaging = Boolean.getBoolean(COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY);
    }

    @Override
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
        JavaPluginConvention javaConvention = new DefaultJavaPluginConvention(project, objectFactory);
        project.getConvention().getPlugins().put("java", javaConvention);
        project.getExtensions().add(SourceSetContainer.class, "sourceSets", javaConvention.getSourceSets());
        project.getExtensions().create(JavaPluginExtension.class, "java", DefaultJavaPluginExtension.class, javaConvention, project);
        project.getExtensions().add(JavaInstallationRegistry.class, "javaInstalls", javaInstallationRegistry);
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
        JavaEcosystemSupport.configureSchema(attributesSchema, objectFactory);
        project.getDependencies().getArtifactTypes().create(ArtifactTypeDefinition.JAR_TYPE).getAttributes()
                .attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME))
                .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));
    }

    private void configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                ConventionMapping outputConventionMapping = ((IConventionAware) sourceSet.getOutput()).getConventionMapping();

                ConfigurationContainer configurations = project.getConfigurations();

                defineConfigurationsForSourceSet(sourceSet, configurations, pluginConvention);
                definePathsForSourceSet(sourceSet, outputConventionMapping, project);

                createProcessResourcesTask(sourceSet, sourceSet.getResources(), project);
                TaskProvider<JavaCompile> compileTask = createCompileJavaTask(sourceSet, sourceSet.getJava(), project);
                createClassesTask(sourceSet, project);

                // If we are potentially compiling a module, we require JARs of all dependencies as they may potentially include an Automatic-Module-Name
                if (JavaModuleDetector.isModuleSource(CompilationSourceDirs.inferSourceRoots((FileTreeInternal) sourceSet.getJava().getAsFileTree()))) {
                    Configuration compileClasspathConfiguration = configurations.getByName(sourceSet.getCompileClasspathConfigurationName());
                    // Ideally, the attribute would be configured lazily taking 'JavaCompile.modularClasspathHandling' into account after the task is realized and configured.
                    // For that we need: https://github.com/gradle/gradle/issues/11139
                    compileClasspathConfiguration.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));
                }

                JvmPluginsHelper.configureOutputDirectoryForSourceSet(sourceSet, sourceSet.getJava(), project, compileTask, compileTask.map(new Transformer<CompileOptions, JavaCompile>() {
                    @Override
                    public CompileOptions transform(JavaCompile javaCompile) {
                        return javaCompile.getOptions();
                    }
                }));
            }
        });
    }

    private TaskProvider<JavaCompile> createCompileJavaTask(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target) {
        return target.getTasks().register(sourceSet.getCompileJavaTaskName(), JavaCompile.class, new Action<JavaCompile>() {
            @Override
            public void execute(JavaCompile compileTask) {
                compileTask.setDescription("Compiles " + sourceDirectorySet + ".");
                compileTask.setSource(sourceDirectorySet);
                ConventionMapping conventionMapping = compileTask.getConventionMapping();
                conventionMapping.map("classpath", new Callable<Object>() {
                    @Override
                    public Object call() {
                        return sourceSet.getCompileClasspath();
                    }
                });
                JvmPluginsHelper.configureAnnotationProcessorPath(sourceSet, sourceDirectorySet, compileTask.getOptions(), target);
                String generatedHeadersDir = "generated/sources/headers/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
                compileTask.getOptions().getHeaderOutputDirectory().convention(target.getLayout().getBuildDirectory().dir(generatedHeadersDir));
                JavaPluginExtension javaPluginExtension = target.getExtensions().getByType(JavaPluginExtension.class);
                compileTask.getRelease().convention(javaPluginExtension.getRelease());
                compileTask.getModularClasspathHandling().getInferModulePath().convention(javaPluginExtension.getModularClasspathHandling().getInferModulePath());
            }
        });
    }

    private void createProcessResourcesTask(final SourceSet sourceSet, final SourceDirectorySet resourceSet, final Project target) {
        target.getTasks().register(sourceSet.getProcessResourcesTaskName(), ProcessResources.class, new Action<ProcessResources>() {
            @Override
            public void execute(ProcessResources resourcesTask) {
                resourcesTask.setDescription("Processes " + resourceSet + ".");
                new DslObject(resourcesTask.getRootSpec()).getConventionMapping().map("destinationDir", new Callable<File>() {
                    @Override
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
            @Override
            public Object call() {
                String classesDirName = "resources/" + sourceSet.getName();
                return new File(project.getBuildDir(), classesDirName);
            }
        });

        sourceSet.getJava().srcDir("src/" + sourceSet.getName() + "/java");
        sourceSet.getResources().srcDir("src/" + sourceSet.getName() + "/resources");
    }

    private void defineConfigurationsForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations, final JavaPluginConvention convention) {
        String compileConfigurationName = sourceSet.getCompileConfigurationName();
        String implementationConfigurationName = sourceSet.getImplementationConfigurationName();
        String runtimeConfigurationName = sourceSet.getRuntimeConfigurationName();
        String runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        String compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
        String compileClasspathConfigurationName = sourceSet.getCompileClasspathConfigurationName();
        String annotationProcessorConfigurationName = sourceSet.getAnnotationProcessorConfigurationName();
        String runtimeClasspathConfigurationName = sourceSet.getRuntimeClasspathConfigurationName();
        String apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
        String runtimeElementsConfigurationName = sourceSet.getRuntimeElementsConfigurationName();
        String sourceSetName = sourceSet.toString();
        Action<ConfigurationInternal> configureDefaultTargetPlatform = JvmPluginsHelper.configureDefaultTargetPlatform(convention);


        DeprecatableConfiguration compileConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(compileConfigurationName);
        compileConfiguration.setVisible(false);
        compileConfiguration.setDescription("Dependencies for " + sourceSetName + " (deprecated, use '" + implementationConfigurationName + "' instead).");

        Configuration implementationConfiguration = configurations.maybeCreate(implementationConfigurationName);
        implementationConfiguration.setVisible(false);
        implementationConfiguration.setDescription("Implementation only dependencies for " + sourceSetName + ".");
        implementationConfiguration.setCanBeConsumed(false);
        implementationConfiguration.setCanBeResolved(false);
        implementationConfiguration.extendsFrom(compileConfiguration);

        DeprecatableConfiguration runtimeConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(runtimeConfigurationName);
        runtimeConfiguration.setVisible(false);
        runtimeConfiguration.extendsFrom(compileConfiguration);
        runtimeConfiguration.setDescription("Runtime dependencies for " + sourceSetName + " (deprecated, use '" + runtimeOnlyConfigurationName + "' instead).");

        DeprecatableConfiguration compileOnlyConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(compileOnlyConfigurationName);
        compileOnlyConfiguration.setVisible(false);
        compileOnlyConfiguration.setDescription("Compile only dependencies for " + sourceSetName + ".");

        ConfigurationInternal compileClasspathConfiguration = (ConfigurationInternal) configurations.maybeCreate(compileClasspathConfigurationName);
        compileClasspathConfiguration.setVisible(false);
        compileClasspathConfiguration.extendsFrom(compileOnlyConfiguration, implementationConfiguration);
        compileClasspathConfiguration.setDescription("Compile classpath for " + sourceSetName + ".");
        compileClasspathConfiguration.setCanBeConsumed(false);
        JvmPluginsHelper.configureAttributesForCompileClasspath(compileClasspathConfiguration, convention, objectFactory, javaClasspathPackaging);

        ConfigurationInternal annotationProcessorConfiguration = (ConfigurationInternal) configurations.maybeCreate(annotationProcessorConfigurationName);
        annotationProcessorConfiguration.setVisible(false);
        annotationProcessorConfiguration.setDescription("Annotation processors and their dependencies for " + sourceSetName + ".");
        annotationProcessorConfiguration.setCanBeConsumed(false);
        annotationProcessorConfiguration.setCanBeResolved(true);
        JvmPluginsHelper.configureAttributesForRuntimeClasspath(annotationProcessorConfiguration, convention, objectFactory);

        Configuration runtimeOnlyConfiguration = configurations.maybeCreate(runtimeOnlyConfigurationName);
        runtimeOnlyConfiguration.setVisible(false);
        runtimeOnlyConfiguration.setCanBeConsumed(false);
        runtimeOnlyConfiguration.setCanBeResolved(false);
        runtimeOnlyConfiguration.setDescription("Runtime only dependencies for " + sourceSetName + ".");

        ConfigurationInternal runtimeClasspathConfiguration = (ConfigurationInternal) configurations.maybeCreate(runtimeClasspathConfigurationName);
        runtimeClasspathConfiguration.setVisible(false);
        runtimeClasspathConfiguration.setCanBeConsumed(false);
        runtimeClasspathConfiguration.setCanBeResolved(true);
        runtimeClasspathConfiguration.setDescription("Runtime classpath of " + sourceSetName + ".");
        runtimeClasspathConfiguration.extendsFrom(runtimeOnlyConfiguration, runtimeConfiguration, implementationConfiguration);
        JvmPluginsHelper.configureAttributesForRuntimeClasspath(runtimeClasspathConfiguration, convention, objectFactory);

        sourceSet.setCompileClasspath(compileClasspathConfiguration);
        sourceSet.setRuntimeClasspath(sourceSet.getOutput().plus(runtimeClasspathConfiguration));
        sourceSet.setAnnotationProcessorPath(annotationProcessorConfiguration);

        compileConfiguration.deprecateForDeclaration(implementationConfigurationName);
        compileConfiguration.deprecateForConsumption(apiElementsConfigurationName);
        compileConfiguration.deprecateForResolution(compileClasspathConfigurationName);

        compileOnlyConfiguration.deprecateForConsumption(apiElementsConfigurationName);
        compileOnlyConfiguration.deprecateForResolution(compileClasspathConfigurationName);

        runtimeConfiguration.deprecateForDeclaration(runtimeOnlyConfigurationName);
        runtimeConfiguration.deprecateForConsumption(runtimeElementsConfigurationName);
        runtimeConfiguration.deprecateForResolution(runtimeClasspathConfigurationName);

        compileClasspathConfiguration.deprecateForDeclaration(implementationConfigurationName, compileOnlyConfigurationName);
        runtimeClasspathConfiguration.deprecateForDeclaration(implementationConfigurationName, compileOnlyConfigurationName, runtimeOnlyConfigurationName);
    }

    private void configureCompileDefaults(final Project project, final JavaPluginConvention javaConvention) {
        project.getTasks().withType(AbstractCompile.class).configureEach(new Action<AbstractCompile>() {
            @Override
            public void execute(final AbstractCompile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("sourceCompatibility", new Callable<Object>() {
                    @Override
                    public Object call() {
                        return javaConvention.getSourceCompatibility().toString();
                    }
                });
                conventionMapping.map("targetCompatibility", new Callable<Object>() {
                    @Override
                    public Object call() {
                        return javaConvention.getTargetCompatibility().toString();
                    }
                });
            }
        });
    }

    private void configureJavaDoc(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Javadoc.class).configureEach(new Action<Javadoc>() {
            @Override
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    @Override
                    public Object call() {
                        return new File(convention.getDocsDir(), "javadoc");
                    }
                });
                javadoc.getConventionMapping().map("title", new Callable<Object>() {
                    @Override
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
                boolean hasIncludedBuilds = !buildTask.getProject().getGradle().getIncludedBuilds().isEmpty();
                buildTask.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        if (hasIncludedBuilds) {
                            task.getLogger().warn("[composite-build] Warning: `" + task.getPath() + "` task does not build included builds.");
                        }
                    }
                });
            }
        });
    }

    private void configureTest(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Test.class).configureEach(test -> configureTestDefaults(test, project, convention));
    }

    private void configureTestDefaults(final Test test, Project project, final JavaPluginConvention convention) {
        DirectoryReport htmlReport = test.getReports().getHtml();
        JUnitXmlReport xmlReport = test.getReports().getJunitXml();

        // TODO - should replace `testResultsDir` and `testReportDir` with `Property` types and map their values
        xmlReport.getOutputLocation().convention(project.getLayout().getProjectDirectory().dir(project.provider(() -> new File(convention.getTestResultsDir(), test.getName()).getAbsolutePath())));
        htmlReport.getOutputLocation().convention(project.getLayout().getProjectDirectory().dir(project.provider(() -> new File(convention.getTestReportDir(), test.getName()).getAbsolutePath())));
        test.getBinaryResultsDirectory().convention(project.getLayout().getProjectDirectory().dir(project.provider(() -> new File(convention.getTestResultsDir(), test.getName() + "/binary").getAbsolutePath())));
        test.workingDir(project.getProjectDir());
    }
}
