/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceGenerator;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultJvmPluginServices implements JvmPluginServices {
    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;
    private final InstanceGenerator instanceGenerator;
    private final Map<ConfigurationInternal, Set<TaskProvider<?>>> configurationToCompileTasks; // ? is really AbstractCompile & HasCompileOptions

    private ProjectInternal project; // would be great to avoid this but for lazy capabilities it's hard to avoid!

    @Inject
    public DefaultJvmPluginServices(ObjectFactory objectFactory,
                                    ProviderFactory providerFactory,
                                    InstanceGenerator instanceGenerator) {
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
        this.instanceGenerator = instanceGenerator;
        configurationToCompileTasks = new HashMap<>(5);
    }

    @Override
    public void inject(ProjectInternal project) {
        this.project = project;
    }

    @Override
    public void configureAsCompileClasspath(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().apiUsage().withExternalDependencies().preferStandardJVM()
        );
    }

    @Override
    public void configureAsRuntimeClasspath(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().runtimeUsage().asJar().withExternalDependencies().preferStandardJVM()
        );
    }

    @Override
    public void configureAsApiElements(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().apiUsage().asJar().withExternalDependencies()
        );
    }

    @Override
    public void configureAsRuntimeElements(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().runtimeUsage().asJar().withExternalDependencies()
        );
    }

    @Override
    public <T> void configureAttributes(HasConfigurableAttributes<T> configurable, Action<? super JvmEcosystemAttributesDetails> configuration) {
        AttributeContainerInternal attributes = (AttributeContainerInternal) configurable.getAttributes();
        DefaultJvmEcosystemAttributesDetails details = instanceGenerator.newInstance(DefaultJvmEcosystemAttributesDetails.class, objectFactory, attributes);
        configuration.execute(details);
    }

    @Override
    public void replaceArtifacts(Configuration outgoingConfiguration, Object... providers) {
        clearArtifacts(outgoingConfiguration);
        ConfigurationPublications outgoing = outgoingConfiguration.getOutgoing();
        for (Object provider : providers) {
            outgoing.artifact(provider);
        }
    }

    @Override
    public void registerJvmLanguageSourceDirectory(SourceSet sourceSet, String name, Action<? super JvmLanguageSourceDirectoryBuilder> configuration) {
        DefaultJvmLanguageSourceDirectoryBuilder builder = instanceGenerator.newInstance(DefaultJvmLanguageSourceDirectoryBuilder.class,
            name,
            project,
            sourceSet);
        configuration.execute(builder);
        builder.build();
    }

    @Override
    public void registerJvmLanguageGeneratedSourceDirectory(SourceSet sourceSet, Action<? super JvmLanguageGeneratedSourceDirectoryBuilder> configuration) {
        DefaultJvmLanguageGeneratedSourceDirectoryBuilder builder = instanceGenerator.newInstance(DefaultJvmLanguageGeneratedSourceDirectoryBuilder.class,
            project,
            sourceSet);
        configuration.execute(builder);
        builder.build();
    }

    @Override
    public Provider<Configuration> registerDependencyBucket(String name, String description) {
        return project.getConfigurations().register(name, cnf -> {
            cnf.setCanBeResolved(false);
            cnf.setCanBeConsumed(false);
            cnf.setDescription(description);
        });
    }

    private void clearArtifacts(Configuration outgoingConfiguration) {
        outgoingConfiguration.getOutgoing().getArtifacts().clear();
        for (Configuration configuration : outgoingConfiguration.getExtendsFrom()) {
            clearArtifacts(configuration);
        }
    }

    @Override
    public ConfigurationVariant configureResourcesDirectoryVariant(Configuration configuration, SourceSet sourceSet) {
        ConfigurationPublications publications = configuration.getOutgoing();
        ConfigurationVariantInternal variant = (ConfigurationVariantInternal) publications.getVariants().maybeCreate("resources");
        variant.setDescription("Directories containing assembled resource files for " + sourceSet.getName() + ".");
        variant.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.RESOURCES));
        DefaultSourceSetOutput output = Cast.uncheckedCast(sourceSet.getOutput());
        DefaultSourceSetOutput.DirectoryContribution resourcesContribution = output.getResourcesContribution();
        if (resourcesContribution != null) {
            variant.artifact(new LazyJavaDirectoryArtifact(project.getTaskDependencyFactory(), ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY, resourcesContribution.getTask(), resourcesContribution.getDirectory()));
        }
        return variant;
    }

    @Override
    public ConfigurationVariant configureClassesDirectoryVariant(Configuration configuration, SourceSet sourceSet) {
        ConfigurationPublications publications = configuration.getOutgoing();
        ConfigurationVariantInternal variant = (ConfigurationVariantInternal) publications.getVariants().maybeCreate("classes");
        variant.setDescription("Directories containing compiled class files for " + sourceSet.getName() + ".");
        variant.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.CLASSES));
        variant.artifactsProvider(() ->  {
            FileCollection classesDirs = sourceSet.getOutput().getClassesDirs();
            return classesDirs.getFiles().stream()
                .map(file -> new LazyJavaDirectoryArtifact(project.getTaskDependencyFactory(), ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, classesDirs, providerFactory.provider(() -> file)))
                .collect(Collectors.toList());
        });
        return variant;
    }

    @Override
    public <COMPILE extends AbstractCompile & HasCompileOptions> void useDefaultTargetPlatformInference(Configuration configuration, TaskProvider<COMPILE> compileTask) {
        ConfigurationInternal configurationInternal = (ConfigurationInternal) configuration;
        Set<TaskProvider<COMPILE>> compileTasks = Cast.uncheckedCast(configurationToCompileTasks.computeIfAbsent(configurationInternal, key -> new HashSet<>()));
        compileTasks.add(compileTask);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        configurationInternal.getAttributes().attributeProvider(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            providerFactory.provider(() -> getDefaultTargetPlatform(configuration, java, compileTasks))
        );
    }

    private static <COMPILE extends AbstractCompile & HasCompileOptions> int getDefaultTargetPlatform(Configuration configuration, JavaPluginExtension java, Set<TaskProvider<COMPILE>> compileTasks) {
        assert !compileTasks.isEmpty();

        if (!configuration.isCanBeConsumed() && java.getAutoTargetJvmDisabled()) {
            return Integer.MAX_VALUE;
        }

        return compileTasks.stream().map(provider -> {
            COMPILE compileTask = provider.get();
            if (compileTask.getOptions().getRelease().isPresent()) {
                return compileTask.getOptions().getRelease().get();
            }

            List<String> compilerArgs = compileTask.getOptions().getCompilerArgs();
            int flagIndex = compilerArgs.indexOf("--release");

            if (flagIndex != -1 && flagIndex + 1 < compilerArgs.size()) {
                return Integer.parseInt(String.valueOf(compilerArgs.get(flagIndex + 1)));
            } else {
                return Integer.parseInt(JavaVersion.toVersion(compileTask.getTargetCompatibility()).getMajorVersion());
            }
        }).max(Comparator.naturalOrder()).get();
    }

    /**
     * A custom artifact type which allows the getFile call to be done lazily only when the
     * artifact is actually needed.
     */
    private static class LazyJavaDirectoryArtifact extends AbstractPublishArtifact {
        private final String type;
        private final Provider<File> fileProvider;

        public LazyJavaDirectoryArtifact(TaskDependencyFactory taskDependencyFactory, String type, Object dependency, Provider<File> fileProvider) {
            super(taskDependencyFactory, dependency);
            this.type = type;
            this.fileProvider = fileProvider;
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

        @Override
        public File getFile() {
            return fileProvider.get();
        }
    }
}
