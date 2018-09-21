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
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.internal.CompositePublicationArtifactSet;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactSet;
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
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.CollectionUtils;
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

    /*
     * Maven supports wildcards in exclusion rules according to:
     * http://www.smartjava.org/content/maven-and-wildcard-exclusions
     * https://issues.apache.org/jira/browse/MNG-3832
     * This should be used for non-transitive dependencies
     */
    private static final Set<ExcludeRule> EXCLUDE_ALL_RULE = Collections.<ExcludeRule>singleton(new DefaultExcludeRule("*", "*"));
    private static final Comparator<? super UsageContext> USAGE_ORDERING = new Comparator<UsageContext>() {
        @Override
        public int compare(UsageContext left, UsageContext right) {
            // API first
            if (left.getUsage().getName().equals(Usage.JAVA_API)) {
                return -1;
            }
            if (right.getUsage().getName().equals(Usage.JAVA_API)) {
                return 1;
            }
            return left.getUsage().getName().compareTo(right.getUsage().getName());
        }
    };

    private final String name;
    private final MavenPomInternal pom;
    private final MutableMavenProjectIdentity projectIdentity;
    private final DefaultMavenArtifactSet mainArtifacts;
    private final PublicationArtifactSet<MavenArtifact> metadataArtifacts;
    private final PublicationArtifactSet<MavenArtifact> derivedArtifacts;
    private final PublicationArtifactSet<MavenArtifact> publishableArtifacts;
    private final Set<MavenDependencyInternal> runtimeDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final Set<MavenDependencyInternal> apiDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final Set<MavenDependency> runtimeDependencyConstraints = new LinkedHashSet<MavenDependency>();
    private final Set<MavenDependency> apiDependencyConstraints = new LinkedHashSet<MavenDependency>();
    private final Set<MavenDependency> importDependencyConstraints = new LinkedHashSet<MavenDependency>();
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final FeaturePreviews featurePreviews;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private MavenArtifact pomArtifact;
    private MavenArtifact moduleMetadataArtifact;
    private Task moduleDescriptorGenerator;
    private SoftwareComponentInternal component;
    private boolean isPublishWithOriginalFileName;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;

    public DefaultMavenPublication(
        String name, MutableMavenProjectIdentity projectIdentity, NotationParser<Object, MavenArtifact> mavenArtifactParser, Instantiator instantiator,
        ObjectFactory objectFactory, ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
        FeaturePreviews featurePreviews, ImmutableAttributesFactory immutableAttributesFactory) {
        this.name = name;
        this.projectDependencyResolver = projectDependencyResolver;
        this.projectIdentity = projectIdentity;
        this.immutableAttributesFactory = immutableAttributesFactory;
        mainArtifacts = instantiator.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory);
        metadataArtifacts = new DefaultPublicationArtifactSet<MavenArtifact>(MavenArtifact.class, "metadata artifacts for " + name, fileCollectionFactory);
        derivedArtifacts = new DefaultPublicationArtifactSet<MavenArtifact>(MavenArtifact.class, "derived artifacts for " + name, fileCollectionFactory);
        publishableArtifacts = new CompositePublicationArtifactSet<MavenArtifact>(MavenArtifact.class, mainArtifacts, metadataArtifacts, derivedArtifacts);
        pom = instantiator.newInstance(DefaultMavenPom.class, this, instantiator, objectFactory);
        this.featurePreviews = featurePreviews;
    }

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

    public MavenPomInternal getPom() {
        return pom;
    }

    @Override
    public void setPomGenerator(Task pomGenerator) {
        if (pomArtifact != null) {
            metadataArtifacts.remove(pomArtifact);
        }
        pomArtifact = new SingleOutputTaskMavenArtifact(pomGenerator, "pom", null);
        metadataArtifacts.add(pomArtifact);
    }

    @Override
    public void setModuleDescriptorGenerator(Task descriptorGenerator) {
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
        Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
        Set<ModuleDependency> seenDependencies = Sets.newHashSet();
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

            Set<MavenDependencyInternal> dependencies = dependenciesFor(usageContext.getUsage());
            for (ModuleDependency dependency : usageContext.getDependencies()) {
                if (seenDependencies.add(dependency)) {
                    if (dependency instanceof ProjectDependency) {
                        addProjectDependency((ProjectDependency) dependency, globalExcludes, dependencies);
                    } else {
                        if (PlatformSupport.isTargettingPlatform(dependency)) {
                            addImportDependencyConstraint(dependency);
                        } else {
                            addModuleDependency(dependency, globalExcludes, dependencies);
                        }
                    }
                }
            }
            Set<MavenDependency> dependencyConstraints = dependencyConstraintsFor(usageContext.getUsage());
            for (DependencyConstraint dependency : usageContext.getDependencyConstraints()) {
                if (seenConstraints.add(dependency) && dependency.getVersion() != null) {
                    addDependencyConstraint(dependency, dependencyConstraints);
                }
            }
        }
    }

    private void addImportDependencyConstraint(ModuleDependency dependency) {
        importDependencyConstraints.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), "pom"));
    }

    private List<UsageContext> getSortedUsageContexts() {
        List<UsageContext> usageContexts = Lists.newArrayList(this.component.getUsages());
        Collections.sort(usageContexts, USAGE_ORDERING);
        return usageContexts;
    }

    private Set<MavenDependencyInternal> dependenciesFor(Usage usage) {
        if (Usage.JAVA_API.equals(usage.getName())) {
            return apiDependencies;
        }
        return runtimeDependencies;
    }

    private Set<MavenDependency> dependencyConstraintsFor(Usage usage) {
        if (Usage.JAVA_API.equals(usage.getName())) {
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

    public MavenArtifact artifact(Object source) {
        return mainArtifacts.artifact(source);
    }

    public MavenArtifact artifact(Object source, Action<? super MavenArtifact> config) {
        return mainArtifacts.artifact(source, config);
    }

    public MavenArtifactSet getArtifacts() {
        populateFromComponent();
        return mainArtifacts;
    }

    public void setArtifacts(Iterable<?> sources) {
        artifactsOverridden = true;
        mainArtifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    public String getGroupId() {
        return projectIdentity.getGroupId().get();
    }

    public void setGroupId(String groupId) {
        projectIdentity.getGroupId().set(groupId);
    }

    public String getArtifactId() {
        return projectIdentity.getArtifactId().get();
    }

    public void setArtifactId(String artifactId) {
        projectIdentity.getArtifactId().set(artifactId);
    }

    public String getVersion() {
        return projectIdentity.getVersion().get();
    }

    public void setVersion(String version) {
        projectIdentity.getVersion().set(version);
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

    public Set<MavenDependencyInternal> getRuntimeDependencies() {
        populateFromComponent();
        return runtimeDependencies;
    }

    public Set<MavenDependencyInternal> getApiDependencies() {
        populateFromComponent();
        return apiDependencies;
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        populateFromComponent();
        DomainObjectSet<MavenArtifact> existingDerivedArtifacts = this.derivedArtifacts.matching(new Spec<MavenArtifact>() {
            @Override
            public boolean isSatisfiedBy(MavenArtifact artifact) {
                return artifact.getFile().exists();
            }
        });
        Set<MavenArtifact> artifactsToBePublished = CompositeDomainObjectSet.create(MavenArtifact.class, mainArtifacts, metadataArtifacts, existingDerivedArtifacts);
        return new MavenNormalizedPublication(name, pom.getPackaging(), getPomArtifact(), projectIdentity, artifactsToBePublished, determineMainArtifact());
    }

    private MavenArtifact getPomArtifact() {
        if (pomArtifact == null) {
            throw new IllegalStateException("pomArtifact not set for publication");
        }
        return pomArtifact;
    }

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
