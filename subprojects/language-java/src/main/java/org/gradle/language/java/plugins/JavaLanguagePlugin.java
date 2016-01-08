/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java.plugins;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.internal.Transformers;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.internal.*;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.internal.DefaultJavaLanguageSourceSet;
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Iterables.concat;
import static org.gradle.util.CollectionUtils.first;

/**
 * Plugin for compiling Java code. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin} and {@link org.gradle.language.jvm.plugins.JvmResourcesPlugin}. Registers "java"
 * language support with the {@link JavaSourceSet}.
 */
public class JavaLanguagePlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
        project.getPluginManager().apply(JvmResourcesPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @LanguageType
        void registerLanguage(LanguageTypeBuilder<JavaSourceSet> builder) {
            builder.setLanguageName("java");
            builder.defaultImplementation(DefaultJavaLanguageSourceSet.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            ModelSchemaStore schemaStore = serviceRegistry.get(ModelSchemaStore.class);
            languages.add(new Java(schemaStore));
        }
    }

    /**
     * The language transform implementation for Java sources.
     */
    public static class Java implements LanguageTransform<JavaSourceSet, JvmByteCode> {
        private final JavaSourceTransformTaskConfig config;
        private final List<PlatformJavaCompileConfig> platformJavaConfigurers = Lists.newLinkedList();

        public Java(ModelSchemaStore schemaStore) {
            this.config = new JavaSourceTransformTaskConfig(schemaStore, platformJavaConfigurers);
        }

        public void registerPlatformJavaCompileConfig(PlatformJavaCompileConfig configurer) {
            platformJavaConfigurers.add(configurer);
        }

        public Class<JavaSourceSet> getSourceSetType() {
            return JavaSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<JvmByteCode> getOutputType() {
            return JvmByteCode.class;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return config;
        }

        private static File conventionalCompilationOutputDirFor(JvmAssembly assembly) {
            return first(assembly.getClassDirectories());
        }

        private static DependencyResolvingClasspath classpathFor(BinarySpec binary, JavaSourceSet javaSourceSet, ServiceRegistry serviceRegistry, ModelSchemaStore schemaStore) {
            Iterable<DependencySpec> dependencies = compileDependencies(binary, javaSourceSet);

            ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
            RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
            List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
            return new DependencyResolvingClasspath((BinarySpecInternal) binary, javaSourceSet, dependencies, dependencyResolver, schemaStore, resolutionAwareRepositories);
        }

        private static Iterable<DependencySpec> compileDependencies(BinarySpec binary, DependentSourceSet sourceSet) {
            return concat(
                sourceSet.getDependencies().getDependencies(),
                componentDependenciesOf(binary),
                apiDependenciesOf(binary));
        }

        private static Iterable<DependencySpec> componentDependenciesOf(BinarySpec binary) {
            return binary instanceof WithDependencies
                ? ((WithDependencies) binary).getDependencies()
                : NO_DEPENDENCIES;
        }

        private static Iterable<DependencySpec> apiDependenciesOf(BinarySpec binary) {
            return binary instanceof JarBinarySpecInternal
                ? ((JarBinarySpecInternal) binary).getApiDependencies()
                : NO_DEPENDENCIES;
        }

        private static final Iterable<DependencySpec> NO_DEPENDENCIES = ImmutableSet.of();

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof WithJvmAssembly;
        }

        /**
         * Interface for additional configuration to be done by plugins after the Java compile
         * task has been configured.
         */
        public interface PlatformJavaCompileConfig {
            /**
             * Configures the generated {@link PlatformJavaCompile} tasks.
             * @param spec the binary for which this compile task has been created
             * @param sourceSet the source set for which this compile task has been created
             * @param javaCompile the generated compile task
             */
            void configureJavaCompile(BinarySpec spec, JavaSourceSet sourceSet, PlatformJavaCompile javaCompile);
        }

        private static class JavaSourceTransformTaskConfig implements SourceTransformTaskConfig {

            private final ModelSchemaStore schemaStore;
            private final List<PlatformJavaCompileConfig> platformJavaConfigurers;

            private JavaSourceTransformTaskConfig(ModelSchemaStore schemaStore, List<PlatformJavaCompileConfig> platformJavaConfigurers) {
                this.schemaStore = schemaStore;
                this.platformJavaConfigurers = platformJavaConfigurers;
            }

            public String getTaskPrefix() {
                return "compile";
            }

            public Class<? extends DefaultTask> getTaskType() {
                return PlatformJavaCompile.class;
            }

            public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                PlatformJavaCompile compile = (PlatformJavaCompile) task;
                JavaSourceSet javaSourceSet = (JavaSourceSet) sourceSet;
                JvmAssembly assembly = ((WithJvmAssembly) binary).getAssembly();
                assembly.builtBy(compile);

                compile.setDescription(String.format("Compiles %s.", javaSourceSet));
                compile.setDestinationDir(conventionalCompilationOutputDirFor(assembly));
                compile.setDependencyCacheDir(new File(compile.getProject().getBuildDir(), "jvm-dep-cache"));
                compile.dependsOn(javaSourceSet);
                compile.setSource(javaSourceSet.getSource());

                JavaPlatform targetPlatform = assembly.getTargetPlatform();
                String targetCompatibility = targetPlatform.getTargetCompatibility().toString();
                compile.setPlatform(targetPlatform);
                compile.setToolChain(assembly.getToolChain());
                compile.setTargetCompatibility(targetCompatibility);
                compile.setSourceCompatibility(targetCompatibility);

                DependencyResolvingClasspath classpath = classpathFor(binary, javaSourceSet, serviceRegistry, schemaStore);
                compile.setClasspath(classpath);

                for (PlatformJavaCompileConfig configurer : platformJavaConfigurers) {
                    configurer.configureJavaCompile(binary, javaSourceSet, compile);
                }
            }
        }
    }
}
