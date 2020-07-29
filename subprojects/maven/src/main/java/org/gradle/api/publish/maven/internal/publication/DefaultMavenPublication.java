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
import com.google.common.collect.Lists;
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
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.MavenPublishingAwareContext;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.VersionMappingStrategy;
import org.gradle.api.publish.internal.CompositePublicationArtifactSet;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.AbstractMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;
import org.gradle.api.publish.maven.internal.artifact.DerivedMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MutableMavenProjectIdentity;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

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

import static java.util.stream.Collectors.toMap;

public class DefaultMavenPublication implements MavenPublicationInternal {
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

    private static final Comparator<String> VARIANT_ORDERING = (left, right) -> {
        // API first
        if (API_VARIANT.equals(left) || API_ELEMENTS_VARIANT.equals(left)) {
            return -1;
        }
        if (API_VARIANT.equals(right) || API_ELEMENTS_VARIANT.equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    };

    @VisibleForTesting
    public static final String INCOMPATIBLE_FEATURE = " contains dependencies that will produce a pom file that cannot be consumed by a Maven client.";
    @VisibleForTesting
    public static final String UNSUPPORTED_FEATURE = " contains dependencies that cannot be represented in a published pom file.";
    @VisibleForTesting
    public static final String PUBLICATION_WARNING_FOOTER = "These issues indicate information that is lost in the published 'pom' metadata file, which may be an issue if the published library is consumed by an old Gradle version or Apache Maven.\nThe 'module' metadata file, which is used by Gradle 6+ is not affected.";


    private final String name;
    private final MavenPomInternal pom;
    private final MutableMavenProjectIdentity projectIdentity;
    private final DefaultMavenArtifactSet mainArtifacts;
    private final PublicationArtifactSet<MavenArtifact> metadataArtifacts;
    private final PublicationArtifactSet<MavenArtifact> derivedArtifacts;
    private final PublicationArtifactSet<MavenArtifact> publishableArtifacts;
    private final Set<MavenDependencyInternal> runtimeDependencies = new LinkedHashSet<>();
    private final Set<MavenDependencyInternal> apiDependencies = new LinkedHashSet<>();
    private final Set<MavenDependencyInternal> optionalDependencies = new LinkedHashSet<>();
    private final Set<MavenDependency> runtimeDependencyConstraints = new LinkedHashSet<>();
    private final Set<MavenDependency> apiDependencyConstraints = new LinkedHashSet<>();
    private final Set<MavenDependency> importDependencyConstraints = new LinkedHashSet<>();
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final PlatformSupport platformSupport;
    private final Set<String> silencedVariants = new HashSet<>();
    private MavenArtifact pomArtifact;
    private SingleOutputTaskMavenArtifact moduleMetadataArtifact;
    private TaskProvider<? extends Task> moduleDescriptorGenerator;
    private SoftwareComponentInternal component;
    private boolean isPublishWithOriginalFileName;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;
    private boolean versionMappingInUse = false;
    private boolean silenceAllPublicationWarnings;
    private boolean withBuildIdentifier = true;

    @Inject
    public DefaultMavenPublication(
        String name, MutableMavenProjectIdentity projectIdentity, NotationParser<Object, MavenArtifact> mavenArtifactParser, Instantiator instantiator,
        ObjectFactory objectFactory, ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
        ImmutableAttributesFactory immutableAttributesFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator, VersionMappingStrategyInternal versionMappingStrategy,
        PlatformSupport platformSupport) {
        this.name = name;
        this.projectDependencyResolver = projectDependencyResolver;
        this.projectIdentity = projectIdentity;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.versionMappingStrategy = versionMappingStrategy;
        this.platformSupport = platformSupport;
        this.mainArtifacts = instantiator.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory, collectionCallbackActionDecorator);
        this.metadataArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        derivedArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        publishableArtifacts = new CompositePublicationArtifactSet<>(MavenArtifact.class, Cast.uncheckedCast(new PublicationArtifactSet<?>[]{mainArtifacts, metadataArtifacts, derivedArtifacts}));
        pom = instantiator.newInstance(DefaultMavenPom.class, this, instantiator, objectFactory);
    }

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

    @Nullable
    @Override
    public SoftwareComponentInternal getComponent() {
        return component;
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
        pomArtifact = new SingleOutputTaskMavenArtifact(pomGenerator, "pom", null);
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
        moduleMetadataArtifact = new SingleOutputTaskMavenArtifact(moduleDescriptorGenerator, "module", null);
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
        if (this.component != null) {
            throw new InvalidUserDataException(String.format("Maven publication '%s' cannot include multiple components", name));
        }
        this.component = (SoftwareComponentInternal) component;
        artifactsOverridden = false;
        updateModuleDescriptorArtifact();
    }

    private void populateFromComponent() {
        if (populated) {
            return;
        }
        populated = true;
        if (component == null) {
            return;
        }
        PublicationWarningsCollector publicationWarningsCollector = new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, INCOMPATIBLE_FEATURE, PUBLICATION_WARNING_FOOTER, "suppressPomMetadataWarningsFor");
        Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
        Set<PublishedDependency> seenDependencies = Sets.newHashSet();
        Set<DependencyConstraint> seenConstraints = Sets.newHashSet();
        for (UsageContext usageContext : getSortedUsageContexts()) {
            // TODO Need a smarter way to map usage to artifact classifier
            for (PublishArtifact publishArtifact : usageContext.getArtifacts()) {
                ArtifactKey key = new ArtifactKey(publishArtifact.getFile(), publishArtifact.getClassifier(), publishArtifact.getExtension());
                if (!artifactsOverridden && seenArtifacts.add(key)) {
                    artifact(publishArtifact);
                }
            }

            Set<ExcludeRule> globalExcludes = usageContext.getGlobalExcludes();

            publicationWarningsCollector.newContext(usageContext.getName());
            Set<MavenDependencyInternal> dependencies = dependenciesFor(usageContext);
            for (ModuleDependency dependency : usageContext.getDependencies()) {
                if (seenDependencies.add(PublishedDependency.of(dependency))) {
                    if (isDependencyWithDefaultArtifact(dependency) && dependencyMatchesProject(dependency)) {
                        // We skip all self referencing dependency declarations, unless they have custom artifact information
                        continue;
                    }
                    if (platformSupport.isTargetingPlatform(dependency)) {
                        if (dependency instanceof ProjectDependency) {
                            addImportDependencyConstraint((ProjectDependency) dependency);
                        } else {
                            if (!versionMappingInUse && isVersionMavenIncompatible(dependency.getVersion())) {
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
                            if (!versionMappingInUse && isVersionMavenIncompatible(dependency.getVersion())) {
                                publicationWarningsCollector.addIncompatible(String.format("%s:%s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                            }
                            addModuleDependency(dependency, globalExcludes, dependencies);
                        }
                    }
                }
            }
            Set<MavenDependency> dependencyConstraints = dependencyConstraintsFor(usageContext);
            for (DependencyConstraint dependency : usageContext.getDependencyConstraints()) {
                if (seenConstraints.add(dependency)) {
                    if (dependency instanceof DefaultProjectDependencyConstraint) {
                        addDependencyConstraint((DefaultProjectDependencyConstraint) dependency, dependencyConstraints);
                    } else if (dependency.getVersion() != null) {
                        if (!versionMappingInUse && isVersionMavenIncompatible(dependency.getVersion())) {
                            publicationWarningsCollector.addIncompatible(String.format("constraint %s:%s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                        }
                        addDependencyConstraint(dependency, dependencyConstraints);
                    }
                }
            }
            if (!usageContext.getCapabilities().isEmpty()) {
                for (Capability capability : usageContext.getCapabilities()) {
                    if (isNotDefaultCapability(capability)) {
                        publicationWarningsCollector.addVariantUnsupported(String.format("Declares capability %s:%s:%s which cannot be mapped to Maven", capability.getGroup(), capability.getName(), capability.getVersion()));
                    }
                }
            }
        }
        if (!silenceAllPublicationWarnings) {
            publicationWarningsCollector.complete(getDisplayName() + " pom metadata", silencedVariants);
        }
    }

    private boolean isNotDefaultCapability(Capability capability) {
        ModuleVersionIdentifier coordinates = getCoordinates();
        return !capability.getGroup().equals(coordinates.getGroup())
            || !capability.getName().equals(coordinates.getName())
            || !capability.getVersion().equals(coordinates.getVersion());
    }

    private boolean isDependencyWithDefaultArtifact(ModuleDependency dependency) {
        if (dependency.getArtifacts().isEmpty()) {
            return true;
        }
        return dependency.getArtifacts().stream().allMatch(artifact -> Strings.nullToEmpty(artifact.getClassifier()).isEmpty());
    }

    private boolean dependencyMatchesProject(ModuleDependency dependency) {
        return getCoordinates().getModule().equals(DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getName()));
    }

    private boolean isVersionMavenIncompatible(String version) {
        if (version == null) {
            return false;
        }
        if (version.contains("+")) {
            return true;
        }
        if (version.contains("latest")) {
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

    private List<UsageContext> getSortedUsageContexts() {
        List<UsageContext> usageContexts = Lists.newArrayList(this.component.getUsages());
        if (component instanceof AdhocComponentWithVariants) {
            Collections.sort(Cast.uncheckedCast(usageContexts), Comparator.comparing(MavenPublishingAwareContext::getScopeMapping));
        } else {
            Collections.sort(usageContexts, (u1, u2) -> VARIANT_ORDERING.compare(u1.getName(), u2.getName()));
        }
        return usageContexts;
    }

    private Set<MavenDependencyInternal> dependenciesFor(UsageContext usage) {
        if (usage instanceof MavenPublishingAwareContext) {
            MavenPublishingAwareContext.ScopeMapping mapping = ((MavenPublishingAwareContext) usage).getScopeMapping();
            switch (mapping) {
                case compile:
                    return apiDependencies;
                case runtime:
                    return runtimeDependencies;
                case compile_optional:
                case runtime_optional:
                    // currently single list of optionals
                    return optionalDependencies;
            }
        }
        // legacy mode for internal APIs
        String name = usage.getName();
        if (API_VARIANT.equals(name) || API_ELEMENTS_VARIANT.equals(name)) {
            return apiDependencies;
        }
        return runtimeDependencies;
    }

    private Set<MavenDependency> dependencyConstraintsFor(UsageContext usage) {
        if (usage instanceof MavenPublishingAwareContext) {
            MavenPublishingAwareContext.ScopeMapping mapping = ((MavenPublishingAwareContext) usage).getScopeMapping();
            switch (mapping) {
                case compile:
                case compile_optional:
                    return apiDependencyConstraints;
                case runtime:
                case runtime_optional:
                    return runtimeDependencyConstraints;
            }
        }
        // legacy mode
        String name = usage.getName();
        if (API_VARIANT.equals(name) || API_ELEMENTS_VARIANT.equals(name)) {
            return apiDependencyConstraints;
        }
        return runtimeDependencyConstraints;
    }

    private void addProjectDependency(ProjectDependency dependency, Set<ExcludeRule> globalExcludes, Set<MavenDependencyInternal> dependencies) {
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, dependency);
        dependencies.add(new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), Collections.emptyList(), getExcludeRules(globalExcludes, dependency)));
    }

    private void addModuleDependency(ModuleDependency dependency, Set<ExcludeRule> globalExcludes, Set<MavenDependencyInternal> dependencies) {
        dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), dependency.getArtifacts(), getExcludeRules(globalExcludes, dependency)));
    }

    private void addDependencyConstraint(DependencyConstraint dependency, Set<MavenDependency> dependencies) {
        dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion()));
    }

    private void addDependencyConstraint(DefaultProjectDependencyConstraint dependency, Set<MavenDependency> dependencies) {
        ProjectDependency projectDependency = dependency.getProjectDependency();
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(ModuleVersionIdentifier.class, projectDependency);
        dependencies.add(new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion()));
    }

    private static Set<ExcludeRule> getExcludeRules(Set<ExcludeRule> globalExcludes, ModuleDependency dependency) {
        return dependency.isTransitive() ? Sets.union(globalExcludes, dependency.getExcludeRules()) : EXCLUDE_ALL_RULE;
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
        return projectIdentity.getGroupId().get();
    }

    @Override
    public void setGroupId(String groupId) {
        projectIdentity.getGroupId().set(groupId);
    }

    @Override
    public String getArtifactId() {
        return projectIdentity.getArtifactId().get();
    }

    @Override
    public void setArtifactId(String artifactId) {
        projectIdentity.getArtifactId().set(artifactId);
    }

    @Override
    public String getVersion() {
        return projectIdentity.getVersion().get();
    }

    @Override
    public void setVersion(String version) {
        projectIdentity.getVersion().set(version);
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
    @Nullable
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return versionMappingStrategy;
    }

    @Override
    public boolean writeGradleMetadataMarker() {
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
        MavenArtifact artifact = new DerivedMavenArtifact((AbstractMavenArtifact) originalArtifact, file);
        derivedArtifacts.add(artifact);
        return artifact;
    }

    @Override
    public void removeDerivedArtifact(MavenArtifact artifact) {
        derivedArtifacts.remove(artifact);
    }

    @Override
    public MutableMavenProjectIdentity getMavenProjectIdentity() {
        return projectIdentity;
    }

    @Override
    public Set<MavenDependency> getApiDependencyConstraints() {
        populateFromComponent();
        return apiDependencyConstraints;
    }

    @Override
    public Set<MavenDependency> getRuntimeDependencyConstraints() {
        populateFromComponent();
        return runtimeDependencyConstraints;
    }

    @Override
    public Set<MavenDependency> getImportDependencyConstraints() {
        populateFromComponent();
        return importDependencyConstraints;
    }

    @Override
    public Set<MavenDependencyInternal> getRuntimeDependencies() {
        populateFromComponent();
        return runtimeDependencies;
    }

    @Override
    public Set<MavenDependencyInternal> getApiDependencies() {
        populateFromComponent();
        return apiDependencies;
    }

    @Override
    public Set<MavenDependencyInternal> getOptionalDependencies() {
        populateFromComponent();
        return optionalDependencies;
    }

    @Override
    public MavenNormalizedPublication asNormalisedPublication() {
        populateFromComponent();

        // Preserve identity of artifacts
        Map<MavenArtifact, MavenArtifact> normalizedArtifacts = normalizedMavenArtifacts();

        return new MavenNormalizedPublication(
            name,
            projectIdentity,
            pom.getPackaging(),
            normalizedArtifactFor(getPomArtifact(), normalizedArtifacts),
            normalizedArtifactFor(determineMainArtifact(), normalizedArtifacts),
            new LinkedHashSet<>(normalizedArtifacts.values())
        );
    }

    private MavenArtifact normalizedArtifactFor(MavenArtifact artifact, Map<MavenArtifact, MavenArtifact> normalizedArtifacts) {
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
                this::normalizedArtifactFor
            ));
    }

    private MavenArtifact normalizedArtifactFor(MavenArtifact artifact) {
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

    @Override
    public String determinePackagingFromArtifacts() {
        Set<MavenArtifact> unclassifiedArtifacts = getUnclassifiedArtifactsWithExtension();
        if (unclassifiedArtifacts.size() == 1) {
            return unclassifiedArtifacts.iterator().next().getExtension();
        }
        return "pom";
    }

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

    private boolean hasNoClassifier(MavenArtifact element) {
        return element.getClassifier() == null || element.getClassifier().length() == 0;
    }

    private boolean hasExtension(MavenArtifact element) {
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
        return getComponent() != null;
    }

    @Override
    public PublishedFile getPublishedFile(final PublishArtifact source) {
        checkThatArtifactIsPublishedUnmodified(source);
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
        String version = getMavenProjectIdentity().getVersion().get();
        String status = MavenVersionUtils.inferStatusFromVersionNumber(version);
        return immutableAttributesFactory.of(ProjectInternal.STATUS_ATTRIBUTE, status);
    }

    /*
     * When the artifacts declared in a component are modified for publishing (name/classifier/extension), then the
     * Maven publication no longer represents the underlying java component. Instead of
     * publishing incorrect metadata, we fail any attempt to publish the module metadata.
     *
     * In the long term, we will likely prevent any modification of artifacts added from a component. Instead, we will
     * make it easier to modify the component(s) produced by a project, allowing the
     * published metadata to accurately reflect the local component metadata.
     */
    private void checkThatArtifactIsPublishedUnmodified(PublishArtifact source) {
        populateFromComponent();
        for (MavenArtifact mavenArtifact : mainArtifacts) {
            if (source.getFile().equals(mavenArtifact.getFile())
                && source.getExtension().equals(mavenArtifact.getExtension())
                && Strings.nullToEmpty(source.getClassifier()).equals(Strings.nullToEmpty(mavenArtifact.getClassifier()))) {
                return;
            }
        }

        throw new PublishException("Cannot publish module metadata where component artifacts are modified.");
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

        public ArtifactKey(File file, String classifier, String extension) {
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
