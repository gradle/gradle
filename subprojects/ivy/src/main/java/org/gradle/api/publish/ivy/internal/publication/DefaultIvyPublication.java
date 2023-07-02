/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.IvyPublishingAwareVariant;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.VersionMappingStrategy;
import org.gradle.api.publish.internal.CompositePublicationArtifactSet;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.InvalidIvyPublicationException;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyConfigurationContainer;
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec;
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet;
import org.gradle.api.publish.ivy.internal.artifact.DerivedIvyArtifact;
import org.gradle.api.publish.ivy.internal.artifact.SingleOutputTaskIvyArtifact;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependencySet;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyExcludeRule;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyProjectDependency;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publisher.DefaultReadOnlyIvyPublicationIdentity;
import org.gradle.api.publish.ivy.internal.publisher.IvyArtifactInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.publish.ivy.internal.publisher.MutableIvyPublicationidentity;
import org.gradle.api.publish.ivy.internal.publisher.NormalizedIvyArtifact;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class DefaultIvyPublication implements IvyPublicationInternal {

    private final static Logger LOG = Logging.getLogger(DefaultIvyPublication.class);

    private static final String API_VARIANT = "api";
    private static final String API_ELEMENTS_VARIANT = "apiElements";
    private static final String RUNTIME_VARIANT = "runtime";
    private static final String RUNTIME_ELEMENTS_VARIANT = "runtimeElements";

    @VisibleForTesting
    public static final String UNSUPPORTED_FEATURE = " contains dependencies that cannot be represented in a published ivy descriptor.";
    @VisibleForTesting
    public static final String PUBLICATION_WARNING_FOOTER = "These issues indicate information that is lost in the published 'ivy.xml' metadata file, which may be an issue if the published library is consumed by an old Gradle version or Apache Ivy.\nThe 'module' metadata file, which is used by Gradle 6+ is not affected.";

    private final String name;
    private final MutableIvyPublicationidentity publicationIdentity;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final TaskDependencyFactory taskDependencyFactory;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final Factory<ComponentParser> componentParserFactory;

    private final IvyModuleDescriptorSpecInternal descriptor;
    private final IvyConfigurationContainer configurations;
    private final DefaultIvyArtifactSet mainArtifacts;
    private final PublicationArtifactSet<IvyArtifact> metadataArtifacts;
    private final PublicationArtifactSet<IvyArtifact> derivedArtifacts;
    private final PublicationArtifactSet<IvyArtifact> publishableArtifacts;
    private final Property<ComponentParser.ParsedComponent> parsedComponent;
    private final Set<String> silencedVariants = new HashSet<>();
    private IvyArtifact ivyDescriptorArtifact;
    private TaskProvider<? extends Task> moduleDescriptorGenerator;
    private SingleOutputTaskIvyArtifact gradleModuleDescriptorArtifact;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;
    private boolean versionMappingInUse = false;
    private boolean silenceAllPublicationWarnings;
    private boolean withBuildIdentifier = false;

    @Inject
    public DefaultIvyPublication(
        String name, Instantiator instantiator, ObjectFactory objectFactory, MutableIvyPublicationidentity publicationIdentity, NotationParser<Object, IvyArtifact> ivyArtifactNotationParser,
        ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
        ImmutableAttributesFactory immutableAttributesFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator, VersionMappingStrategyInternal versionMappingStrategy, PlatformSupport platformSupport,
        DocumentationRegistry documentationRegistry, TaskDependencyFactory taskDependencyFactory
    ) {
        this.name = name;
        this.publicationIdentity = publicationIdentity;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.versionMappingStrategy = versionMappingStrategy;
        this.taskDependencyFactory = taskDependencyFactory;
        this.componentParserFactory = () -> new ComponentParser(
            instantiator,
            platformSupport,
            projectDependencyResolver,
            ivyArtifactNotationParser,
            documentationRegistry,
            collectionCallbackActionDecorator
        );

        this.parsedComponent = objectFactory.property(ComponentParser.ParsedComponent.class);
        this.parsedComponent.convention(getComponent().map(this::parseComponent));
        this.parsedComponent.finalizeValueOnRead();

        this.mainArtifacts = instantiator.newInstance(DefaultIvyArtifactSet.class, name, ivyArtifactNotationParser, fileCollectionFactory, collectionCallbackActionDecorator);
        this.metadataArtifacts = new DefaultPublicationArtifactSet<>(IvyArtifact.class, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.derivedArtifacts = new DefaultPublicationArtifactSet<>(IvyArtifact.class, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.publishableArtifacts = new CompositePublicationArtifactSet<>(taskDependencyFactory, IvyArtifact.class, Cast.uncheckedCast(new PublicationArtifactSet<?>[]{mainArtifacts, metadataArtifacts, derivedArtifacts}));

        this.configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator, collectionCallbackActionDecorator);

        this.descriptor = instantiator.newInstance(DefaultIvyModuleDescriptorSpec.class, this, instantiator, objectFactory);
        this.descriptor.getDependencies().set(this.parsedComponent.<Set<IvyDependencyInternal>>map(ComponentParser.ParsedComponent::getDependencies).orElse(Collections.emptySet()));
        this.descriptor.getGlobalExcludes().set(this.parsedComponent.map(ComponentParser.ParsedComponent::getGlobalExcludes).orElse(Collections.emptySet()));
        this.descriptor.getConfigurations().set(this.configurations);
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    public void withoutBuildIdentifier() {
        withBuildIdentifier = false;
    }

    @Override
    public void withBuildIdentifier() {
        withBuildIdentifier = true;
    }

    @Override
    public boolean isPublishBuildId() {
        return withBuildIdentifier;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("Ivy publication", name);
    }

    @Override
    public boolean isLegacy() {
        return false;
    }

    @Override
    public abstract Property<SoftwareComponentInternal> getComponent();

    @Override
    public IvyModuleDescriptorSpecInternal getDescriptor() {
        return descriptor;
    }

    @Override
    public void setIvyDescriptorGenerator(TaskProvider<? extends Task> descriptorGenerator) {
        if (ivyDescriptorArtifact != null) {
            metadataArtifacts.remove(ivyDescriptorArtifact);
        }
        ivyDescriptorArtifact = new SingleOutputTaskIvyArtifact(descriptorGenerator, publicationIdentity, "xml", "ivy", null, taskDependencyFactory);
        ivyDescriptorArtifact.setName("ivy");
        metadataArtifacts.add(ivyDescriptorArtifact);
    }

    @Override
    public void setModuleDescriptorGenerator(TaskProvider<? extends Task> descriptorGenerator) {
        moduleDescriptorGenerator = descriptorGenerator;
        if (gradleModuleDescriptorArtifact != null) {
            metadataArtifacts.remove(gradleModuleDescriptorArtifact);
        }
        gradleModuleDescriptorArtifact = null;
        updateModuleDescriptorArtifact();
    }

    private void updateModuleDescriptorArtifact() {
        if (!canPublishModuleMetadata()) {
            return;
        }
        if (moduleDescriptorGenerator == null) {
            return;
        }
        gradleModuleDescriptorArtifact = new SingleOutputTaskIvyArtifact(moduleDescriptorGenerator, publicationIdentity, "module", "json", null, taskDependencyFactory);
        metadataArtifacts.add(gradleModuleDescriptorArtifact);
        moduleDescriptorGenerator = null;
    }

    @Override
    public void descriptor(Action<? super IvyModuleDescriptorSpec> configure) {
        configure.execute(descriptor);
    }

    @Override
    public boolean isAlias() {
        return alias;
    }

    @Override
    public void setAlias(boolean alias) {
        this.alias = alias;
    }

    @Override
    public void from(SoftwareComponent component) {
        if (getComponent().isPresent()) {
            throw new InvalidUserDataException(String.format("Ivy publication '%s' cannot include multiple components", name));
        }
        getComponent().set((SoftwareComponentInternal) component);
        getComponent().finalizeValue();
        artifactsOverridden = false;

        updateModuleDescriptorArtifact();
    }

    // TODO: This method should be removed in favor of lazily adding artifacts to the publication state.
    // This is currently blocked by Signing eagerly realizing the publication artifacts.
    private void populateFromComponent() {
        if (populated) {
            return;
        }
        populated = true;
        if (!artifactsOverridden && parsedComponent.isPresent()) {
            mainArtifacts.addAll(parsedComponent.get().getArtifacts());
            configurations.addAll(parsedComponent.get().getConfigurations());
        }
    }

    private ComponentParser.ParsedComponent parseComponent(SoftwareComponentInternal component) {
        // Finalize the component to avoid GMM later modification
        // See issue https://github.com/gradle/gradle/issues/20581
        component.finalizeValue();

        ComponentParser.ParsedComponent result = componentParserFactory.create().build(component, getCoordinates(), versionMappingInUse);

        if (!silenceAllPublicationWarnings) {
            result.getWarnings().complete(getDisplayName() + " ivy metadata", silencedVariants);
        }

        return result;
    }

    /**
     * Encapsulates the logic required to extract data from a {@link SoftwareComponent} in order
     * to transform that component to a {@link ParsedComponent}
     */
    private static class ComponentParser {

        private final PlatformSupport platformSupport;
        private final ProjectDependencyPublicationResolver projectDependencyResolver;
        private final NotationParser<Object, IvyArtifact> ivyArtifactParser;
        private final DocumentationRegistry documentationRegistry;

        private final Set<IvyArtifact> artifacts = new LinkedHashSet<>();
        private final IvyConfigurationContainer configurations;
        private final DefaultIvyDependencySet ivyDependencies;
        private final Set<IvyExcludeRule> globalExcludes = new LinkedHashSet<>();
        private final PublicationWarningsCollector publicationWarningsCollector =
            new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, "", PUBLICATION_WARNING_FOOTER, "suppressIvyMetadataWarningsFor");

        public ComponentParser(
            Instantiator instantiator,
            PlatformSupport platformSupport,
            ProjectDependencyPublicationResolver projectDependencyResolver,
            NotationParser<Object, IvyArtifact> ivyArtifactParser,
            DocumentationRegistry documentationRegistry,
            CollectionCallbackActionDecorator collectionCallbackActionDecorator
        ) {
            this.platformSupport = platformSupport;
            this.projectDependencyResolver = projectDependencyResolver;
            this.ivyArtifactParser = ivyArtifactParser;
            this.documentationRegistry = documentationRegistry;

            this.configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator, collectionCallbackActionDecorator);
            this.ivyDependencies = instantiator.newInstance(DefaultIvyDependencySet.class, collectionCallbackActionDecorator);
        }

        private ParsedComponent build(SoftwareComponentInternal component, ModuleVersionIdentifier coordinates, boolean versionMappingInUse) {
            PublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry);

            Set<? extends SoftwareComponentVariant> variants = component.getUsages();

            populateConfigurations(variants);
            populateArtifacts(variants);
            populateDependencies(variants, publicationWarningsCollector, versionMappingInUse);
            populateGlobalExcludes(variants);

            return new ParsedComponent(
                artifacts,
                configurations,
                ivyDependencies,
                globalExcludes,
                publicationWarningsCollector
            );
        }

        private void populateConfigurations(Set<? extends SoftwareComponentVariant> variants) {
            IvyConfiguration defaultConfiguration = configurations.maybeCreate("default");
            for (SoftwareComponentVariant variant : variants) {
                String conf = mapVariantNameToIvyConfiguration(variant.getName());
                configurations.maybeCreate(conf);
                if (defaultShouldExtend(variant)) {
                    defaultConfiguration.extend(conf);
                }
            }
        }

        /**
         * In general, default extends all configurations such that you get 'everything' when depending on default.
         * If a variant is optional, however it is not included.
         * If a variant represents the Java API variant, it is also not included, because the Java Runtime variant already includes everything
         * (including both also works but would lead to some duplication, that might break backwards compatibility in certain cases).
         */
        private static boolean defaultShouldExtend(SoftwareComponentVariant variant) {
            if (!(variant instanceof IvyPublishingAwareVariant)) {
                return true;
            }
            if (((IvyPublishingAwareVariant) variant).isOptional()) {
                return false;
            }
            return !isJavaApiVariant(variant.getName());
        }

        private static boolean isJavaRuntimeVariant(String variantName) {
            return RUNTIME_VARIANT.equals(variantName) || RUNTIME_ELEMENTS_VARIANT.equals(variantName);
        }

        private static boolean isJavaApiVariant(String variantName) {
            return API_VARIANT.equals(variantName) || API_ELEMENTS_VARIANT.equals(variantName);
        }

        private void populateArtifacts(Set<? extends SoftwareComponentVariant> variants) {
            Map<String, IvyArtifact> seenArtifacts = Maps.newHashMap();
            for (SoftwareComponentVariant variant : variants) {
                String conf = mapVariantNameToIvyConfiguration(variant.getName());
                for (PublishArtifact publishArtifact : variant.getArtifacts()) {
                    String key = artifactKey(publishArtifact);
                    IvyArtifact ivyArtifact = seenArtifacts.get(key);
                    if (ivyArtifact == null) {
                        ivyArtifact = ivyArtifactParser.parseNotation(publishArtifact);
                        ivyArtifact.setConf(conf);
                        seenArtifacts.put(key, ivyArtifact);
                        artifacts.add(ivyArtifact);
                    } else {
                        ivyArtifact.setConf(ivyArtifact.getConf() + "," + conf);
                    }
                }
            }
        }

        private static String artifactKey(PublishArtifact publishArtifact) {
            return publishArtifact.getName() + ":" + publishArtifact.getType() + ":" + publishArtifact.getExtension() + ":" + publishArtifact.getClassifier();
        }

        private void populateDependencies(Set<? extends SoftwareComponentVariant> variants, PublicationWarningsCollector publicationWarningsCollector, boolean versionMappingInUse) {
            for (SoftwareComponentVariant variant : variants) {
                publicationWarningsCollector.newContext(variant.getName());
                for (ModuleDependency dependency : variant.getDependencies()) {
                    String confMapping = confMappingFor(variant, dependency);
                    if (!dependency.getAttributes().isEmpty()) {
                        publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                    }
                    if (dependency instanceof ProjectDependency) {
                        addProjectDependency((ProjectDependency) dependency, confMapping);
                    } else {
                        ExternalDependency externalDependency = (ExternalDependency) dependency;
                        if (platformSupport.isTargetingPlatform(dependency)) {
                            publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared as platform", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                        }
                        if (!versionMappingInUse && externalDependency.getVersion() == null) {
                            publicationWarningsCollector.addUnsupported(String.format("%s:%s declared without version", externalDependency.getGroup(), externalDependency.getName()));
                        }
                        addExternalDependency(externalDependency, confMapping, ((AttributeContainerInternal) variant.getAttributes()).asImmutable());
                    }
                }

                if (!variant.getDependencyConstraints().isEmpty()) {
                    for (DependencyConstraint constraint : variant.getDependencyConstraints()) {
                        publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared as a dependency constraint", constraint.getGroup(), constraint.getName(), constraint.getVersion()));
                    }
                }
                if (!variant.getCapabilities().isEmpty()) {
                    for (Capability capability : variant.getCapabilities()) {
                        publicationWarningsCollector.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Ivy", capability.getGroup(), capability.getName(), capability.getVersion()));
                    }
                }

            }
        }

        private void populateGlobalExcludes(Set<? extends SoftwareComponentVariant> variants) {
            for (SoftwareComponentVariant variant : variants) {
                String conf = mapVariantNameToIvyConfiguration(variant.getName());
                for (ExcludeRule excludeRule : variant.getGlobalExcludes()) {
                    globalExcludes.add(new DefaultIvyExcludeRule(excludeRule, conf));
                }
            }
        }

        private static String confMappingFor(SoftwareComponentVariant variant, ModuleDependency dependency) {
            String conf = mapVariantNameToIvyConfiguration(variant.getName());
            String confMappingTarget = mapVariantNameToIvyConfiguration(dependency.getTargetConfiguration());

            // If the following code is activated implementation/runtime separation will be published to ivy. This however is a breaking change.
            //
            // if (confMappingTarget == null) {
            //     if (variant instanceof MavenPublishingAwareVariant) {
            //         MavenPublishingAwareContext.ScopeMapping mapping = ((MavenPublishingAwareVariant) variant).getScopeMapping();
            //         if (mapping == runtime || mapping == runtime_optional) {
            //             confMappingTarget = "runtime";
            //         }
            //         if (mapping == compile || mapping == compile_optional) {
            //             confMappingTarget = "compile";
            //         }
            //     }
            // }

            if (confMappingTarget == null) {
                confMappingTarget = Dependency.DEFAULT_CONFIGURATION;
            }
            return conf + "->" + confMappingTarget;
        }

        /**
         * The variant name usually corresponds to the name of the Gradle configuration on which the variant is based on.
         * For backward compatibility, the 'apiElements' and 'runtimeElements' configurations/variants of the Java ecosystem are named 'compile' and 'runtime' in the publication.
         */
        private static String mapVariantNameToIvyConfiguration(String variantName) {
            if (isJavaApiVariant(variantName)) {
                return "compile";
            }
            if (isJavaRuntimeVariant(variantName)) {
                return "runtime";
            }
            return variantName;
        }

        private void addProjectDependency(ProjectDependency dependency, String confMapping) {
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, dependency);
            DefaultIvyDependency moduleDep = new DefaultIvyDependency(
                identifier.getGroup(), identifier.getName(), identifier.getVersion(), confMapping, dependency.isTransitive(), Collections.emptyList(), dependency.getExcludeRules());
            ivyDependencies.add(new DefaultIvyProjectDependency(moduleDep, dependency.getDependencyProject().getPath()));
        }

        private void addExternalDependency(ExternalDependency dependency, String confMapping, ImmutableAttributes attributes) {
            ivyDependencies.add(new DefaultIvyDependency(dependency, confMapping, attributes));
        }

        /**
         * Represents the parsed data from a {@link SoftwareComponent} that is required
         * to build a publication.
         */
        private static class ParsedComponent {
            private final Set<IvyArtifact> artifacts;
            private final IvyConfigurationContainer configurations;
            private final DefaultIvyDependencySet dependencies;
            private final Set<IvyExcludeRule> globalExcludes;
            private final PublicationWarningsCollector warnings;

            public ParsedComponent(
                Set<IvyArtifact> artifacts,
                IvyConfigurationContainer configurations,
                DefaultIvyDependencySet ivyDependencies,
                Set<IvyExcludeRule> globalExcludes,
                PublicationWarningsCollector warnings
            ) {
                this.artifacts = artifacts;
                this.configurations = configurations;
                this.dependencies = ivyDependencies;
                this.globalExcludes = globalExcludes;
                this.warnings = warnings;
            }

            public Set<IvyArtifact> getArtifacts() {
                return artifacts;
            }

            public IvyConfigurationContainer getConfigurations() {
                return configurations;
            }

            public DefaultIvyDependencySet getDependencies() {
                return dependencies;
            }

            public Set<IvyExcludeRule> getGlobalExcludes() {
                return globalExcludes;
            }

            public PublicationWarningsCollector getWarnings() {
                return warnings;
            }
        }
    }

    @Override
    public void configurations(Action<? super IvyConfigurationContainer> config) {
        populateFromComponent();
        config.execute(configurations);
    }

    @Override
    public IvyConfigurationContainer getConfigurations() {
        populateFromComponent();
        return configurations;
    }

    @Override
    public IvyArtifact artifact(Object source) {
        return mainArtifacts.artifact(source);
    }

    @Override
    public IvyArtifact artifact(Object source, Action<? super IvyArtifact> config) {
        return mainArtifacts.artifact(source, config);
    }

    @Override
    public void setArtifacts(Iterable<?> sources) {
        artifactsOverridden = true;
        mainArtifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    @Override
    public DefaultIvyArtifactSet getArtifacts() {
        populateFromComponent();
        return mainArtifacts;
    }

    @Override
    public String getOrganisation() {
        return publicationIdentity.getOrganisation();
    }

    @Override
    public void setOrganisation(String organisation) {
        publicationIdentity.setOrganisation(organisation);
    }

    @Override
    public String getModule() {
        return publicationIdentity.getModule();
    }

    @Override
    public void setModule(String module) {
        publicationIdentity.setModule(module);
    }

    @Override
    public String getRevision() {
        return publicationIdentity.getRevision();
    }

    @Override
    public void setRevision(String revision) {
        publicationIdentity.setRevision(revision);
    }

    @Override
    public PublicationArtifactSet<IvyArtifact> getPublishableArtifacts() {
        populateFromComponent();
        return publishableArtifacts;
    }

    @Override
    public void allPublishableArtifacts(Action<? super IvyArtifact> action) {
        publishableArtifacts.all(action);
    }

    @Override
    public void whenPublishableArtifactRemoved(Action<? super IvyArtifact> action) {
        publishableArtifacts.whenObjectRemoved(action);
    }

    @Override
    public IvyArtifact addDerivedArtifact(IvyArtifact originalArtifact, DerivedArtifact fileProvider) {
        DerivedArtifact effectiveFileProvider = originalArtifact == gradleModuleDescriptorArtifact
            ? new GradleModuleDescriptorDerivedArtifact(fileProvider, gradleModuleDescriptorArtifact)
            : fileProvider;

        IvyArtifact artifact = new DerivedIvyArtifact(originalArtifact, effectiveFileProvider, taskDependencyFactory);
        derivedArtifacts.add(artifact);
        return artifact;
    }

    @Override
    public void removeDerivedArtifact(IvyArtifact artifact) {
        derivedArtifacts.remove(artifact);
    }

    @Override
    public IvyPublicationIdentity getIdentity() {
        return publicationIdentity;
    }

    @Override
    public IvyNormalizedPublication asNormalisedPublication() {
        populateFromComponent();

        // Preserve identity of artifacts
        Set<IvyArtifact> main = linkedHashSetOf(
            normalized(
                mainArtifacts.stream(),
                this::isValidArtifact
            )
        );
        LinkedHashSet<IvyArtifact> all = new LinkedHashSet<>(main);
        normalized(
            Streams.concat(metadataArtifacts.stream(), derivedArtifacts.stream()),
            this::isPublishableArtifact
        ).forEach(all::add);
        return new IvyNormalizedPublication(
            name,
            getCoordinates(),
            main,
            asReadOnlyIdentity(getIdentity()),
            getIvyDescriptorFile(),
            all
        );
    }

    private static <T> Set<T> linkedHashSetOf(Stream<T> stream) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        stream.forEach(set::add);
        return set;
    }

    private static IvyPublicationIdentity asReadOnlyIdentity(IvyPublicationIdentity identity) {
        return new DefaultReadOnlyIvyPublicationIdentity(identity);
    }

    private boolean isValidArtifact(IvyArtifact artifact) {
        // Validation is done this way for backwards compatibility
        File artifactFile = artifact.getFile();
        if (artifactFile == null) {
            throw new InvalidIvyPublicationException(name, String.format("artifact file does not exist: '%s'", artifact));
        }
        if (!((IvyArtifactInternal) artifact).shouldBePublished()) {
            // Fail if it's the main artifact, otherwise simply disable publication
            if (artifact.getClassifier() == null) {
                throw new IllegalStateException("Artifact " + artifact.getFile().getName() + " wasn't produced by this build.");
            }
            return false;
        }
        return true;
    }

    private static Stream<IvyArtifact> normalized(Stream<IvyArtifact> artifacts, Predicate<IvyArtifact> predicate) {
        return artifacts
            .filter(predicate)
            .map(DefaultIvyPublication::normalizedArtifactFor);
    }

    private boolean isPublishableArtifact(IvyArtifact element) {
        if (!((PublicationArtifactInternal) element).shouldBePublished()) {
            return false;
        }
        if (gradleModuleDescriptorArtifact == element) {
            // We temporarily want to allow skipping the publication of Gradle module metadata
            return gradleModuleDescriptorArtifact.isEnabled();
        }
        return true;
    }

    private static NormalizedIvyArtifact normalizedArtifactFor(IvyArtifact artifact) {
        return ((IvyArtifactInternal) artifact).asNormalisedArtifact();
    }

    @Override
    public boolean writeGradleMetadataMarker() {
        return canPublishModuleMetadata()
            && gradleModuleDescriptorArtifact != null
            && gradleModuleDescriptorArtifact.isEnabled();
    }

    private boolean canPublishModuleMetadata() {
        // Cannot yet publish module metadata without component
        return getComponent().isPresent();
    }

    private File getIvyDescriptorFile() {
        if (ivyDescriptorArtifact == null) {
            throw new IllegalStateException("ivyDescriptorArtifact not set for publication");
        }
        return ivyDescriptorArtifact.getFile();
    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return DefaultModuleVersionIdentifier.newId(getOrganisation(), getModule(), getRevision());
    }

    @Nullable
    @Override
    public <T> T getCoordinates(Class<T> type) {
        if (type.isAssignableFrom(ModuleVersionIdentifier.class)) {
            return type.cast(getCoordinates());
        }
        return null;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return immutableAttributesFactory.of(ProjectInternal.STATUS_ATTRIBUTE, getDescriptor().getStatus());
    }

    private String getPublishedUrl(PublishArtifact source) {
        return getArtifactFileName(source.getClassifier(), source.getExtension());
    }

    private String getArtifactFileName(String classifier, String extension) {
        StringBuilder artifactPath = new StringBuilder();
        ModuleVersionIdentifier coordinates = getCoordinates();
        artifactPath.append(coordinates.getName());
        artifactPath.append('-');
        artifactPath.append(coordinates.getVersion());
        if (GUtil.isTrue(classifier)) {
            artifactPath.append('-');
            artifactPath.append(classifier);
        }
        if (GUtil.isTrue(extension)) {
            artifactPath.append('.');
            artifactPath.append(extension);
        }
        return artifactPath.toString();
    }

    @Override
    public PublishedFile getPublishedFile(PublishArtifact source) {
        final String publishedUrl = getPublishedUrl(source);
        return new PublishedFile() {
            @Override
            public String getName() {
                return publishedUrl;
            }

            @Override
            public String getUri() {
                return publishedUrl;
            }
        };
    }

    @Override
    public void versionMapping(Action<? super VersionMappingStrategy> configureAction) {
        this.versionMappingInUse = true;
        configureAction.execute(versionMappingStrategy);
    }

    @Override
    public void suppressIvyMetadataWarningsFor(String variantName) {
        silencedVariants.add(variantName);
    }

    @Override
    public void suppressAllIvyMetadataWarnings() {
        this.silenceAllPublicationWarnings = true;
    }

    @Override
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return versionMappingStrategy;
    }

    private static class GradleModuleDescriptorDerivedArtifact implements DerivedArtifact {

        private final DerivedArtifact derivedArtifact;

        private final SingleOutputTaskIvyArtifact gradleModuleDescriptorArtifact;

        public GradleModuleDescriptorDerivedArtifact(DerivedArtifact derivedArtifact, SingleOutputTaskIvyArtifact gradleModuleDescriptorArtifact) {
            this.derivedArtifact = derivedArtifact;
            this.gradleModuleDescriptorArtifact = gradleModuleDescriptorArtifact;
        }

        @Nullable
        @Override
        public File create() {
            return derivedArtifact.create();
        }

        @Override
        public boolean shouldBePublished() {
            return gradleModuleDescriptorArtifact.shouldBePublished() &&
                derivedArtifact.shouldBePublished();
        }
    }
}
