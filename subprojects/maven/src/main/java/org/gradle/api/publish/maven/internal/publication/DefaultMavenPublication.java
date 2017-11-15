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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.DependencyArtifact;
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
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.specs.Spec;
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
    private final MavenProjectIdentity projectIdentity;
    private final DefaultMavenArtifactSet mavenArtifacts;
    private final Set<MavenDependencyInternal> runtimeDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final Set<MavenDependencyInternal> apiDependencies = new LinkedHashSet<MavenDependencyInternal>();
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ExperimentalFeatures experimentalFeatures;
    private FileCollection pomFile;
    private SoftwareComponentInternal component;
    private boolean isPublishWithOriginalFileName;
    private boolean alias;

    public DefaultMavenPublication(
        String name, MavenProjectIdentity projectIdentity, NotationParser<Object, MavenArtifact> mavenArtifactParser, Instantiator instantiator,
        ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
        ExperimentalFeatures experimentalFeatures
    ) {
        this.name = name;
        this.projectDependencyResolver = projectDependencyResolver;
        this.projectIdentity = new DefaultMavenProjectIdentity(projectIdentity.getGroupId(), projectIdentity.getArtifactId(), projectIdentity.getVersion());
        mavenArtifacts = instantiator.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory);
        pom = instantiator.newInstance(DefaultMavenPom.class, this);
        this.experimentalFeatures = experimentalFeatures;
    }

    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public SoftwareComponentInternal getComponent() {
        return component;
    }

    public MavenPomInternal getPom() {
        return pom;
    }

    public void setPomFile(FileCollection pomFile) {
        this.pomFile = pomFile;
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

        Set<ArtifactKey> seenArtifacts = Sets.newHashSet();
        Set<ModuleDependency> seenDependencies = Sets.newHashSet();
        for (UsageContext usageContext : getSortedUsageContexts()) {
            // TODO Need a smarter way to map usage to artifact classifier
            for (PublishArtifact publishArtifact : usageContext.getArtifacts()) {
                ArtifactKey key = new ArtifactKey(publishArtifact.getFile(), publishArtifact.getClassifier(), publishArtifact.getExtension());
                if (seenArtifacts.add(key)) {
                    artifact(publishArtifact);
                }
            }

            Set<MavenDependencyInternal> dependencies = dependenciesFor(usageContext.getUsage());
            for (ModuleDependency dependency : usageContext.getDependencies()) {
                if (seenDependencies.add(dependency)) {
                    if (dependency instanceof ProjectDependency) {
                        addProjectDependency((ProjectDependency) dependency, dependencies);
                    } else {
                        addModuleDependency(dependency, dependencies);
                    }
                }
            }
        }
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

    private void addProjectDependency(ProjectDependency dependency, Set<MavenDependencyInternal> dependencies) {
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(dependency);
        dependencies.add(new DefaultMavenDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), Collections.<DependencyArtifact>emptyList(), getExcludeRules(dependency)));
    }

    private void addModuleDependency(ModuleDependency dependency, Set<MavenDependencyInternal> dependencies) {
        dependencies.add(new DefaultMavenDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), dependency.getArtifacts(), getExcludeRules(dependency)));
    }

    private static Set<ExcludeRule> getExcludeRules(ModuleDependency dependency) {
        return dependency.isTransitive() ? dependency.getExcludeRules() : EXCLUDE_ALL_RULE;
    }

    public MavenArtifact artifact(Object source) {
        return mavenArtifacts.artifact(source);
    }

    public MavenArtifact artifact(Object source, Action<? super MavenArtifact> config) {
        return mavenArtifacts.artifact(source, config);
    }

    public MavenArtifactSet getArtifacts() {
        return mavenArtifacts;
    }

    public void setArtifacts(Iterable<?> sources) {
        mavenArtifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    public String getGroupId() {
        return projectIdentity.getGroupId();
    }

    public void setGroupId(String groupId) {
        projectIdentity.setGroupId(groupId);
    }

    public String getArtifactId() {
        return projectIdentity.getArtifactId();
    }

    public void setArtifactId(String artifactId) {
        projectIdentity.setArtifactId(artifactId);
    }

    public String getVersion() {
        return projectIdentity.getVersion();
    }

    public void setVersion(String version) {
        projectIdentity.setVersion(version);
    }

    public FileCollection getPublishableFiles() {
        return new UnionFileCollection(mavenArtifacts.getFiles(), pomFile);
    }

    public MavenProjectIdentity getMavenProjectIdentity() {
        return projectIdentity;
    }

    public Set<MavenDependencyInternal> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public Set<MavenDependencyInternal> getApiDependencies() {
        return apiDependencies;
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        return new MavenNormalizedPublication(name, getPomFile(), projectIdentity, getArtifacts(), determineMainArtifact());
    }

    private File getPomFile() {
        if (pomFile == null) {
            throw new IllegalStateException("pomFile not set for publication");
        }
        return pomFile.getSingleFile();
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

    // TODO:DAZ The module metadata file should never be in the artifacts collection to start with
    private Set<MavenArtifact> getUnclassifiedArtifactsWithExtension() {
        return CollectionUtils.filter(mavenArtifacts, new Spec<MavenArtifact>() {
            public boolean isSatisfiedBy(MavenArtifact mavenArtifact) {
                return hasNoClassifier(mavenArtifact) && hasExtension(mavenArtifact) && isNotModuleMetadata(mavenArtifact);
            }
        });
    }

    private boolean isNotModuleMetadata(MavenArtifact artifact) {
        return !artifact.getExtension().equals("module");
    }

    private boolean hasNoClassifier(MavenArtifact element) {
        return element.getClassifier() == null || element.getClassifier().length() == 0;
    }

    private boolean hasExtension(MavenArtifact element) {
        return element.getExtension() != null && element.getExtension().length() > 0;
    }

    public ModuleVersionIdentifier getCoordinates() {
        return new DefaultModuleVersionIdentifier(getGroupId(), getArtifactId(), getVersion());
    }

    public void publishWithOriginalFileName() {
        this.isPublishWithOriginalFileName = true;
    }

    @Override
    public boolean canPublishModuleMetadata() {
        if (getComponent() == null) {
            // Cannot yet publish module metadata without component
            return false;
        }
        if (getComponent() instanceof ComponentWithVariants) {
            // Always publish `ComponentWithVariants`
            return true;
        }
        return experimentalFeatures.isEnabled();
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

    /*
      When the artifacts declared in a component are modified for publishing (name/classifier/extension),
      then the Maven publication no longer represents the underlying java component.
      Instead of publishing incorrect metadata, we fail any attempt to publish the module metadata.

      In the long term, we will likely prevent any modification of artifacts added from a component.
      Instead, we will make it easier to modify the component(s) produced by a project, allowing the published
      metadata to accurately reflect the local component metadata.
     */
    private void checkThatArtifactIsPublishedUnmodified(PublishArtifact source) {
        for (MavenArtifact mavenArtifact : mavenArtifacts) {
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
