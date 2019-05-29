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
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
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
import org.gradle.api.internal.java.JavaLibraryPlatform;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.VersionMappingStrategy;
import org.gradle.api.publish.internal.CompositePublicationArtifactSet;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;
import org.gradle.api.publish.maven.internal.artifact.DerivedMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MutableMavenProjectIdentity;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.CollectionUtils;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.FeaturePreviews.Feature.GRADLE_METADATA;

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
    public static final String UNSUPPORTED_FEATURE = " contains dependencies that cannot be represented in a published pom file.";

    private final String name;
    private final MavenPomInternal pom;
    private final MutableMavenProjectIdentity projectIdentity;
    private final DefaultMavenArtifactSet mainArtifacts;
    private final PublicationArtifactSet<MavenArtifact> metadataArtifacts;
    private final PublicationArtifactSet<MavenArtifact> derivedArtifacts;
    private final PublicationArtifactSet<MavenArtifact> publishableArtifacts;
    private final Set<MavenDependencyInternal> runtimeDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final Set<MavenDependencyInternal> apiDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final Set<MavenDependencyInternal> optionalDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final Set<MavenDependency> runtimeDependencyConstraints = new LinkedHashSet<MavenDependency>();
    private final Set<MavenDependency> apiDependencyConstraints = new LinkedHashSet<MavenDependency>();
    private final Set<MavenDependency> importDependencyConstraints = new LinkedHashSet<MavenDependency>();
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final FeaturePreviews featurePreviews;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private MavenArtifact pomArtifact;
    private SingleOutputTaskMavenArtifact moduleMetadataArtifact;
    private TaskProvider<? extends Task> moduleDescriptorGenerator;
    private SoftwareComponentInternal component;
    private boolean isPublishWithOriginalFileName;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;
    private boolean versionMappingInUse = false;

    public DefaultMavenPublication(
            String name, MutableMavenProjectIdentity projectIdentity, NotationParser<Object, MavenArtifact> mavenArtifactParser, Instantiator instantiator,
            ObjectFactory objectFactory, ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
            FeaturePreviews featurePreviews, ImmutableAttributesFactory immutableAttributesFactory,
            CollectionCallbackActionDecorator collectionCallbackActionDecorator, VersionMappingStrategyInternal versionMappingStrategy) {
        this.name = name;
        this.projectDependencyResolver = projectDependencyResolver;
        this.projectIdentity = projectIdentity;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.versionMappingStrategy = versionMappingStrategy;
        this.mainArtifacts = instantiator.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory, collectionCallbackActionDecorator);
        this.metadataArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        derivedArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        publishableArtifacts = new CompositePublicationArtifactSet<>(MavenArtifact.class, mainArtifacts, metadataArtifacts, derivedArtifacts);
        pom = instantiator.newInstance(DefaultMavenPom.class, this, instantiator, objectFactory);
        this.featurePreviews = featurePreviews;
    }

    @Override
    public String getName() {
        return name;
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
        if (component instanceof JavaLibraryPlatform) {
            DeprecationLogger.nagUserWithDeprecatedIndirectUserCodeCause("components.javaLibraryPlatform", "Use the 'java-platform' plugin instead.");
        }
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
        PublicationWarningsCollector publicationWarningsCollector = new PublicationWarningsCollector(LOG, UNSUPPORTED_FEATURE, INCOMPATIBLE_FEATURE);
        Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
        Set<String> seenDependencies = Sets.newHashSet();
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

            Set<MavenDependencyInternal> dependencies = dependenciesFor(usageContext);
            for (ModuleDependency dependency : usageContext.getDependencies()) {
                if (seenDependencies.add(dependency.getGroup() + ":" + dependency.getName())) {
                    if (PlatformSupport.isTargettingPlatform(dependency)) {
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
                if (seenConstraints.add(dependency) && dependency.getVersion() != null) {
                    if (!versionMappingInUse && isVersionMavenIncompatible(dependency.getVersion())) {
                        publicationWarningsCollector.addIncompatible(String.format("constraint %s:%s:%s declared with a Maven incompatible version notation", dependency.getGroup(), dependency.getName(), dependency.getVersion()));
                    }
                    addDependencyConstraint(dependency, dependencyConstraints);
                }
            }
            if (!usageContext.getCapabilities().isEmpty()) {
                for (Capability capability : usageContext.getCapabilities()) {
                    publicationWarningsCollector.addUnsupported(String.format("Declares capability %s:%s:%s", capability.getGroup(), capability.getName(), capability.getVersion()));
                }
            }
        }
        publicationWarningsCollector.complete(getDisplayName());
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
        if (API_VARIANT.equals(name)  || API_ELEMENTS_VARIANT.equals(name)) {
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
        dependencies.add(new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), Collections.<DependencyArtifact>emptyList(), getExcludeRules(globalExcludes, dependency)));
    }

    private void addModuleDependency(ModuleDependency dependency, Set<ExcludeRule> globalExcludes, Set<MavenDependencyInternal> dependencies) {
        dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), dependency.getArtifacts(), getExcludeRules(globalExcludes, dependency)));
    }

    private void addDependencyConstraint(DependencyConstraint dependency, Set<MavenDependency> dependencies) {
        dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion()));
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
    @Nullable
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return versionMappingStrategy;
    }

    @Override
    public boolean writeGradleMetadataMarker() {
        if (canPublishModuleMetadata() && moduleMetadataArtifact != null && moduleMetadataArtifact.isEnabled()) {
            return true;
        }
        return false;
    }

    @Override
    public FileCollection getPublishableFiles() {
        return getPublishableArtifacts().getFiles();
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
    public MavenArtifact addDerivedArtifact(MavenArtifact originalArtifact, Factory<File> file) {
        MavenArtifact artifact = new DerivedMavenArtifact(originalArtifact, file);
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
        DomainObjectSet<MavenArtifact> existingDerivedArtifacts = this.derivedArtifacts.matching(new Spec<MavenArtifact>() {
            @Override
            public boolean isSatisfiedBy(MavenArtifact artifact) {
                return artifact.getFile().exists();
            }
        });
        Set<MavenArtifact> artifactsToBePublished = CompositeDomainObjectSet.create(MavenArtifact.class, mainArtifacts, metadataArtifacts, existingDerivedArtifacts).matching(new Spec<MavenArtifact>() {
            @Override
            public boolean isSatisfiedBy(MavenArtifact element) {
                if (moduleMetadataArtifact == element) {
                    // We temporarily want to allow skipping the publication of Gradle module metadata
                    return moduleMetadataArtifact.isEnabled();
                }
                return true;
            }
        });
        return new MavenNormalizedPublication(name, projectIdentity, pom.getPackaging(), getPomArtifact(), determineMainArtifact(), artifactsToBePublished);
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
        return CollectionUtils.filter(mainArtifacts, new Spec<MavenArtifact>() {
            @Override
            public boolean isSatisfiedBy(MavenArtifact mavenArtifact) {
                return hasNoClassifier(mavenArtifact) && hasExtension(mavenArtifact);
            }
        });
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
        if (getComponent() == null) {
            // Cannot yet publish module metadata without component
            return false;
        }
        if (getComponent() instanceof ComponentWithVariants) {
            // Always publish `ComponentWithVariants`
            return true;
        }
        return featurePreviews.isFeatureEnabled(GRADLE_METADATA);
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
}
