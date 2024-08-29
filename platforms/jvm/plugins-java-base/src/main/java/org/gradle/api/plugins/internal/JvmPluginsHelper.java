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

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.internal.Cast;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.gradle.util.internal.TextUtil.camelToKebabCase;

/**
 * Helpers for Jvm plugins. They are in a separate class so that they don't leak
 * into the public API.
 */
public class JvmPluginsHelper {

    /**
     * For compatibility with <a href="https://plugins.gradle.org/plugin/io.freefair.aspectj">AspectJ Plugin</a>
     */
    @Deprecated
    public static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, CompileOptions options, final Project target) {
        compile.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
        compile.getSource().setFrom(sourceSet.getJava());

        compileAgainstJavaOutputs(compile, sourceSet, target.getObjects());
        configureAnnotationProcessorPath(sourceSet, sourceDirectorySet, options, target);
    }

    /**
     * Configures {@code compileTask} to compile against {@code sourceSet}'s compile classpath
     * in addition to the outputs of the java compilation, as specified by {@link SourceSet#getJava()}
     *
     * @param compileTask The task to configure.
     * @param sourceSet The source set whose output contains the java classes to compile against.
     * @param objectFactory An {@link ObjectFactory}.
     */
    public static void compileAgainstJavaOutputs(AbstractCompile compileTask, final SourceSet sourceSet, final ObjectFactory objectFactory) {
        ConfigurableFileCollection classpath = objectFactory.fileCollection();
        classpath.from((Callable<Object>) () -> sourceSet.getCompileClasspath().plus(objectFactory.fileCollection().from(sourceSet.getJava().getClassesDirectory())));
        compileTask.getConventionMapping().map("classpath", () -> classpath);
    }

    public static void configureAnnotationProcessorPath(final SourceSet sourceSet, SourceDirectorySet sourceDirectorySet, CompileOptions options, final Project target) {
        final ConventionMapping conventionMapping = new DslObject(options).getConventionMapping();
        conventionMapping.map("annotationProcessorPath", sourceSet::getAnnotationProcessorPath);
        String annotationProcessorGeneratedSourcesChildPath = "generated/sources/annotationProcessor/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        options.getGeneratedSourceOutputDirectory().convention(target.getLayout().getBuildDirectory().dir(annotationProcessorGeneratedSourcesChildPath));
    }

    /**
     * For compatibility with <a href="https://plugins.gradle.org/plugin/io.freefair.aspectj">AspectJ Plugin</a>
     */
    @Deprecated
    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, Provider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        TaskProvider<? extends AbstractCompile> taskProvider = Cast.uncheckedCast(compileTask);
        configureOutputDirectoryForSourceSet(sourceSet, sourceDirectorySet, target, taskProvider, options);
    }

    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, TaskProvider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        sourceDirectorySet.getDestinationDirectory().convention(target.getLayout().getBuildDirectory().dir(sourceSetChildPath));

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.getClassesDirs().from(sourceDirectorySet.getDestinationDirectory()).builtBy(compileTask);
        sourceSetOutput.getGeneratedSourcesDirs().from(options.flatMap(CompileOptions::getGeneratedSourceOutputDirectory));
        sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory);
    }

    public static void configureJavaDocTask(String displayName, SourceSet sourceSet, TaskContainer tasks, @Nullable JavaPluginExtension javaPluginExtension) {
        String javadocTaskName = sourceSet.getJavadocTaskName();
        if (!tasks.getNames().contains(javadocTaskName)) {
            tasks.register(javadocTaskName, Javadoc.class, javadoc -> {
                javadoc.setDescription("Generates Javadoc API documentation for the " + displayName + ".");
                javadoc.setGroup(JvmConstants.DOCUMENTATION_GROUP);
                javadoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));
                javadoc.getSource().setFrom(sourceSet.getAllJava());
                if (javaPluginExtension != null) {
                    javadoc.getConventionMapping().map("destinationDir", () -> javaPluginExtension.getDocsDir().dir(javadocTaskName).get().getAsFile());
                    javadoc.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
                }
            });
        }
    }

    public static Configuration createDocumentationVariantWithArtifact(
        String variantName,
        @Nullable String featureName,
        String docsType,
        Set<Capability> capabilities,
        String jarTaskName,
        Object artifactSource,
        ProjectInternal project
    ) {
        Configuration variant = project.getConfigurations().maybeCreateMigratingUnlocked(variantName, ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE);
        variant.setVisible(false);
        variant.setDescription(docsType + " elements for " + (featureName == null ? "main" : featureName) + ".");

        ObjectFactory objectFactory = project.getObjects();
        AttributeContainer attributes = variant.getAttributes();
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, docsType));
        capabilities.forEach(variant.getOutgoing()::capability);

        TaskContainer tasks = project.getTasks();

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

        TaskProvider<Jar> jar = tasks.named(jarTaskName, Jar.class);
        variant.getOutgoing().artifact(new LazyPublishArtifact(jar, project.getFileResolver(), project.getTaskDependencyFactory()));

        return variant;
    }

    /**
     * Configures the compile task to use the proper source and target compatibility conventions.
     *
     * @param compile The compile task to configure
     * @param javaExtension The java extension containing the raw source and target compatibility values
     * @param compatibilityComputer A function to compute the compatibility version to use as the convention
     *      given the raw version values and the current version property values set on the extension
     */
    public static void configureCompileDefaults(AbstractCompile compile, DefaultJavaPluginExtension javaExtension, BiFunction<JavaVersion, Supplier<JavaVersion>, JavaVersion> compatibilityComputer) {
        ConventionMapping conventionMapping = compile.getConventionMapping();
        conventionMapping.map("sourceCompatibility", () -> computeSourceCompatibilityConvention(javaExtension, compatibilityComputer).toString());
        conventionMapping.map("targetCompatibility", () -> computeTargetCompatibilityConvention(javaExtension, compile, compatibilityComputer).toString());
    }

    private static JavaVersion computeSourceCompatibilityConvention(DefaultJavaPluginExtension javaExtension, BiFunction<JavaVersion, Supplier<JavaVersion>, JavaVersion> compatibilityComputer) {
        return compatibilityComputer.apply(javaExtension.getRawSourceCompatibility(), javaExtension::getSourceCompatibility);
    }

    private static JavaVersion computeTargetCompatibilityConvention(DefaultJavaPluginExtension javaExtension, AbstractCompile compile, BiFunction<JavaVersion, Supplier<JavaVersion>, JavaVersion> compatibilityComputer) {
        JavaVersion rawTargetCompatibility = javaExtension.getRawTargetCompatibility();
        if (rawTargetCompatibility == null) {
            rawTargetCompatibility = JavaVersion.toVersion(compile.getSourceCompatibility());
        }
        return compatibilityComputer.apply(rawTargetCompatibility, javaExtension::getTargetCompatibility);
    }
}
