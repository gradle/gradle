/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.plugins.scala;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.ScalaSourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.api.tasks.scala.internal.ScalaRuntimeHelper;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.scala.tasks.AbstractScalaCompile;
import org.gradle.language.scala.tasks.KeepAliveMode;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;
import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * <p>A {@link Plugin} which compiles and tests Scala sources.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/scala_plugin.html">Scala plugin reference</a>
 */
public abstract class ScalaBasePlugin implements Plugin<Project> {

    /**
     * Default Scala Zinc compiler version
     *
     * @since 6.0
     */
    public static final String DEFAULT_ZINC_VERSION = "1.10.4";
    private static final String DEFAULT_SCALA_ZINC_VERSION = "2.13";

    @VisibleForTesting
    public static final String ZINC_CONFIGURATION_NAME = "zinc";
    public static final String SCALA_RUNTIME_EXTENSION_NAME = "scalaRuntime";

    /**
     * Configuration for scala compiler plugins.
     *
     * @since 6.4
     */
    public static final String SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME = "scalaCompilerPlugins";

    private final ObjectFactory objectFactory;
    private final JvmPluginServices jvmPluginServices;
    private final DependencyFactory dependencyFactory;

    @Inject
    public ScalaBasePlugin(
        ObjectFactory objectFactory,
        JvmPluginServices jvmPluginServices,
        DependencyFactory dependencyFactory
    ) {
        this.objectFactory = objectFactory;
        this.jvmPluginServices = jvmPluginServices;
        this.dependencyFactory = dependencyFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        ScalaRuntime scalaRuntime = project.getExtensions().create(SCALA_RUNTIME_EXTENSION_NAME, ScalaRuntime.class, project);

        ScalaPluginExtension scalaPluginExtension = project.getExtensions().create("scala", ScalaPluginExtension.class);
        scalaPluginExtension.getZincVersion().convention(ScalaBasePlugin.DEFAULT_ZINC_VERSION);

        Provider<ResolvableConfiguration> toolchainClasspath = createToolchainRuntimeClasspath(project, scalaPluginExtension);

        Usage incrementalAnalysisUsage = objectFactory.named(Usage.class, "incremental-analysis");
        Category incrementalAnalysisCategory = objectFactory.named(Category.class, "scala-analysis");
        configureConfigurations((ProjectInternal) project, incrementalAnalysisCategory, incrementalAnalysisUsage, scalaPluginExtension);

        configureCompileDefaults(project, scalaRuntime, (DefaultJavaPluginExtension) javaPluginExtension(project), scalaPluginExtension, toolchainClasspath);
        configureSourceSetDefaults((ProjectInternal) project, incrementalAnalysisCategory, incrementalAnalysisUsage, scalaPluginExtension);
        configureScaladoc(project, scalaRuntime, scalaPluginExtension, toolchainClasspath);
    }

    @SuppressWarnings("deprecation")
    private void configureConfigurations(final ProjectInternal project, Category incrementalAnalysisCategory, final Usage incrementalAnalysisUsage, ScalaPluginExtension scalaPluginExtension) {
        DependencyHandler dependencyHandler = project.getDependencies();

        Configuration plugins = project.getConfigurations().resolvableDependencyScopeUnlocked(SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME);
        plugins.setTransitive(false);
        jvmPluginServices.configureAsRuntimeClasspath(plugins);

        Configuration zinc = project.getConfigurations().resolvableDependencyScopeUnlocked(ZINC_CONFIGURATION_NAME);
        zinc.setVisible(false);
        zinc.setDescription("The Zinc incremental compiler to be used for this Scala project.");

        zinc.getResolutionStrategy().eachDependency(rule -> {
            if (rule.getRequested().getGroup().equals("com.typesafe.zinc") && rule.getRequested().getName().equals("zinc")) {
                rule.useTarget("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + DEFAULT_ZINC_VERSION);
                rule.because("Typesafe Zinc is no longer maintained.");
            }
        });

        zinc.defaultDependencies(dependencies -> {
            dependencies.add(dependencyHandler.create("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + scalaPluginExtension.getZincVersion().get()));
            // Add safeguard and clear error if the user changed the scala version when using default zinc
            zinc.getIncoming().afterResolve(resolvableDependencies -> {
                resolvableDependencies.getResolutionResult().allComponents(component -> {
                    if (component.getModuleVersion() != null && component.getModuleVersion().getName().equals("scala-library")) {
                        if (!component.getModuleVersion().getVersion().startsWith(DEFAULT_SCALA_ZINC_VERSION)) {
                            throw new InvalidUserCodeException("The version of 'scala-library' was changed while using the default Zinc version. " +
                                "Version " + component.getModuleVersion().getVersion() + " is not compatible with org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + DEFAULT_ZINC_VERSION);
                        }
                    }
                });
            });
        });

        zinc.getDependencyConstraints().add(dependencyHandler.getConstraints().create(Log4jBannedVersion.LOG4J2_CORE_COORDINATES, constraint -> constraint.version(version -> {
            version.require(Log4jBannedVersion.LOG4J2_CORE_REQUIRED_VERSION);
            version.reject(Log4jBannedVersion.LOG4J2_CORE_VULNERABLE_VERSION_RANGE);
        })));

        @SuppressWarnings("deprecation") final Configuration incrementalAnalysisElements = project.getConfigurations().migratingUnlocked("incrementalScalaAnalysisElements", ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE);
        incrementalAnalysisElements.setVisible(false);
        incrementalAnalysisElements.setDescription("Incremental compilation analysis files");
        incrementalAnalysisElements.getAttributes().attribute(USAGE_ATTRIBUTE, incrementalAnalysisUsage);
        incrementalAnalysisElements.getAttributes().attribute(CATEGORY_ATTRIBUTE, incrementalAnalysisCategory);

        AttributeMatchingStrategy<Usage> matchingStrategy = dependencyHandler.getAttributesSchema().attribute(USAGE_ATTRIBUTE);
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules.class, actionConfiguration -> {
            actionConfiguration.params(incrementalAnalysisUsage);
            actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API));
            actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        });
    }

    private Provider<ResolvableConfiguration> createToolchainRuntimeClasspath(Project project, ScalaPluginExtension scalaPluginExtension) {
        Provider<DependencyScopeConfiguration> scalaToolchain = project.getConfigurations().dependencyScope("scalaToolchain", conf -> {
            conf.setDescription("Dependencies for the Scala toolchain");
            conf.getDependencies().addLater(createScalaCompilerDependency(scalaPluginExtension));
            conf.getDependencies().addLater(createScalaBridgeDependency(scalaPluginExtension));
            conf.getDependencies().addLater(createScalaCompilerInterfaceDependency(scalaPluginExtension));
            conf.getDependencies().addLater(createScaladocDependency(scalaPluginExtension));
        });

        return project.getConfigurations().resolvable("scalaToolchainRuntimeClasspath", conf -> {
            conf.setDescription("Runtime classpath for the Scala toolchain");
            conf.extendsFrom(scalaToolchain.get());
            jvmPluginServices.configureAsRuntimeClasspath(conf);
        });
    }

    private void configureSourceSetDefaults(final ProjectInternal project, Category incrementalAnalysisCategory, final Usage incrementalAnalysisUsage, ScalaPluginExtension scalaPluginExtension) {
        javaPluginExtension(project).getSourceSets().all(sourceSet -> {

            ScalaSourceDirectorySet scalaSource = createScalaSourceDirectorySet(sourceSet);
            sourceSet.getExtensions().add(ScalaSourceDirectorySet.class, "scala", scalaSource);
            scalaSource.srcDir(project.file("src/" + sourceSet.getName() + "/scala"));

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            final FileCollection scalaSourceFiles = scalaSource;
            sourceSet.getResources().getFilter().exclude(
                spec(element -> scalaSourceFiles.contains(element.getFile()))
            );
            sourceSet.getAllJava().source(scalaSource);
            sourceSet.getAllSource().source(scalaSource);

            project.getConfigurations().getByName(sourceSet.getImplementationConfigurationName()).getDependencies().addLater(createScalaDependency(scalaPluginExtension));

            Configuration incrementalAnalysis = createIncrementalAnalysisConfigurationFor(project.getConfigurations(), incrementalAnalysisCategory, incrementalAnalysisUsage, sourceSet);

            createScalaCompileTask(project, sourceSet, scalaSource, incrementalAnalysis);
        });
    }

    /**
     * Determines the scala standard library that user code compiles against.
     */
    private Provider<Dependency> createScalaDependency(ScalaPluginExtension scalaPluginExtension) {
        return scalaPluginExtension.getScalaVersion().map(scalaVersion -> {
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return dependencyFactory.create("org.scala-lang", "scala3-library_3", scalaVersion);
            } else {
                return dependencyFactory.create("org.scala-lang", "scala-library", scalaVersion);
            }
        });
    }

    /**
     * Determines the Scala compiler dependency.
     */
    private Provider<Dependency> createScalaCompilerDependency(ScalaPluginExtension scalaPluginExtension) {
        return scalaPluginExtension.getScalaVersion().map(scalaVersion -> {
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return dependencyFactory.create("org.scala-lang", "scala3-compiler_3", scalaVersion);
            } else {
                return dependencyFactory.create("org.scala-lang", "scala-compiler", scalaVersion);
            }
        });
    }

    /**
     * Determines Scala bridge dependency. In Scala 3 it is released for each Scala
     * version together with the compiler jars. For Scala 2 we download sources jar and compile
     * it later on.
     *
     * @see org.gradle.api.internal.tasks.scala.ZincScalaCompilerFactory
     */
    private Provider<Dependency> createScalaBridgeDependency(ScalaPluginExtension scalaPluginExtension) {
        return scalaPluginExtension.getScalaVersion().zip(scalaPluginExtension.getZincVersion(), (scalaVersion, zincVersion) -> {
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return dependencyFactory.create("org.scala-lang", "scala3-sbt-bridge", scalaVersion);
            } else {
                String scalaMajorMinorVersion = Joiner.on('.').join(Splitter.on('.').splitToList(scalaVersion).subList(0, 2));
                String name = "compiler-bridge_" + scalaMajorMinorVersion;
                ModuleDependency dependency = dependencyFactory.create("org.scala-sbt", name, zincVersion);

                // Use an artifact to remain compatible with Ivy repositories, which
                // don't support variant derivation.
                dependency.artifact(artifact -> {
                    artifact.setClassifier("sources");
                    artifact.setType("jar");
                    artifact.setExtension("jar");
                    artifact.setName(name);
                });

                return dependency;
            }
        });
    }

    /**
     * Determines Scala compiler interfaces dependency.
     */
    private Provider<Dependency> createScalaCompilerInterfaceDependency(ScalaPluginExtension scalaPluginExtension) {
        return scalaPluginExtension.getScalaVersion().zip(scalaPluginExtension.getZincVersion(), (scalaVersion, zincVersion) -> {
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return dependencyFactory.create("org.scala-lang", "scala3-interfaces", scalaVersion);
            } else {
                return dependencyFactory.create("org.scala-sbt", "compiler-interface", zincVersion);
            }
        });
    }

    /**
     * Determines Scaladoc dependency. Note that scaladoc for Scala 2 is packaged along with the compiler.
     */
    private Provider<Dependency> createScaladocDependency(ScalaPluginExtension scalaPluginExtension) {
        return scalaPluginExtension.getScalaVersion().map(scalaVersion -> {
            if (ScalaRuntimeHelper.isScala3(scalaVersion)) {
                return dependencyFactory.create("org.scala-lang", "scaladoc_3", scalaVersion);
            } else {
                return null;
            }
        });
    }

    /**
     * In 9.0, once {@link org.gradle.api.internal.tasks.DefaultScalaSourceSet} is removed, we can update this to only construct the source directory
     * set instead of the entire source set.
     */
    @SuppressWarnings("deprecation")
    private ScalaSourceDirectorySet createScalaSourceDirectorySet(SourceSet sourceSet) {
        org.gradle.api.internal.tasks.DefaultScalaSourceSet scalaSourceSet = objectFactory.newInstance(org.gradle.api.internal.tasks.DefaultScalaSourceSet.class, ((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
        DeprecationLogger.whileDisabled(() ->
            new DslObject(sourceSet).getConvention().getPlugins().put("scala", scalaSourceSet)
        );
        return scalaSourceSet.getScala();
    }

    private static Configuration createIncrementalAnalysisConfigurationFor(RoleBasedConfigurationContainerInternal configurations, Category incrementalAnalysisCategory, Usage incrementalAnalysisUsage, SourceSet sourceSet) {
        Configuration classpath = configurations.getByName(sourceSet.getImplementationConfigurationName());
        @SuppressWarnings("deprecation") Configuration incrementalAnalysis = configurations.migratingUnlocked("incrementalScalaAnalysisFor" + sourceSet.getName(), ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE);
        incrementalAnalysis.setVisible(false);
        incrementalAnalysis.setDescription("Incremental compilation analysis files for " + ((DefaultSourceSet) sourceSet).getDisplayName());
        incrementalAnalysis.extendsFrom(classpath);
        incrementalAnalysis.getAttributes().attribute(USAGE_ATTRIBUTE, incrementalAnalysisUsage);
        incrementalAnalysis.getAttributes().attribute(CATEGORY_ATTRIBUTE, incrementalAnalysisCategory);
        return incrementalAnalysis;
    }

    private void createScalaCompileTask(final Project project, final SourceSet sourceSet, ScalaSourceDirectorySet scalaSource, final Configuration incrementalAnalysis) {
        final TaskProvider<ScalaCompile> compileTask = project.getTasks().register(sourceSet.getCompileTaskName("scala"), ScalaCompile.class, scalaCompile -> {
            JvmPluginsHelper.compileAgainstJavaOutputs(scalaCompile, sourceSet, objectFactory);
            JvmPluginsHelper.configureAnnotationProcessorPath(sourceSet, scalaSource, scalaCompile.getOptions(), project);
            scalaCompile.setDescription("Compiles the " + scalaSource + ".");
            scalaCompile.setSource(scalaSource);
            scalaCompile.getJavaLauncher().convention(getJavaLauncher(project));

            configureIncrementalAnalysis(project, sourceSet, incrementalAnalysis, scalaCompile);
        });
        JvmPluginsHelper.configureOutputDirectoryForSourceSet(sourceSet, scalaSource, project, compileTask, compileTask.map(AbstractScalaCompile::getOptions));

        project.getTasks().named(sourceSet.getClassesTaskName(), task -> task.dependsOn(compileTask));
    }

    private void configureIncrementalAnalysis(Project project, SourceSet sourceSet, Configuration incrementalAnalysis, ScalaCompile scalaCompile) {
        scalaCompile.getAnalysisMappingFile().set(project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".mapping"));

        // cannot compute at task execution time because we need association with source set
        IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
        incrementalOptions.getAnalysisFile().set(
            project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".analysis")
        );

        incrementalOptions.getClassfileBackupDir().set(
            project.getLayout().getBuildDirectory().file("tmp/scala/classfileBackup/" + scalaCompile.getName() + ".bak")
        );

        final Jar jarTask = (Jar) project.getTasks().findByName(sourceSet.getJarTaskName());
        if (jarTask != null) {
            incrementalOptions.getPublishedCode().set(jarTask.getArchiveFile());
        }
        scalaCompile.getAnalysisFiles().from(incrementalAnalysis.getIncoming().artifactView(viewConfiguration -> {
            viewConfiguration.lenient(true);
            viewConfiguration.componentFilter(spec(element -> element instanceof ProjectComponentIdentifier));
        }).getFiles());

        // See https://github.com/gradle/gradle/issues/14434.  We do this so that the incrementalScalaAnalysisForXXX configuration
        // is resolved during task graph calculation.  It is not an input, but if we leave it to be resolved during task execution,
        // it can potentially block trying to resolve project dependencies.
        scalaCompile.dependsOn(scalaCompile.getAnalysisFiles());
    }

    private static void configureCompileDefaults(
        Project project,
        ScalaRuntime scalaRuntime,
        DefaultJavaPluginExtension javaExtension,
        ScalaPluginExtension scalaPluginExtension,
        Provider<ResolvableConfiguration> scalaToolchainRuntimeClasspath
    ) {
        project.getTasks().withType(ScalaCompile.class).configureEach(compile -> {
            ConventionMapping conventionMapping = compile.getConventionMapping();
            conventionMapping.map("scalaClasspath", (Callable<FileCollection>) () -> getScalaToolchainClasspath(
                scalaPluginExtension,
                scalaToolchainRuntimeClasspath,
                scalaRuntime,
                compile.getClasspath()
            ));
            conventionMapping.map("zincClasspath", (Callable<Configuration>) () -> project.getConfigurations().getAt(ZINC_CONFIGURATION_NAME));
            conventionMapping.map("scalaCompilerPlugins", (Callable<FileCollection>) () -> project.getConfigurations().getAt(SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME));
            conventionMapping.map("sourceCompatibility", () -> computeJavaSourceCompatibilityConvention(javaExtension, compile).toString());
            conventionMapping.map("targetCompatibility", () -> computeJavaTargetCompatibilityConvention(javaExtension, compile).toString());
            compile.getScalaCompileOptions().getKeepAliveMode().convention(KeepAliveMode.SESSION);
        });
    }

    private static JavaVersion computeJavaSourceCompatibilityConvention(DefaultJavaPluginExtension javaExtension, ScalaCompile compileTask) {
        JavaVersion rawSourceCompatibility = javaExtension.getRawSourceCompatibility();
        if (rawSourceCompatibility != null) {
            return rawSourceCompatibility;
        }
        return JavaVersion.toVersion(compileTask.getJavaLauncher().get().getMetadata().getLanguageVersion().toString());
    }

    private static JavaVersion computeJavaTargetCompatibilityConvention(DefaultJavaPluginExtension javaExtension, ScalaCompile compileTask) {
        JavaVersion rawTargetCompatibility = javaExtension.getRawTargetCompatibility();
        if (rawTargetCompatibility != null) {
            return rawTargetCompatibility;
        }
        return JavaVersion.toVersion(compileTask.getSourceCompatibility());
    }

    private static void configureScaladoc(
        Project project,
        ScalaRuntime scalaRuntime,
        ScalaPluginExtension scalaPluginExtension,
        Provider<ResolvableConfiguration> scalaToolchainRuntimeClasspath
    ) {
        project.getTasks().withType(ScalaDoc.class).configureEach(scalaDoc -> {
            scalaDoc.getConventionMapping().map("scalaClasspath", (Callable<FileCollection>) () -> getScalaToolchainClasspath(
                scalaPluginExtension,
                scalaToolchainRuntimeClasspath,
                scalaRuntime,
                scalaDoc.getClasspath()
            ));
            scalaDoc.getConventionMapping().map("destinationDir", (Callable<File>) () -> javaPluginExtension(project).getDocsDir().dir("scaladoc").get().getAsFile());
            scalaDoc.getConventionMapping().map("title", (Callable<String>) () -> project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
            scalaDoc.getJavaLauncher().convention(getJavaLauncher(project));
        });
    }

    private static FileCollection getScalaToolchainClasspath(
        ScalaPluginExtension scalaPluginExtension,
        Provider<ResolvableConfiguration> scalaToolchainRuntimeClasspath,
        ScalaRuntime scalaRuntime,
        FileCollection taskClasspath
    ) {
        if (scalaPluginExtension.getScalaVersion().isPresent()) {
            return scalaToolchainRuntimeClasspath.get();
        } else {
            // TODO: Deprecate this path in 9.x when we de-incubate ScalaPluginExtension#getScalaVersion()
            return scalaRuntime.inferScalaClasspath(taskClasspath);
        }
    }

    private static Provider<JavaLauncher> getJavaLauncher(Project project) {
        final JavaPluginExtension extension = javaPluginExtension(project);
        final JavaToolchainService service = extensionOf(project, JavaToolchainService.class);
        return service.launcherFor(extension.getToolchain());
    }

    private static JavaPluginExtension javaPluginExtension(Project project) {
        return extensionOf(project, JavaPluginExtension.class);
    }

    private static <T> T extensionOf(ExtensionAware extensionAware, Class<T> type) {
        return extensionAware.getExtensions().getByType(type);
    }

    static class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage> {
        private final ImmutableSet<Usage> expectedUsages;
        private final Usage javaRuntime;

        @Inject
        UsageDisambiguationRules(Usage incrementalAnalysis, Usage javaApi, Usage javaRuntime) {
            this.javaRuntime = javaRuntime;
            this.expectedUsages = ImmutableSet.of(incrementalAnalysis, javaApi, javaRuntime);
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getConsumerValue() == null) {
                if (details.getCandidateValues().equals(expectedUsages)) {
                    details.closestMatch(javaRuntime);
                }
            }
        }
    }
}
