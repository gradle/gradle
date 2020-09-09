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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.instantiation.InstanceGenerator;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultJvmPluginServices implements JvmPluginServices {
    private final ConfigurationContainer configurations;
    private final ObjectFactory objectFactory;
    private final TaskContainer tasks;
    private final SoftwareComponentContainer components;
    private final InstanceGenerator instanceGenerator;

    private SourceSetContainer sourceSets;
    private ProjectInternal project; // would be great to avoid this but for lazy capabilities it's hard to avoid!

    @Inject
    public DefaultJvmPluginServices(ConfigurationContainer configurations,
                                    ObjectFactory objectFactory,
                                    TaskContainer tasks,
                                    SoftwareComponentContainer components,
                                    InstanceGenerator instanceGenerator) {
        this.configurations = configurations;
        this.objectFactory = objectFactory;
        this.tasks = tasks;
        this.components = components;
        this.instanceGenerator = instanceGenerator;
    }

    @Override
    public void inject(ProjectInternal project, SourceSetContainer sourceSets) {
        this.project = project;
        this.sourceSets = sourceSets;
    }

    @Override
    public void configureClassesDirectoryVariant(String configurationName, SourceSet sourceSet) {
        configurations.all(config -> {
            if (configurationName.equals(config.getName())) {
                registerClassesDirVariant(sourceSet, config);
            }
        });
    }

    @Override
    public <T> void configureAsCompileClasspath(HasConfigurableAttributes<T> configuration) {
        configureAttributes(configuration, details -> details.library().apiUsage().withExternalDependencies());
    }

    @Override
    public <T> void configureAsRuntimeClasspath(HasConfigurableAttributes<T> configuration) {
        configureAttributes(configuration, details -> details.library().runtimeUsage().asJar().withExternalDependencies());
    }

    @Override
    public <T> void configureAttributes(HasConfigurableAttributes<T> configurable, Action<? super JvmEcosystemAttributesDetails> configuration) {
        AttributeContainerInternal attributes = (AttributeContainerInternal) configurable.getAttributes();
        DefaultJvmEcosystemAttributesDetails details = instanceGenerator.newInstance(DefaultJvmEcosystemAttributesDetails.class, objectFactory, attributes);
        configuration.execute(details);
    }

    @Override
    public void useDefaultTargetPlatformInference(Configuration configuration, SourceSet sourceSet) {
        ((ConfigurationInternal) configuration).beforeLocking(
            configureDefaultTargetPlatform(configuration.isCanBeConsumed(), tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)));
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

    private void registerClassesDirVariant(final SourceSet sourceSet, Configuration configuration) {
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
                TaskDependency classesContributors = output.getClassesContributors();
                ImmutableList.Builder<PublishArtifact> artifacts = ImmutableList.builderWithExpectedSize(classesDirs.size());
                for (File classesDir : classesDirs) {
                    // this is an approximation: all "compiled" sources will use the same task dependency
                    artifacts.add(new JvmPluginsHelper.IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, classesContributors) {
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

    private Action<ConfigurationInternal> configureDefaultTargetPlatform(boolean alwaysEnabled, TaskProvider<JavaCompile> compileTaskProvider) {
        return conf -> {
            JavaPluginConvention javaConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
            if (alwaysEnabled || javaConvention == null || !javaConvention.getAutoTargetJvmDisabled()) {
                JavaCompile javaCompile = compileTaskProvider.get();
                int majorVersion;
                if (javaCompile.getOptions().getRelease().isPresent()) {
                    majorVersion = javaCompile.getOptions().getRelease().get();
                } else {
                    int releaseFlag = getReleaseOption(javaCompile.getOptions().getCompilerArgs());
                    if (releaseFlag != 0) {
                        majorVersion = releaseFlag;
                    } else {
                        majorVersion = Integer.parseInt(JavaVersion.toVersion(javaCompile.getTargetCompatibility()).getMajorVersion());
                    }
                }
                JavaEcosystemSupport.configureDefaultTargetPlatform(conf, majorVersion);
            }
        };
    }

    @Override
    public Configuration createOutgoingElements(String name, Action<? super OutgoingElementsBuilder> configuration) {
        DefaultElementsConfigurationBuilder builder = instanceGenerator.newInstance(DefaultElementsConfigurationBuilder.class,
            name,
            this,
            configurations,
            components);
        configuration.execute(builder);
        return builder.build();
    }

    @Override
    public Configuration createResolvableConfiguration(String name, Action<? super ResolvableConfigurationBuilder> action) {
        DefaultResolvableConfigurationBuilder builder = instanceGenerator.newInstance(DefaultResolvableConfigurationBuilder.class,
            name,
            this,
            configurations);
        action.execute(builder);
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
        boolean api;
        SourceSet sourceSet;
        List<Object> artifactProducers;
        List<Capability> capabilities;
        boolean classDirectory;
        private boolean published;

        @Inject
        public DefaultElementsConfigurationBuilder(String name, JvmPluginServices jvmEcosystemUtilities, ConfigurationContainer configurations, SoftwareComponentContainer components) {
            super(name, jvmEcosystemUtilities, configurations);
            this.components = components;
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
                jvmEcosystemUtilities.useDefaultTargetPlatformInference(cnf, sourceSet);
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
                jvmEcosystemUtilities.configureClassesDirectoryVariant(name, sourceSet);
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
            ImmutableCapability capability = new ImmutableCapability(group, name, version);
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

    public static class DefaultResolvableConfigurationBuilder extends AbstractConfigurationBuilder<DefaultResolvableConfigurationBuilder> implements ResolvableConfigurationBuilder {
        private Boolean libraryApi;
        private Boolean libraryRuntime;
        private Map<String, String> buckets;

        @Inject
        public DefaultResolvableConfigurationBuilder(String name,
                                                     JvmPluginServices jvmEcosystemUtilities,
                                                     ConfigurationContainer configurations) {
            super(name, jvmEcosystemUtilities, configurations);
        }

        @Override
        public ResolvableConfigurationBuilder usingDependencyBucket(String name) {
            return usingDependencyBucket(name, null);
        }

        @Override
        public ResolvableConfigurationBuilder usingDependencyBucket(String name, String description) {
            if (buckets == null) {
                buckets = Maps.newLinkedHashMap();
            }
            buckets.put(name, description);
            return this;
        }

        @Override
        Configuration build() {
            if (buckets != null) {
                for (Map.Entry<String, String> entry : buckets.entrySet()) {
                    String bucketName = entry.getKey();
                    String description = entry.getValue();
                    Configuration bucket = configurations.maybeCreate(bucketName);
                    if (description != null) {
                        bucket.setDescription(description);
                    } else if (this.description != null) {
                        bucket.setDescription("Dependencies for " + this.description);
                    }
                    bucket.setVisible(false);
                    bucket.setCanBeConsumed(false);
                    bucket.setCanBeResolved(false);
                    extendsFrom(bucket);
                }
            }
            Configuration resolvable = configurations.maybeCreate(name);
            if (description != null) {
                resolvable.setDescription(description);
            }
            resolvable.setVisible(false);
            resolvable.setCanBeConsumed(false);
            resolvable.setCanBeResolved(true);

            Configuration[] extendsFrom = buildExtendsFrom();
            if (extendsFrom != null) {
                resolvable.extendsFrom(extendsFrom);
            }
            jvmEcosystemUtilities.configureAttributes(resolvable, details -> {
                    if (libraryApi != null && libraryApi) {
                        details.library()
                            .asJar()
                            .withExternalDependencies()
                            .apiUsage();
                    } else if (libraryRuntime != null && libraryRuntime) {
                        details.library()
                            .asJar()
                            .withExternalDependencies()
                            .runtimeUsage();
                    }
                    if (libraryRuntime == null && libraryApi == null && attributesRefiner == null) {
                        throw new IllegalStateException("You didn't tell what kind of component to look for. You need to configure at least one attribute");
                    }
                    if (attributesRefiner != null) {
                        attributesRefiner.execute(details);
                    }
                }
            );
            return resolvable;
        }

        @Override
        public ResolvableConfigurationBuilder requiresJavaLibrariesRuntime() {
            this.libraryApi = null;
            this.libraryRuntime = true;
            return this;
        }

        @Override
        public ResolvableConfigurationBuilder requiresJavaLibrariesAPI() {
            this.libraryRuntime = null;
            this.libraryApi = true;
            return this;
        }

        @Override
        public ResolvableConfigurationBuilder requiresAttributes(Action<? super JvmEcosystemAttributesDetails> refiner) {
            return attributes(refiner);
        }
    }
}
