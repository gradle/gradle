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

import com.google.common.collect.ImmutableList;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmByteCode;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.DependentSourceSetInternal;
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
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;

import java.io.File;
import java.util.*;

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
            languages.add(new Java());
        }
    }

    private static class Java implements LanguageTransform<JavaSourceSet, JvmByteCode> {
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
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "compile";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return PlatformJavaCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet) {
                    PlatformJavaCompile compile = (PlatformJavaCompile) task;
                    JavaSourceSet javaSourceSet = (JavaSourceSet) sourceSet;
                    JvmBinarySpec binary = (JvmBinarySpec) binarySpec;
                    GradleInternal gradle = (GradleInternal) task.getProject().getGradle();
                    ArtifactDependencyResolver dependencyResolver = gradle.getServices().get(ArtifactDependencyResolver.class);
                    ProjectInternal project = (ProjectInternal) task.getProject();
                    RepositoryHandler repositories = project.getRepositories();
                    GlobalDependencyResolutionRules globalDependencyResolutionRules = project.getServices().get(GlobalDependencyResolutionRules.class);

                    compile.setDescription(String.format("Compiles %s.", javaSourceSet));
                    compile.setDestinationDir(binary.getClassesDir());
                    compile.setPlatform(binary.getTargetPlatform());

                    compile.setSource(javaSourceSet.getSource());
                    compile.setClasspath(new DependencyResolvingClasspath(javaSourceSet, dependencyResolver, repositories, globalDependencyResolutionRules));
                    compile.setTargetCompatibility(binary.getTargetPlatform().getTargetCompatibility().toString());
                    compile.setSourceCompatibility(binary.getTargetPlatform().getTargetCompatibility().toString());

                    compile.setDependencyCacheDir(new File(compile.getProject().getBuildDir(), "jvm-dep-cache"));
                    compile.dependsOn(javaSourceSet);
                    binary.getTasks().getJar().dependsOn(compile);
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof JvmBinarySpec;
        }
    }

    private static class DependencyResolvingClasspath extends AbstractFileCollection {
        private final JavaSourceSet sourceSet;
        private final ArtifactDependencyResolver dependencyResolver;
        private final GlobalDependencyResolutionRules globalDependencyResolutionRules;
        private final RepositoryHandler repositories;

        private DependencyResolvingClasspath(
            JavaSourceSet sourceSet,
            ArtifactDependencyResolver dependencyResolver,
            RepositoryHandler repositories,
            GlobalDependencyResolutionRules globalDependencyResolutionRules) {
            this.sourceSet = sourceSet;
            this.dependencyResolver = dependencyResolver;
            this.repositories = repositories;
            this.globalDependencyResolutionRules = globalDependencyResolutionRules;
        }

        @Override
        public String getDisplayName() {
            return "Classpath for "+sourceSet.getDisplayName();
        }

        @Override
        public Set<File> getFiles() {
            Set<File> classpath = new LinkedHashSet<File>();
            classpath.addAll(sourceSet.getCompileClasspath().getFiles().getFiles());
            ResolverResults results = new ResolverResults();
            ImmutableList<ArtifactRepository> artifactRepositories = ImmutableList.copyOf(repositories.iterator());
            List<ResolutionAwareRepository> resolutionRepositories = new LinkedList<ResolutionAwareRepository>();
            for (ArtifactRepository artifactRepository : artifactRepositories) {
                if (artifactRepository instanceof ResolutionAwareRepository) {
                    resolutionRepositories.add((ResolutionAwareRepository)artifactRepository);
                }
            }
            DependentSourceSetInternal dss = (DependentSourceSetInternal) sourceSet;
            //dependencyResolver.resolveArtifacts(dss, resolutionRepositories, globalDependencyResolutionRules, results);
            return classpath;
        }
    }
}
