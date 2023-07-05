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

package org.gradle.api.publish.maven.internal.publication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.DefaultSoftwareComponentVariant;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.VersionMappingStrategy;
import org.gradle.api.publish.internal.CompositePublicationArtifactSet;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.component.MavenPublishingAwareVariant;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.AbstractMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;
import org.gradle.api.publish.maven.internal.artifact.DerivedMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenProjectDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.publish.maven.internal.validation.MavenPublicationErrorChecker;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public abstract class DefaultMavenPublication implements MavenPublicationInternal {
    private final static Logger LOG = Logging.getLogger(DefaultMavenPublication.class);

    private static final String API_VARIANT = "api";
    private static final String API_ELEMENTS_VARIANT = "apiElements";

    /*
     * Maven supports wildcards in exclusion rules according to:
     * http://www.smartjava.org/content/maven-and-wildcard-exclusions
     * https://issues.apache.org/jira/browse/MNG-3832
     * This should be used for non-transitive dependencies
     */
    private static final Set<ExcludeRule> EXCLUDE_ALL_RULE = Collections.singleton(new DefaultExcludeRule("*", "*"));

    @VisibleForTesting
    public static final String INCOMPATIBLE_FEATURE = " contains dependencies that will produce a pom file that cannot be consumed by a Maven client.";
    @VisibleForTesting
    public static final String UNSUPPORTED_FEATURE = " contains dependencies that cannot be represented in a published pom file.";
    @VisibleForTesting
    public static final String PUBLICATION_WARNING_FOOTER = "These issues indicate information that is lost in the published 'pom' metadata file, which may be an issue if the published library is consumed by an old Gradle version or Apache Maven.\nThe 'module' metadata file, which is used by Gradle 6+ is not affected.";

    private final String name;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final TaskDependencyFactory taskDependencyFactory;
    private final Factory<ComponentParser> componentParserFactory;

    private final MavenPomInternal pom;
    private final DefaultMavenArtifactSet mainArtifacts;
    private final PublicationArtifactSet<MavenArtifact> metadataArtifacts;
    private final PublicationArtifactSet<MavenArtifact> derivedArtifacts;
    private final PublicationArtifactSet<MavenArtifact> publishableArtifacts;
    private final Property<ComponentParser.ParsedComponent> parsedComponent;
    private final Set<String> silencedVariants = new HashSet<>();
    private MavenArtifact pomArtifact;
    private SingleOutputTaskMavenArtifact moduleMetadataArtifact;
    private TaskProvider<? extends Task> moduleDescriptorGenerator;
    private boolean isPublishWithOriginalFileName;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;
    private boolean versionMappingInUse = false;
    private boolean silenceAllPublicationWarnings;
    private boolean withBuildIdentifier = false;

    @Inject
    public DefaultMavenPublication(
        String name,
        DependencyMetaDataProvider dependencyMetaDataProvider,
        NotationParser<Object, MavenArtifact> mavenArtifactParser,
        ObjectFactory objectFactory,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        FileCollectionFactory fileCollectionFactory,
        ImmutableAttributesFactory immutableAttributesFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator,
        VersionMappingStrategyInternal versionMappingStrategy,
        PlatformSupport platformSupport,
        DocumentationRegistry documentationRegistry,
        TaskDependencyFactory taskDependencyFactory,
        ProviderFactory providerFactory
    ) {
        this.name = name;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.versionMappingStrategy = versionMappingStrategy;
        this.taskDependencyFactory = taskDependencyFactory;
        this.componentParserFactory = () -> new ComponentParser(
            platformSupport,
            projectDependencyResolver,
            mavenArtifactParser,
            documentationRegistry
        );

        this.parsedComponent = objectFactory.property(ComponentParser.ParsedComponent.class);
        this.parsedComponent.convention(getComponent().map(this::parseComponent));
        this.parsedComponent.finalizeValueOnRead();

        this.mainArtifacts = objectFactory.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory, collectionCallbackActionDecorator);
        this.metadataArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.derivedArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.publishableArtifacts = new CompositePublicationArtifactSet<>(taskDependencyFactory, MavenArtifact.class, Cast.uncheckedCast(new PublicationArtifactSet<?>[]{mainArtifacts, metadataArtifacts, derivedArtifacts}));

        pom = objectFactory.newInstance(DefaultMavenPom.class, objectFactory, versionMappingStrategy);
        pom.getWriteGradleMetadataMarker().set(providerFactory.provider(this::writeGradleMetadataMarker));
        pom.getDependencies().set(parsedComponent.map(ComponentParser.ParsedComponent::getDependencies).orElse(DefaultMavenPomDependencies.EMPTY));
        pom.getPackagingProperty().convention(providerFactory.provider(this::determinePackagingFromArtifacts));

        Module module = dependencyMetaDataProvider.getModule();
        MavenProjectIdentity projectIdentity = pom.getProjectIdentity();
        projectIdentity.getGroupId().convention(providerFactory.provider(module::getGroup));
        projectIdentity.getArtifactId().convention(providerFactory.provider(module::getName));
        projectIdentity.getVersion().convention(providerFactory.provider(module::getVersion));
    }

    @Override
    public abstract Property<SoftwareComponentInternal> getComponent();

    @Override
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
        return Describables.withTypeAndName("Maven publication", name);
    }

    @Override
    public boolean isLegacy() {
        return false;
    }

    @Override
    public MavenPomInternal getPom() {
        return pom;
    }

    @Override
    public void setPomGenerator(TaskProvider<? extends Task> pomGenerator) {
        if (pomArtifact != null) {
            metadataArtifacts.remove(pomArtifact);
        }
        pomArtifact = new SingleOutputTaskMavenArtifact(pomGenerator, "pom", null, taskDependencyFactory);
        metadataArtifacts.add(pomArtifact);
    }

    @Override
    public void setModuleDescriptorGenerator(TaskProvider<? extends Task> descriptorGenerator) {
        moduleDescriptorGenerator = descriptorGenerator;
        if (moduleMetadataArtifact != null) {
            metadataArtifacts.remove(moduleMetadataArtifact);
        }
        moduleMetadataArtifact = null;
        updateModuleDescriptorArtifact();
    }

    private void updateModuleDescriptorArtifact() {
        if (!canPublishModuleMetadata()) {
            return;
        }
        if (moduleDescriptorGenerator == null) {
            return;
        }
        moduleMetadataArtifact = new SingleOutputTaskMavenArtifact(moduleDescriptorGenerator, "module", null, taskDependencyFactory);
        metadataArtifacts.add(moduleMetadataArtifact);
        moduleDescriptorGenerator = null;
    }


    @Override
    public void pom(Action<? super MavenPom> configure) {
        configure.execute(pom);
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
            throw new InvalidUserDataException(String.format("Maven publication '%s' cannot include multiple components", name));
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
        }
    }

    private ComponentParser.ParsedComponent parseComponent(SoftwareComponentInternal component) {
        // Finalize the component to avoid GMM later modification
        // See issue https://github.com/gradle/gradle/issues/20581
        component.finalizeValue();

        ComponentParser.ParsedComponent result = componentParserFactory.create().build(component, getCoordinates(), versionMappingInUse);

        if (!silenceAllPublicationWarnings) {
            result.getWarnings().complete(getDisplayName() + " pom metadata", silencedVariants);
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
        private final NotationParser<Object, MavenArtifact> mavenArtifactParser;
        private final DocumentationRegistry documentationRegistry;

        private final Set<MavenArtifact> artifacts = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> runtimeDependencies = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> apiDependencies = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> optionalApiDependencies = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> optionalRuntimeDependencies = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> runtimeDependencyConstraints = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> apiDependencyConstraints = new LinkedHashSet<>();
        private final Set<MavenDependencyInternal> importDependencyConstraints = new LinkedHashSet<>();
        private final PublicationWarningsCollector publicationWarningsCollector =
            new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, INCOMPATIBLE_FEATURE, PUBLICATION_WARNING_FOOTER, "suppressPomMetadataWarningsFor");

        public ComponentParser(
            PlatformSupport platformSupport,
            ProjectDependencyPublicationResolver projectDependencyResolver,
            NotationParser<Object, MavenArtifact> mavenArtifactParser,
            DocumentationRegistry documentationRegistry
        ) {
            this.platformSupport = platformSupport;
            this.projectDependencyResolver = projectDependencyResolver;
            this.mavenArtifactParser = mavenArtifactParser;
            this.documentationRegistry = documentationRegistry;
        }

        private ParsedComponent build(SoftwareComponentInternal component, ModuleVersionIdentifier coordinates, boolean versionMappingInUse) {
            MavenPublicationErrorChecker.checkForUnpublishableAttributes(component, documentationRegistry);

            Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
            Set<PublishedDependency> seenDependencies = Sets.newHashSet();
            Set<DependencyConstraint> seenConstraints = Sets.newHashSet();
            for (MavenPublishingAwareVariant variant : getSortedVariants(component)) {
                // TODO Need a smarter way to map variant to artifact classifier
                for (PublishArtifact publishArtifact : variant.getArtifacts()) {
                    ArtifactKey key = new ArtifactKey(publishArtifact.getFile(), publishArtifact.getClassifier(), publishArtifact.getExtension());
                    if (seenArtifacts.add(key)) {
                        artifacts.add(mavenArtifactParser.parseNotation(publishArtifact));
                    }
                }

                Set<ExcludeRule> globalExcludes = variant.getGlobalExcludes();

                publicationWarningsCollector.newContext(variant.getName());
                Set<MavenDependencyInternal> dependencies = dependenciesFor(variant);
                for (ModuleDependency dependency : variant.getDependencies()) {
                    if (seenDependencies.add(PublishedDependency.of(dependency))) {
                        if (isDependencyWithDefaultArtifact(dependency) && dependencyMatchesProject(dependency, coordinates)) {
                            // We skip all self referencing dependency declarations, unless they have custom artifact information
                            continue;
                        }
                        if (platformSupport.isTargetingPlatform(dependency)) {
                            if (dependency instanceof ProjectDependency) {
                                addImportDependencyConstraint((ProjectDependency) dependency);
                            } else {
                                if (isMavenIncompatibleVersionInUse(dependency.getVersion(), versionMappingInUse)) {
                                    publicationWarningsCollector.addIncompatible(String.format("%s:%s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                                }
                                addImportDependencyConstraint(dependency);
                            }
                        } else {
                            if (!dependency.getAttributes().isEmpty()) {
                                publicationWarningsCollector.addUnsupported(String.format("%s:%s:%s declared with Gradle attributes", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                            }
                            if (dependency instanceof ProjectDependency) {
                                addProjectDependency((ProjectDependency) dependency, globalExcludes, dependencies);
                            } else {
                                if (isMavenIncompatibleVersionInUse(dependency.getVersion(), versionMappingInUse)) {
                                    publicationWarningsCollector.addIncompatible(String.format("%s:%s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                                }
                                addModuleDependency(dependency, globalExcludes, dependencies);
                            }
                        }
                    }
                }
                Set<MavenDependencyInternal> dependencyConstraints = dependencyConstraintsFor(variant);
                for (DependencyConstraint dependency : variant.getDependencyConstraints()) {
                    if (seenConstraints.add(dependency)) {
                        if (dependency instanceof DefaultProjectDependencyConstraint) {
                            addDependencyConstraint((DefaultProjectDependencyConstraint) dependency, dependencyConstraints);
                        } else if (dependency.getVersion() != null) {
                            if (isMavenIncompatibleVersionInUse(dependency.getVersion(), versionMappingInUse)) {
                                publicationWarningsCollector.addIncompatible(String.format("constraint %s:%s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                            }
                            addDependencyConstraint(dependency, dependencyConstraints);
                        } else {
                            // Some dependency constraints, like those with rejectAll() have no version and do not map to Maven.
                            publicationWarningsCollector.addIncompatible(String.format("constraint %s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName()));
                        }
                    }
                }

                if (!variant.getCapabilities().isEmpty()) {
                    for (Capability capability : variant.getCapabilities()) {
                        if (isNotDefaultCapability(capability, coordinates)) {
                            publicationWarningsCollector.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Maven", capability.getGroup(), capability.getName(), capability.getVersion()));
                        }
                    }
                }
            }

            return new ParsedComponent(
                artifacts,
                new DefaultMavenPomDependencies(
                    ImmutableList.copyOf(runtimeDependencies),
                    ImmutableList.copyOf(apiDependencies),
                    ImmutableList.copyOf(optionalApiDependencies),
                    ImmutableList.copyOf(optionalRuntimeDependencies),
                    ImmutableList.copyOf(runtimeDependencyConstraints),
                    ImmutableList.copyOf(apiDependencyConstraints),
                    ImmutableList.copyOf(importDependencyConstraints)
                ),
                publicationWarningsCollector
            );
        }

        private static boolean isNotDefaultCapability(Capability capability, ModuleVersionIdentifier coordinates) {
            return !capability.getGroup().equals(coordinates.getGroup())
                || !capability.getName().equals(coordinates.getName())
                || !capability.getVersion().equals(coordinates.getVersion());
        }

        private static boolean isDependencyWithDefaultArtifact(ModuleDependency dependency) {
            if (dependency.getArtifacts().isEmpty()) {
                return true;
            }
            return dependency.getArtifacts().stream().allMatch(artifact -> Strings.nullToEmpty(artifact.getClassifier()).isEmpty());
        }

        private static boolean dependencyMatchesProject(ModuleDependency dependency, ModuleVersionIdentifier coordinates) {
            return coordinates.getModule().equals(DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getName()));
        }

        private static boolean isMavenIncompatibleVersionInUse(@Nullable String version, boolean versionMappingInUse) {
            if (versionMappingInUse) {
                return false;
            }

            if (version == null) {
                return false;
            }
            if (DefaultVersionSelectorScheme.isSubVersion(version)) {
                return true;
            }
            if (DefaultVersionSelectorScheme.isLatestVersion(version)) {
                return !MavenVersionSelectorScheme.isSubstituableLatest(version);
            }
            return false;
        }

        private void addImportDependencyConstraint(ProjectDependency dependency) {
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, dependency);
            importDependencyConstraints.add(new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), "pom"));
        }

        private void addImportDependencyConstraint(ModuleDependency dependency) {
            importDependencyConstraints.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), "pom"));
        }

        private static List<MavenPublishingAwareVariant> getSortedVariants(SoftwareComponentInternal component) {
            return component.getUsages().stream()
                .map(ComponentParser::asMavenAwareVariant)
                .sorted(Comparator.comparing(MavenPublishingAwareVariant::getScopeMapping))
                .collect(Collectors.toList());
        }

        private static MavenPublishingAwareVariant asMavenAwareVariant(SoftwareComponentVariant variant) {
            if (variant instanceof MavenPublishingAwareVariant) {
                return (MavenPublishingAwareVariant) variant;
            } else {
                return new LegacyVariant(variant);
            }
        }

        private static class LegacyVariant extends DefaultSoftwareComponentVariant implements MavenPublishingAwareVariant {
            private LegacyVariant(SoftwareComponentVariant delegate) {
                super(
                    delegate.getName(), delegate.getAttributes(), delegate.getArtifacts(), delegate.getDependencies(),
                    delegate.getDependencyConstraints(), delegate.getCapabilities(), delegate.getGlobalExcludes()
                );
            }

            @Override
            public ScopeMapping getScopeMapping() {
                // TODO: Update native plugins to use maven-aware variants so we can remove this.
                String name = getName();
                if (API_VARIANT.equals(name) || API_ELEMENTS_VARIANT.equals(name)) {
                    return ScopeMapping.compile;
                }
                return ScopeMapping.runtime;
            }
        }

        private Set<MavenDependencyInternal> dependenciesFor(MavenPublishingAwareVariant variant) {
            switch (variant.getScopeMapping()) {
                case compile:
                    return apiDependencies;
                case runtime:
                    return runtimeDependencies;
                case compile_optional:
                    return optionalApiDependencies;
                case runtime_optional:
                    return optionalRuntimeDependencies;
                default:
                    throw new IllegalArgumentException("Unknown scope mapping: " + variant.getScopeMapping());
            }
        }

        private Set<MavenDependencyInternal> dependencyConstraintsFor(MavenPublishingAwareVariant variant) {
            switch (variant.getScopeMapping()) {
                case compile:
                case compile_optional:
                    return apiDependencyConstraints;
                case runtime:
                case runtime_optional:
                    return runtimeDependencyConstraints;
                default:
                    throw new IllegalArgumentException("Unknown scope mapping: " + variant.getScopeMapping());
            }
        }

        private void addProjectDependency(ProjectDependency dependency, Set<ExcludeRule> globalExcludes, Set<MavenDependencyInternal> dependencies) {
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, dependency);
            DefaultMavenDependency moduleDependency = new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), Collections.emptyList(), getExcludeRules(globalExcludes, dependency));
            dependencies.add(new DefaultMavenProjectDependency(moduleDependency, dependency.getDependencyProject().getPath()));
        }

        private static void addModuleDependency(ModuleDependency dependency, Set<ExcludeRule> globalExcludes, Set<MavenDependencyInternal> dependencies) {
            dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), dependency.getArtifacts(), getExcludeRules(globalExcludes, dependency)));
        }

        private static void addDependencyConstraint(DependencyConstraint dependency, Set<MavenDependencyInternal> dependencies) {
            dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion()));
        }

        private void addDependencyConstraint(DefaultProjectDependencyConstraint dependency, Set<MavenDependencyInternal> dependencies) {
            ProjectDependency projectDependency = dependency.getProjectDependency();
            ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, projectDependency);
            DefaultMavenDependency moduleDependency = new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion());
            dependencies.add(new DefaultMavenProjectDependency(moduleDependency, projectDependency.getDependencyProject().getPath()));
        }

        private static Set<ExcludeRule> getExcludeRules(Set<ExcludeRule> globalExcludes, ModuleDependency dependency) {
            return dependency.isTransitive() ? Sets.union(globalExcludes, dependency.getExcludeRules()) : EXCLUDE_ALL_RULE;
        }

        /**
         * Represents the parsed data from a {@link SoftwareComponent} that is required
         * to build a publication.
         */
        private static class ParsedComponent {
            private final Set<MavenArtifact> artifacts;
            private final MavenPomDependencies dependencies;
            private final PublicationWarningsCollector warnings;

            public ParsedComponent(
                Set<MavenArtifact> artifacts,
                MavenPomDependencies dependencies,
                PublicationWarningsCollector warnings
            ) {
                this.artifacts = artifacts;
                this.dependencies = dependencies;
                this.warnings = warnings;
            }

            public Set<MavenArtifact> getArtifacts() {
                return artifacts;
            }

            public MavenPomDependencies getDependencies() {
                return dependencies;
            }

            public PublicationWarningsCollector getWarnings() {
                return warnings;
            }
        }
    }

    @Override
    public MavenArtifact artifact(Object source) {
        return mainArtifacts.artifact(source);
    }

    @Override
    public MavenArtifact artifact(Object source, Action<? super MavenArtifact> config) {
        return mainArtifacts.artifact(source, config);
    }

    @Override
    public MavenArtifactSet getArtifacts() {
        populateFromComponent();
        return mainArtifacts;
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
    public String getGroupId() {
        return pom.getProjectIdentity().getGroupId().get();
    }

    @Override
    public void setGroupId(String groupId) {
        pom.getProjectIdentity().getGroupId().set(groupId);
    }

    @Override
    public String getArtifactId() {
        return pom.getProjectIdentity().getArtifactId().get();
    }

    @Override
    public void setArtifactId(String artifactId) {
        pom.getProjectIdentity().getArtifactId().set(artifactId);
    }

    @Override
    public String getVersion() {
        return pom.getProjectIdentity().getVersion().get();
    }

    @Override
    public void setVersion(String version) {
        pom.getProjectIdentity().getVersion().set(version);
    }

    @Override
    public void versionMapping(Action<? super VersionMappingStrategy> configureAction) {
        this.versionMappingInUse = true;
        configureAction.execute(versionMappingStrategy);
    }

    @Override
    public void suppressPomMetadataWarningsFor(String variantName) {
        this.silencedVariants.add(variantName);
    }

    @Override
    public void suppressAllPomMetadataWarnings() {
        this.silenceAllPublicationWarnings = true;
    }

    @Override
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return versionMappingStrategy;
    }

    private boolean writeGradleMetadataMarker() {
        return canPublishModuleMetadata() && moduleMetadataArtifact != null && moduleMetadataArtifact.isEnabled();
    }

    @Override
    public PublicationArtifactSet<MavenArtifact> getPublishableArtifacts() {
        populateFromComponent();
        return publishableArtifacts;
    }

    @Override
    public void allPublishableArtifacts(Action<? super MavenArtifact> action) {
        publishableArtifacts.all(action);
    }

    @Override
    public void whenPublishableArtifactRemoved(Action<? super MavenArtifact> action) {
        publishableArtifacts.whenObjectRemoved(action);
    }

    @Override
    public MavenArtifact addDerivedArtifact(MavenArtifact originalArtifact, DerivedArtifact file) {
        MavenArtifact artifact = new DerivedMavenArtifact((AbstractMavenArtifact) originalArtifact, file, taskDependencyFactory);
        derivedArtifacts.add(artifact);
        return artifact;
    }

    @Override
    public void removeDerivedArtifact(MavenArtifact artifact) {
        derivedArtifacts.remove(artifact);
    }

    @Override
    public MavenNormalizedPublication asNormalisedPublication() {
        populateFromComponent();

        // Preserve identity of artifacts
        Map<MavenArtifact, MavenArtifact> normalizedArtifacts = normalizedMavenArtifacts();

        return new MavenNormalizedPublication(
            name,
            pom.getProjectIdentity(),
            pom.getPackaging(),
            normalizedArtifactFor(getPomArtifact(), normalizedArtifacts),
            normalizedArtifactFor(determineMainArtifact(), normalizedArtifacts),
            new LinkedHashSet<>(normalizedArtifacts.values())
        );
    }

    @Nullable
    private static MavenArtifact normalizedArtifactFor(@Nullable MavenArtifact artifact, Map<MavenArtifact, MavenArtifact> normalizedArtifacts) {
        if (artifact == null) {
            return null;
        }
        MavenArtifact normalized = normalizedArtifacts.get(artifact);
        if (normalized != null) {
            return normalized;
        }
        return normalizedArtifactFor(artifact);
    }

    private Map<MavenArtifact, MavenArtifact> normalizedMavenArtifacts() {
        return artifactsToBePublished()
            .stream()
            .collect(toMap(
                Function.identity(),
                DefaultMavenPublication::normalizedArtifactFor
            ));
    }

    private static MavenArtifact normalizedArtifactFor(MavenArtifact artifact) {
        // TODO: introduce something like a NormalizedMavenArtifact to capture the required MavenArtifact
        //  information and only that instead of having MavenArtifact references in
        //  MavenNormalizedPublication
        return new SerializableMavenArtifact(artifact);
    }

    private DomainObjectSet<MavenArtifact> artifactsToBePublished() {
        return CompositeDomainObjectSet.create(
            MavenArtifact.class,
            Cast.uncheckedCast(
                new DomainObjectCollection<?>[]{mainArtifacts, metadataArtifacts, derivedArtifacts}
            )
        ).matching(element -> {
            if (!((PublicationArtifactInternal) element).shouldBePublished()) {
                return false;
            }
            if (moduleMetadataArtifact == element) {
                // We temporarily want to allow skipping the publication of Gradle module metadata
                return moduleMetadataArtifact.isEnabled();
            }
            return true;
        });
    }

    private MavenArtifact getPomArtifact() {
        if (pomArtifact == null) {
            throw new IllegalStateException("pomArtifact not set for publication");
        }
        return pomArtifact;
    }

    // TODO Remove this attempt to guess packaging from artifacts. Packaging should come from component, or be explicitly set.
    private String determinePackagingFromArtifacts() {
        Set<MavenArtifact> unclassifiedArtifacts = getUnclassifiedArtifactsWithExtension();
        if (unclassifiedArtifacts.size() == 1) {
            return unclassifiedArtifacts.iterator().next().getExtension();
        }
        return "pom";
    }

    @Nullable
    private MavenArtifact determineMainArtifact() {
        Set<MavenArtifact> unclassifiedArtifacts = getUnclassifiedArtifactsWithExtension();
        if (unclassifiedArtifacts.isEmpty()) {
            return null;
        }
        if (unclassifiedArtifacts.size() == 1) {
            // Pom packaging doesn't matter when we have a single unclassified artifact
            return unclassifiedArtifacts.iterator().next();
        }
        for (MavenArtifact unclassifiedArtifact : unclassifiedArtifacts) {
            // With multiple unclassified artifacts, choose the one with extension matching pom packaging
            String packaging = pom.getPackaging();
            if (unclassifiedArtifact.getExtension().equals(packaging)) {
                return unclassifiedArtifact;
            }
        }
        return null;
    }

    private Set<MavenArtifact> getUnclassifiedArtifactsWithExtension() {
        populateFromComponent();
        return CollectionUtils.filter(mainArtifacts, mavenArtifact -> hasNoClassifier(mavenArtifact) && hasExtension(mavenArtifact));
    }

    private static boolean hasNoClassifier(MavenArtifact element) {
        return element.getClassifier() == null || element.getClassifier().length() == 0;
    }

    private static boolean hasExtension(MavenArtifact element) {
        return element.getExtension() != null && element.getExtension().length() > 0;
    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return DefaultModuleVersionIdentifier.newId(getGroupId(), getArtifactId(), getVersion());
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
    public void publishWithOriginalFileName() {
        this.isPublishWithOriginalFileName = true;
    }

    private boolean canPublishModuleMetadata() {
        // Cannot yet publish module metadata without component
        return getComponent().isPresent();
    }

    @Override
    public PublishedFile getPublishedFile(final PublishArtifact source) {
        populateFromComponent();
        MavenPublicationErrorChecker.checkThatArtifactIsPublishedUnmodified(source, mainArtifacts);
        final String publishedUrl = getPublishedUrl(source);
        final String publishedName = isPublishWithOriginalFileName ? source.getFile().getName() : publishedUrl;
        return new PublishedFile() {
            @Override
            public String getName() {
                return publishedName;
            }

            @Override
            public String getUri() {
                return publishedUrl;
            }
        };
    }

    @Nullable
    @Override
    public ImmutableAttributes getAttributes() {
        String version = pom.getProjectIdentity().getVersion().get();
        String status = MavenVersionUtils.inferStatusFromVersionNumber(version);
        return immutableAttributesFactory.of(ProjectInternal.STATUS_ATTRIBUTE, status);
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

    private static class ArtifactKey {
        final File file;
        final String classifier;
        final String extension;

        public ArtifactKey(File file, @Nullable String classifier, @Nullable String extension) {
            this.file = file;
            this.classifier = classifier;
            this.extension = extension;
        }

        @Override
        public boolean equals(Object obj) {
            ArtifactKey other = (ArtifactKey) obj;
            return file.equals(other.file) && Objects.equal(classifier, other.classifier) && Objects.equal(extension, other.extension);
        }

        @Override
        public int hashCode() {
            return file.hashCode() ^ Objects.hashCode(classifier, extension);
        }
    }

    /**
     * This is used to de-duplicate dependencies based on relevant contents.
     * In particular, versions are ignored.
     */
    private static class PublishedDependency {
        private final String group;
        private final String name;
        private final String targetConfiguration;
        private final AttributeContainer attributes;
        private final Set<DependencyArtifact> artifacts;
        private final Set<ExcludeRule> excludeRules;
        private final List<Capability> requestedCapabilities;

        private PublishedDependency(String group, String name, String targetConfiguration, AttributeContainer attributes, Set<DependencyArtifact> artifacts, Set<ExcludeRule> excludeRules, List<Capability> requestedCapabilities) {
            this.group = group;
            this.name = name;
            this.targetConfiguration = targetConfiguration;
            this.attributes = attributes;
            this.artifacts = artifacts;
            this.excludeRules = excludeRules;
            this.requestedCapabilities = requestedCapabilities;
        }

        static PublishedDependency of(ModuleDependency dep) {
            return new PublishedDependency(
                dep.getGroup(),
                dep.getName(),
                dep.getTargetConfiguration(),
                dep.getAttributes(),
                dep.getArtifacts(),
                dep.getExcludeRules(),
                dep.getRequestedCapabilities()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PublishedDependency that = (PublishedDependency) o;
            return Objects.equal(group, that.group) &&
                Objects.equal(name, that.name) &&
                Objects.equal(targetConfiguration, that.targetConfiguration) &&
                Objects.equal(attributes, that.attributes) &&
                Objects.equal(artifacts, that.artifacts) &&
                Objects.equal(excludeRules, that.excludeRules) &&
                Objects.equal(requestedCapabilities, that.requestedCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, name, targetConfiguration, attributes, artifacts, excludeRules, requestedCapabilities);
        }
    }

    private static class SerializableMavenArtifact implements MavenArtifact, PublicationArtifactInternal {

        private final File file;
        private final String extension;
        private final String classifier;
        private final boolean shouldBePublished;

        public SerializableMavenArtifact(MavenArtifact artifact) {
            PublicationArtifactInternal artifactInternal = (PublicationArtifactInternal) artifact;
            this.file = artifact.getFile();
            this.extension = artifact.getExtension();
            this.classifier = artifact.getClassifier();
            this.shouldBePublished = artifactInternal.shouldBePublished();
        }

        @Override
        public String getExtension() {
            return extension;
        }

        @Override
        public void setExtension(String extension) {
            throw new IllegalStateException();
        }

        @Nullable
        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public void setClassifier(@Nullable String classifier) {
            throw new IllegalStateException();
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public void builtBy(Object... tasks) {
            throw new IllegalStateException();
        }

        @Override
        public TaskDependency getBuildDependencies() {
            throw new IllegalStateException();
        }

        @Override
        public boolean shouldBePublished() {
            return shouldBePublished;
        }
    }

}
