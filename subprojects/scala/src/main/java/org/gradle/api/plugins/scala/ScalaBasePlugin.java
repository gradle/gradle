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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
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
import org.gradle.api.internal.tasks.scala.DefaultScalaPluginExtension;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.ScalaSourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDoc;
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
    public static final String DEFAULT_ZINC_VERSION = "1.6.1";
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
    private final JvmEcosystemUtilities jvmEcosystemUtilities;

    @Inject
    public ScalaBasePlugin(ObjectFactory objectFactory, JvmEcosystemUtilities jvmEcosystemUtilities) {
        this.objectFactory = objectFactory;
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        ScalaRuntime scalaRuntime = project.getExtensions().create(SCALA_RUNTIME_EXTENSION_NAME, ScalaRuntime.class, project);
        ScalaPluginExtension scalaPluginExtension = project.getExtensions().create(ScalaPluginExtension.class, "scala", DefaultScalaPluginExtension.class);

        Usage incrementalAnalysisUsage = objectFactory.named(Usage.class, "incremental-analysis");
        Category incrementalAnalysisCategory = objectFactory.named(Category.class, "scala-analysis");
        configureConfigurations((ProjectInternal) project, incrementalAnalysisCategory, incrementalAnalysisUsage, scalaPluginExtension);

        configureCompileDefaults(project, scalaRuntime, (DefaultJavaPluginExtension) javaPluginExtension(project));
        configureSourceSetDefaults((ProjectInternal) project, incrementalAnalysisCategory, incrementalAnalysisUsage);
        configureScaladoc(project, scalaRuntime);
    }

    private void configureConfigurations(final ProjectInternal project, Category incrementalAnalysisCategory, final Usage incrementalAnalysisUsage, ScalaPluginExtension scalaPluginExtension) {
        DependencyHandler dependencyHandler = project.getDependencies();

        Configuration plugins = project.getConfigurations().resolvableBucket(SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME);
        plugins.setTransitive(false);
        jvmEcosystemUtilities.configureAsRuntimeClasspath(plugins);

        Configuration zinc = project.getConfigurations().resolvableBucket(ZINC_CONFIGURATION_NAME);
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

        @SuppressWarnings("deprecation") final Configuration incrementalAnalysisElements = project.getConfigurations().createWithRole("incrementalScalaAnalysisElements", ConfigurationRolesForMigration.INTENDED_CONSUMABLE_BUCKET_TO_INTENDED_CONSUMABLE);
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

    private void configureSourceSetDefaults(final ProjectInternal project, Category incrementalAnalysisCategory, final Usage incrementalAnalysisUsage) {
        javaPluginExtension(project).getSourceSets().all(sourceSet -> {

            ScalaSourceDirectorySet scalaSource = getScalaSourceDirectorySet(sourceSet);
            sourceSet.getExtensions().add(ScalaSourceDirectorySet.class, "scala", scalaSource);
            scalaSource.srcDir(project.file("src/" + sourceSet.getName() + "/scala"));

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            final FileCollection scalaSourceFiles = scalaSource;
            sourceSet.getResources().getFilter().exclude(
                spec(element -> scalaSourceFiles.contains(element.getFile()))
            );
            sourceSet.getAllJava().source(scalaSource);
            sourceSet.getAllSource().source(scalaSource);

            Configuration incrementalAnalysis = createIncrementalAnalysisConfigurationFor(project.getConfigurations(), incrementalAnalysisCategory, incrementalAnalysisUsage, sourceSet);

            createScalaCompileTask(project, sourceSet, scalaSource, incrementalAnalysis);
        });
    }

    /**
     * In 9.0, once {@link org.gradle.api.internal.tasks.DefaultScalaSourceSet} is removed, we can update this to only construct the source directory
     * set instead of the entire source set.
     */
    @SuppressWarnings("deprecation")
    private ScalaSourceDirectorySet getScalaSourceDirectorySet(SourceSet sourceSet) {
        org.gradle.api.internal.tasks.DefaultScalaSourceSet scalaSourceSet = objectFactory.newInstance(org.gradle.api.internal.tasks.DefaultScalaSourceSet.class, ((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
        new DslObject(sourceSet).getConvention().getPlugins().put("scala", scalaSourceSet);
        return scalaSourceSet.getScala();
    }

    private static Configuration createIncrementalAnalysisConfigurationFor(RoleBasedConfigurationContainerInternal configurations, Category incrementalAnalysisCategory, Usage incrementalAnalysisUsage, SourceSet sourceSet) {
        Configuration classpath = configurations.getByName(sourceSet.getImplementationConfigurationName());
        @SuppressWarnings("deprecation") Configuration incrementalAnalysis = configurations.createWithRole("incrementalScalaAnalysisFor" + sourceSet.getName(), ConfigurationRolesForMigration.INTENDED_RESOLVABLE_BUCKET_TO_INTENDED_RESOLVABLE);
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
            viewConfiguration.componentFilter(element -> element instanceof ProjectComponentIdentifier);
        }).getFiles());

        // See https://github.com/gradle/gradle/issues/14434.  We do this so that the incrementalScalaAnalysisForXXX configuration
        // is resolved during task graph calculation.  It is not an input, but if we leave it to be resolved during task execution,
        // it can potentially block trying to resolve project dependencies.
        scalaCompile.dependsOn(scalaCompile.getAnalysisFiles());
    }

    private static void configureCompileDefaults(final Project project, final ScalaRuntime scalaRuntime, final DefaultJavaPluginExtension javaExtension) {
        project.getTasks().withType(ScalaCompile.class).configureEach(compile -> {
            ConventionMapping conventionMapping = compile.getConventionMapping();
            conventionMapping.map("scalaClasspath", (Callable<FileCollection>) () -> scalaRuntime.inferScalaClasspath(compile.getClasspath()));
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

    private void configureScaladoc(final Project project, final ScalaRuntime scalaRuntime) {
        project.getTasks().withType(ScalaDoc.class).configureEach(scalaDoc -> {
            scalaDoc.getConventionMapping().map("scalaClasspath", (Callable<FileCollection>) () -> scalaRuntime.inferScalaClasspath(scalaDoc.getClasspath()));
            scalaDoc.getConventionMapping().map("destinationDir", (Callable<File>) () -> javaPluginExtension(project).getDocsDir().dir("scaladoc").get().getAsFile());
            scalaDoc.getConventionMapping().map("title", (Callable<String>) () -> project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle());
            scalaDoc.getJavaLauncher().convention(getJavaLauncher(project));
        });
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
