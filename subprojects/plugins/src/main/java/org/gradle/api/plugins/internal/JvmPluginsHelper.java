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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.AttributeContainer;
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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.internal.Cast;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static org.gradle.util.internal.TextUtil.camelToKebabCase;

/**
 * Helpers for Jvm plugins. They are in a separate class so that they don't leak
 * into the public API.
 */
public class JvmPluginsHelper {

    /**
     * Adds an API configuration to a source set, so that API dependencies
     * can be declared.
     *
     * @param sourceSet the source set to add an API for
     * @return the created API configuration
     */
    public static Configuration addApiToSourceSet(SourceSet sourceSet, ConfigurationContainer configurations) {
        Configuration apiConfiguration = maybeCreateInvisibleConfig(
            configurations,
            sourceSet.getApiConfigurationName(),
            "API dependencies for " + sourceSet + ".",
            false
        );

        Configuration compileOnlyApiConfiguration = maybeCreateInvisibleConfig(
            configurations,
            sourceSet.getCompileOnlyApiConfigurationName(),
            "Compile only API dependencies for " + sourceSet + ".",
            false
        );

        Configuration apiElementsConfiguration = configurations.getByName(sourceSet.getApiElementsConfigurationName());
        apiElementsConfiguration.extendsFrom(apiConfiguration, compileOnlyApiConfiguration);

        Configuration implementationConfiguration = configurations.getByName(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileOnlyConfiguration = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        compileOnlyConfiguration.extendsFrom(compileOnlyApiConfiguration);

        return apiConfiguration;
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
    @SuppressWarnings("unused")
    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, Provider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        TaskProvider<? extends AbstractCompile> taskProvider = Cast.uncheckedCast(compileTask);
        configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, taskProvider, options, AbstractCompile::getDestinationDirectory);
    }

    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, compileTask, options, AbstractCompile::getDestinationDirectory);
    }

    public static <T extends Task> void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<T> compileTask, Provider<CompileOptions> options, Function<T, DirectoryProperty> classesDirectoryExtractor) {
        final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath));

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.addClassesDir(sourceDirectorySet.getDestinationDirectory());
        sourceSetOutput.registerClassesContributor(compileTask);
        sourceSetOutput.getGeneratedSourcesDirs().from(options.flatMap(CompileOptions::getGeneratedSourceOutputDirectory));
        sourceDirectorySet.compiledBy(compileTask, classesDirectoryExtractor);
    }

    public static void configureJavaDocTask(@Nullable String featureName, SourceSet sourceSet, TaskContainer tasks, @Nullable JavaPluginExtension javaPluginExtension) {
        String javadocTaskName = sourceSet.getJavadocTaskName();
        if (!tasks.getNames().contains(javadocTaskName)) {
            tasks.register(javadocTaskName, Javadoc.class, javadoc -> {
                javadoc.setDescription("Generates Javadoc API documentation for the " + (featureName == null ? "main source code." : "'" + featureName + "' feature."));
                javadoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
                javadoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
                javadoc.setSource(sourceSet.getAllJava());
                if (javaPluginExtension != null) {
                    javadoc.getConventionMapping().map("destinationDir", () -> javaPluginExtension.getDocsDir().dir(javadocTaskName).get().getAsFile());
                    javadoc.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
                }
            });
        }
    }

    public static void configureDocumentationVariantWithArtifact(
        String variantName,
        @Nullable String featureName,
        String docsType,
        List<Capability> capabilities,
        String jarTaskName,
        Object artifactSource,
        @Nullable AdhocComponentWithVariants component,
        ConfigurationContainer configurations,
        TaskContainer tasks,
        ObjectFactory objectFactory
    ) {
        Configuration variant = maybeCreateInvisibleConfig(
            configurations,
            variantName,
            docsType + " elements for " + (featureName == null ? "main" : featureName) + ".",
            true
        );
        AttributeContainer attributes = variant.getAttributes();
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, docsType));
        capabilities.forEach(variant.getOutgoing()::capability);

        if (!tasks.getNames().contains(jarTaskName)) {
            TaskProvider<Jar> jarTask = tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the " + (featureName == null ? "main " + docsType + "." : (docsType + " of the '" + featureName + "' feature.")));
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(artifactSource);
                jar.getArchiveClassifier().set(camelToKebabCase(featureName == null ? docsType : (featureName + "-" + docsType)));
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

    private static Configuration maybeCreateInvisibleConfig(
        ConfigurationContainer container,
        String name,
        String description,
        boolean canBeConsumed
    ) {
        Configuration configuration = container.maybeCreate(name);
        configuration.setVisible(false);
        configuration.setDescription(description);
        configuration.setCanBeResolved(false);
        configuration.setCanBeConsumed(canBeConsumed);
        return configuration;
    }

    @Nullable
    public static AdhocComponentWithVariants findJavaComponent(SoftwareComponentContainer components) {
        SoftwareComponent component = components.findByName("java");
        if (component instanceof AdhocComponentWithVariants) {
            return (AdhocComponentWithVariants) component;
        }
        return null;
    }

    public static Action<ConfigurationInternal> configureLibraryElementsAttributeForCompileClasspath(boolean javaClasspathPackaging, SourceSet sourceSet, TaskProvider<JavaCompile> compileTaskProvider, ObjectFactory objectFactory) {
        return conf -> {
            AttributeContainerInternal attributes = conf.getAttributes();
            if (!attributes.contains(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)) {
                String libraryElements;
                // If we are compiling a module, we require JARs of all dependencies as they may potentially include an Automatic-Module-Name
                if (javaClasspathPackaging || JavaModuleDetector.isModuleSource(compileTaskProvider.get().getModularity().getInferModulePath().get(), CompilationSourceDirs.inferSourceRoots((FileTreeInternal) sourceSet.getJava().getAsFileTree()))) {
                    libraryElements = LibraryElements.JAR;
                } else {
                    libraryElements = LibraryElements.CLASSES;
                }
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, libraryElements));
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
