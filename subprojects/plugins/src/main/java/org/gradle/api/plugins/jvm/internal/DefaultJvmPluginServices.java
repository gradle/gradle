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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
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
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.instantiation.InstanceGenerator;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultJvmPluginServices implements JvmPluginServices {
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;
    private final TaskContainer tasks;
    private final SoftwareComponentContainer components;
    private final InstanceGenerator instanceGenerator;
    private final Map<ConfigurationInternal, Set<TaskProvider<?>>> configurationToCompileTasks; // ? is really AbstractCompile & HasCompileOptions

    private SourceSetContainer sourceSets;
    private ProjectInternal project; // would be great to avoid this but for lazy capabilities it's hard to avoid!

    @Inject
    public DefaultJvmPluginServices(ConfigurationContainer configurations,
                                    ObjectFactory objectFactory,
                                    ProviderFactory providerFactory,
                                    TaskContainer tasks,
                                    SoftwareComponentContainer components,
                                    InstanceGenerator instanceGenerator) {
        this.configurations = configurations;
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
        this.tasks = tasks;
        this.components = components;
        this.instanceGenerator = instanceGenerator;
        configurationToCompileTasks = new HashMap<>(5);
    }

    @Override
    public void inject(ProjectInternal project, SourceSetContainer sourceSets) {
        this.project = project;
        this.sourceSets = sourceSets;
    }

    @Override
    public <T> void configureAsCompileClasspath(HasConfigurableAttributes<T> configuration) {
        configureAttributes(configuration, details -> details.library().apiUsage().withExternalDependencies().preferStandardJVM());
    }

    @Override
    public <T> void configureAsRuntimeClasspath(HasConfigurableAttributes<T> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().runtimeUsage().asJar().withExternalDependencies().preferStandardJVM()
        );
    }

    @Override
    public <T> void configureAttributes(HasConfigurableAttributes<T> configurable, Action<? super JvmEcosystemAttributesDetails> configuration) {
        AttributeContainerInternal attributes = (AttributeContainerInternal) configurable.getAttributes();
        DefaultJvmEcosystemAttributesDetails details = instanceGenerator.newInstance(DefaultJvmEcosystemAttributesDetails.class, objectFactory, attributes);
        configuration.execute(details);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <COMPILE extends AbstractCompile & HasCompileOptions> void useDefaultTargetPlatformInference(Configuration configuration, TaskProvider<COMPILE> compileTask) {
        ConfigurationInternal configurationInternal = (ConfigurationInternal) configuration;
        Set<TaskProvider<?>> compileTasks = configurationToCompileTasks.computeIfAbsent(configurationInternal, key -> {
            HashSet<TaskProvider<?>> taskProviders = new HashSet<>();
            configurationInternal.beforeLocking(
                configureDefaultTargetPlatform(configuration.isCanBeConsumed(), (Set) taskProviders));
            return taskProviders;
        });
        compileTasks.add(compileTask);
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

    private <COMPILE extends AbstractCompile & HasCompileOptions> Action<ConfigurationInternal> configureDefaultTargetPlatform(boolean alwaysEnabled, Set<TaskProvider<COMPILE>> compileTasks) {
        return conf -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
            if (alwaysEnabled || javaPluginExtension == null || !javaPluginExtension.getAutoTargetJvmDisabled()) {
                int majorVersion = 0;
                for (TaskProvider<COMPILE> compileTaskProvider : compileTasks) {
                    COMPILE compileTask = compileTaskProvider.get();
                    if (compileTask.getOptions().getRelease().isPresent()) {
                        majorVersion = Math.max(majorVersion, compileTask.getOptions().getRelease().get());
                    } else {
                        int releaseFlag = getReleaseOption(compileTask.getOptions().getCompilerArgs());
                        if (releaseFlag != 0) {
                            majorVersion = Math.max(majorVersion, releaseFlag);
                        } else {
                            majorVersion = Math.max(majorVersion, Integer.parseInt(JavaVersion.toVersion(compileTask.getTargetCompatibility()).getMajorVersion()));
                        }
                    }
                }
                if (majorVersion != 0) {
                    JavaEcosystemSupport.configureDefaultTargetPlatform(conf, majorVersion);
                }
            }
        };
    }

    @Override
    public Configuration createOutgoingElements(String name, Action<? super OutgoingElementsBuilder> configuration) {
        DefaultElementsConfigurationBuilder builder = instanceGenerator.newInstance(DefaultElementsConfigurationBuilder.class,
            name,
            this,
            configurations,
            components,
            tasks);
        configuration.execute(builder);
        return builder.build();
    }

    @Override
    public void createJvmVariant(String name, Action<? super JvmVariantBuilder> configuration) {
        DefaultJvmVariantBuilder builder = instanceGenerator.newInstance(DefaultJvmVariantBuilder.class,
            name,
            new ProjectDerivedCapability(project, name),
            this,
            sourceSets,
            configurations,
            tasks,
            components,
            project);
        configuration.execute(builder);
        builder.build();
    }

    private static int getReleaseOption(List<String> compilerArgs) {
        int flagIndex = compilerArgs.indexOf("--release");
        if (flagIndex != -1 && flagIndex + 1 < compilerArgs.size()) {
            return Integer.parseInt(String.valueOf(compilerArgs.get(flagIndex + 1)));
        }
        return 0;
    }

    public static class DefaultElementsConfigurationBuilder extends AbstractConfigurationBuilder<DefaultElementsConfigurationBuilder> implements OutgoingElementsBuilder {
        final SoftwareComponentContainer components;
        private final TaskContainer tasks;
        boolean api;
        SourceSet sourceSet;
        List<Object> artifactProducers;
        List<Capability> capabilities;
        boolean classDirectory;
        private boolean published;

        @Inject
        public DefaultElementsConfigurationBuilder(String name, JvmPluginServices jvmEcosystemUtilities, ConfigurationContainerInternal configurations, SoftwareComponentContainer components, TaskContainer tasks) {
            super(name, jvmEcosystemUtilities, (RoleBasedConfigurationContainerInternal) configurations);
            this.components = components;
            this.tasks = tasks;
        }

        @Override
        Configuration build() {
            Configuration cnf = configurations.maybeCreate(name);
            if (description != null) {
                cnf.setDescription(description);
            }
            cnf.setVisible(false);
            cnf.setCanBeConsumed(true);
            cnf.setCanBeResolved(false);
            Configuration[] extendsFrom = buildExtendsFrom();
            if (extendsFrom != null) {
                cnf.extendsFrom(extendsFrom);
            }
            jvmEcosystemUtilities.configureAttributes(cnf, details -> {
                    details.library()
                        .withExternalDependencies();
                    if (api) {
                        details.apiUsage();
                    } else {
                        details.runtimeUsage();
                    }
                    if (attributesRefiner != null) {
                        attributesRefiner.execute(details);
                    }
                    if (Category.LIBRARY.equals(cnf.getAttributes().getAttribute(Category.CATEGORY_ATTRIBUTE).getName())) {
                        if (!cnf.getAttributes().contains(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)) {
                            details.asJar();
                        }
                    }
                }
            );
            if (sourceSet != null) {
                jvmEcosystemUtilities.useDefaultTargetPlatformInference(cnf, tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class));
            }
            ConfigurationPublications outgoing = cnf.getOutgoing();
            if (artifactProducers != null) {
                for (Object provider : artifactProducers) {
                    outgoing.artifact(provider);
                }
            }
            if (capabilities != null) {
                for (Capability capability : capabilities) {
                    outgoing.capability(capability);
                }
            }
            if (classDirectory) {
                if (!api) {
                    throw new IllegalStateException("Cannot add a class directory variant for a runtime outgoing variant");
                }
                if (sourceSet == null) {
                    throw new IllegalStateException("Cannot add a class directory variant without specifying the source set");
                }
                jvmEcosystemUtilities.configureClassesDirectoryVariant(cnf, sourceSet);
            }
            if (published) {
                AdhocComponentWithVariants component = findJavaComponent();
                if (component != null) {
                    component.addVariantsFromConfiguration(cnf, ConfigurationVariantDetails::mapToOptional);
                }
            }
            return cnf;
        }

        @Override
        public OutgoingElementsBuilder providesApi() {
            this.api = true;
            return this;
        }

        @Override
        public OutgoingElementsBuilder providesRuntime() {
            this.api = false;
            return this;
        }

        @Override
        public OutgoingElementsBuilder fromSourceSet(SourceSet sourceSet) {
            this.sourceSet = sourceSet;
            return this;
        }

        @Override
        public OutgoingElementsBuilder artifact(Object producer) {
            if (artifactProducers == null) {
                artifactProducers = Lists.newArrayList();
            }
            artifactProducers.add(producer);
            return this;
        }

        @Override
        public OutgoingElementsBuilder providesAttributes(Action<? super JvmEcosystemAttributesDetails> refiner) {
            return attributes(refiner);
        }

        @Override
        public OutgoingElementsBuilder withCapabilities(List<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        @Override
        public OutgoingElementsBuilder capability(String group, String name, String version) {
            if (capabilities == null) {
                capabilities = Lists.newArrayList();
            }
            DefaultImmutableCapability capability = new DefaultImmutableCapability(group, name, version);
            if (capability.getVersion() == null) {
                throw new InvalidUserDataException("Capabilities declared on outgoing variants must have a version");
            }
            capabilities.add(capability);
            return this;
        }

        @Override
        public OutgoingElementsBuilder withClassDirectoryVariant() {
            this.classDirectory = true;
            return this;
        }

        @Override
        public OutgoingElementsBuilder published() {
            this.published = true;
            return this;
        }

        @Nullable
        public AdhocComponentWithVariants findJavaComponent() {
            SoftwareComponent component = components.findByName("java");
            if (component instanceof AdhocComponentWithVariants) {
                return (AdhocComponentWithVariants) component;
            }
            return null;
        }
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
