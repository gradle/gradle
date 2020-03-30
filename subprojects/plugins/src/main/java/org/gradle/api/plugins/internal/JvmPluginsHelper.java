/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.TextUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

/**
 * Helpers for Jvm plugins. They are in a separate class so that they don't leak
 * into the public API.
 */
public class JvmPluginsHelper {
    private static void registerClassesDirVariant(final SourceSet sourceSet, ObjectFactory objectFactory, Configuration configuration) {
        // Define a classes variant to use for compilation
        ConfigurationPublications publications = configuration.getOutgoing();
        ConfigurationVariantInternal variant = (ConfigurationVariantInternal) publications.getVariants().maybeCreate("classes");
        variant.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.CLASSES));
        variant.artifactsProvider(new Factory<List<PublishArtifact>>() {
            @Nullable
            @Override
            public List<PublishArtifact> create() {
                Set<File> classesDirs = sourceSet.getOutput().getClassesDirs().getFiles();
                DefaultSourceSetOutput output = Cast.uncheckedCast(sourceSet.getOutput());
                TaskDependency compileDependencies = output.getCompileDependencies();
                ImmutableList.Builder<PublishArtifact> artifacts = ImmutableList.builderWithExpectedSize(classesDirs.size());
                for (File classesDir : classesDirs) {
                    // this is an approximation: all "compiled" sources will use the same task dependency
                    artifacts.add(new IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, compileDependencies) {
                        @Override
                        public File getFile() {
                            return classesDir;
                        }
                    });
                }
                return artifacts.build();
            }
        });
    }

    public static void addApiToSourceSet(SourceSet sourceSet, ConfigurationContainer configurations) {
        Configuration apiConfiguration = configurations.maybeCreate(sourceSet.getApiConfigurationName());
        apiConfiguration.setVisible(false);
        apiConfiguration.setDescription("API dependencies for " + sourceSet + ".");
        apiConfiguration.setCanBeResolved(false);
        apiConfiguration.setCanBeConsumed(false);

        Configuration apiElementsConfiguration = configurations.getByName(sourceSet.getApiElementsConfigurationName());
        apiElementsConfiguration.extendsFrom(apiConfiguration);

        Configuration implementationConfiguration = configurations.getByName(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileConfiguration = configurations.getByName(sourceSet.getCompileConfigurationName());
        apiConfiguration.extendsFrom(compileConfiguration);
    }

    public static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, CompileOptions options, final Project target) {
        configureForSourceSet(sourceSet, sourceDirectorySet, compile, target);
        configureAnnotationProcessorPath(sourceSet, sourceDirectorySet, options, target);
    }

    private static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, final Project target) {
        compile.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
        compile.setSource(sourceSet.getJava());

        ConfigurableFileCollection classpath = compile.getProject().getObjects().fileCollection();
        classpath.from((Callable<Object>) () -> sourceSet.getCompileClasspath().plus(target.files(sourceSet.getJava().getClassesDirectory())));

        compile.getConventionMapping().map("classpath", () -> classpath);
    }

    public static void configureAnnotationProcessorPath(final SourceSet sourceSet, SourceDirectorySet sourceDirectorySet, CompileOptions options, final Project target) {
        final ConventionMapping conventionMapping = new DslObject(options).getConventionMapping();
        conventionMapping.map("annotationProcessorPath", sourceSet::getAnnotationProcessorPath);
        String annotationProcessorGeneratedSourcesChildPath = "generated/sources/annotationProcessor/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        options.getGeneratedSourceOutputDirectory().convention(target.getLayout().getBuildDirectory().dir(annotationProcessorGeneratedSourcesChildPath));
    }

    /***
     * For compatibility with https://plugins.gradle.org/plugin/io.freefair.aspectj
     */
    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, Provider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        TaskProvider<? extends AbstractCompile> taskProvider = Cast.uncheckedCast(compileTask);
        configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, taskProvider, options);
    }

    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath));

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.addClassesDir(() -> sourceDirectorySet.getDestinationDirectory().getAsFile().get());
        sourceSetOutput.registerCompileTask(compileTask);
        sourceSetOutput.getGeneratedSourcesDirs().from(options.flatMap(CompileOptions::getGeneratedSourceOutputDirectory));

        sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory);
    }

    public static void configureClassesDirectoryVariant(SourceSet sourceSet, Project target, String targetConfigName, final String usage) {
        target.getConfigurations().all(config -> {
            if (targetConfigName.equals(config.getName())) {
                registerClassesDirVariant(sourceSet, target.getObjects(), config);
            }
        });
    }

    public static void configureJavaDocTask(@Nullable String featureName, SourceSet sourceSet, TaskContainer tasks, JavaPluginExtension javaPluginExtension) {
        String javadocTaskName = sourceSet.getJavadocTaskName();
        if (!tasks.getNames().contains(javadocTaskName)) {
            tasks.register(javadocTaskName, Javadoc.class, javadoc -> {
                javadoc.setDescription("Generates Javadoc API documentation for the " + (featureName == null ? "main source code." : "'" + featureName + "' feature."));
                javadoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
                javadoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
                javadoc.setSource(sourceSet.getAllJava());
                javadoc.getModularClasspathHandling().getInferModulePath().convention(javaPluginExtension.getModularClasspathHandling().getInferModulePath());
            });
        }
    }

    public static void configureDocumentationVariantWithArtifact(String variantName, @Nullable String featureName, String docsType, List<Capability> capabilities, String jarTaskName, Object artifactSource, @Nullable AdhocComponentWithVariants component, ConfigurationContainer configurations, TaskContainer tasks, ObjectFactory objectFactory) {
        Configuration variant = configurations.maybeCreate(variantName);
        variant.setVisible(false);
        variant.setDescription(docsType + " elements for " + (featureName == null ? "main" : featureName) + ".");
        variant.setCanBeResolved(false);
        variant.setCanBeConsumed(true);
        variant.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        variant.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        variant.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        variant.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, docsType));
        capabilities.forEach(variant.getOutgoing()::capability);

        if (!tasks.getNames().contains(jarTaskName)) {
            TaskProvider<Jar> jarTask = tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the " + (featureName == null ? "main " + docsType + "." : (docsType + " of the '" + featureName + "' feature.")));
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(artifactSource);
                jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(featureName == null ? docsType : (featureName + "-" + docsType)));
            });
            if (tasks.getNames().contains(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)) {
                tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(jarTask));
            }
        }
        TaskProvider<Task> jar = tasks.named(jarTaskName);
        variant.getOutgoing().artifact(new LazyPublishArtifact(jar));
        if (component != null) {
            component.addVariantsFromConfiguration(variant, new JavaConfigurationVariantMapping("runtime", true));
        }
    }

    @Nullable
    public static AdhocComponentWithVariants findJavaComponent(SoftwareComponentContainer components) {
        SoftwareComponent component = components.findByName("java");
        if (component instanceof AdhocComponentWithVariants) {
            return (AdhocComponentWithVariants) component;
        }
        return null;
    }

    public static void configureAttributesForCompileClasspath(ConfigurationInternal configuration, JavaPluginConvention javaPluginConvention, ObjectFactory objectFactory, boolean javaClasspathPackaging) {
        configuration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        configuration.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        configuration.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, javaClasspathPackaging? LibraryElements.JAR : LibraryElements.CLASSES));
        configuration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        configuration.beforeLocking(configureDefaultTargetPlatform(javaPluginConvention));
    }

    public static void configureAttributesForRuntimeClasspath(ConfigurationInternal configuration, JavaPluginConvention javaPluginConvention, ObjectFactory objectFactory) {
        configuration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        configuration.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        configuration.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR));
        configuration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        configuration.beforeLocking(configureDefaultTargetPlatform(javaPluginConvention));
    }

    public static Action<ConfigurationInternal> configureDefaultTargetPlatform(final JavaPluginConvention convention) {
        return conf -> {
            if (!convention.getAutoTargetJvmDisabled()) {
                JavaEcosystemSupport.configureDefaultTargetPlatform(conf, convention.getTargetCompatibility());
            }
        };
    }

    /**
     * A custom artifact type which allows the getFile call to be done lazily only when the
     * artifact is actually needed.
     */
    public abstract static class IntermediateJavaArtifact extends AbstractPublishArtifact {
        private final String type;

        public IntermediateJavaArtifact(String type, Object task) {
            super(task);
            this.type = type;
        }

        @Override
        public String getName() {
            return getFile().getName();
        }

        @Override
        public String getExtension() {
            return "";
        }

        @Override
        public String getType() {
            return type;
        }

        @Nullable
        @Override
        public String getClassifier() {
            return null;
        }

        @Override
        public Date getDate() {
            return null;
        }

        @Override
        public boolean shouldBePublished() {
            return false;
        }
    }
}
