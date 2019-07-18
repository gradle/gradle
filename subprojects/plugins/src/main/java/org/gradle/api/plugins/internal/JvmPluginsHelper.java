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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

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
        classpath.from(new Callable<Object>() {
            @Override
            public Object call() {
                return sourceSet.getCompileClasspath().plus(target.files(sourceSet.getJava().getOutputDir()));
            }
        });

        compile.getConventionMapping().map("classpath", new Callable<Object>() {
            @Override
            public Object call() {
                return classpath;
            }
        });
        compile.setDestinationDir(target.provider(new Callable<File>() {
            @Override
            public File call() {
                return sourceDirectorySet.getOutputDir();
            }
        }));
    }

    public static void configureAnnotationProcessorPath(final SourceSet sourceSet, SourceDirectorySet sourceDirectorySet, CompileOptions options, final Project target) {
        final ConventionMapping conventionMapping = new DslObject(options).getConventionMapping();
        conventionMapping.map("annotationProcessorPath", new Callable<Object>() {
            @Override
            public Object call() {
                return sourceSet.getAnnotationProcessorPath();
            }
        });
        final String annotationProcessorGeneratedSourcesChildPath = "generated/sources/annotationProcessor/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        conventionMapping.map("annotationProcessorGeneratedSourcesDirectory", new Callable<Object>() {
            @Override
            public Object call() {
                return new File(target.getBuildDir(), annotationProcessorGeneratedSourcesChildPath);
            }
        });
    }

    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, Provider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        sourceDirectorySet.setOutputDir(target.provider(new Callable<File>() {
            @Override
            public File call() {
                return new File(target.getBuildDir(), sourceSetChildPath);
            }
        }));

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.addClassesDir(new Callable<File>() {
            @Override
            public File call() {
                return sourceDirectorySet.getOutputDir();
            }
        });
        sourceSetOutput.registerCompileTask(compileTask);
        sourceSetOutput.getGeneratedSourcesDirs().from(options.map(new Transformer<Object, CompileOptions>() {
            @Override
            public Object transform(CompileOptions compileOptions) {
                return compileOptions.getAnnotationProcessorGeneratedSourcesDirectory();
            }
        })).builtBy(compileTask);

    }

    public static void configureClassesDirectoryVariant(SourceSet sourceSet, Project target, String targetConfigName, final String usage) {
        target.getConfigurations().all(new Action<Configuration>() {
            @Override
            public void execute(Configuration config) {
                if (targetConfigName.equals(config.getName())) {
                    registerClassesDirVariant(sourceSet, target.getObjects(), config);
                }
            }
        });
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
    }
}
