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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfigurationContainer;
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec;
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependencySet;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class DefaultIvyPublication implements IvyPublicationInternal {

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
    private final IvyModuleDescriptorSpecInternal descriptor;
    private final IvyPublicationIdentity publicationIdentity;
    private final IvyConfigurationContainer configurations;
    private final DefaultIvyArtifactSet ivyArtifacts;
    private final DefaultIvyDependencySet ivyDependencies;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final ExperimentalFeatures experimentalFeatures;
    private FileCollection ivyDescriptorFile;
    private FileCollection gradleModuleDescriptorFile;
    private SoftwareComponentInternal component;
    private boolean alias;

    public DefaultIvyPublication(
        String name, Instantiator instantiator, IvyPublicationIdentity publicationIdentity, NotationParser<Object, IvyArtifact> ivyArtifactNotationParser,
        ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
        ImmutableAttributesFactory immutableAttributesFactory, ExperimentalFeatures experimentalFeatures) {
        this.name = name;
        this.publicationIdentity = publicationIdentity;
        this.projectDependencyResolver = projectDependencyResolver;
        configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator);
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.experimentalFeatures = experimentalFeatures;
        ivyArtifacts = instantiator.newInstance(DefaultIvyArtifactSet.class, name, ivyArtifactNotationParser, fileCollectionFactory);
        ivyDependencies = instantiator.newInstance(DefaultIvyDependencySet.class);
        descriptor = instantiator.newInstance(DefaultIvyModuleDescriptorSpec.class, this);
    }

    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public SoftwareComponentInternal getComponent() {
        return component;
    }

    public IvyModuleDescriptorSpecInternal getDescriptor() {
        return descriptor;
    }

    public void setIvyDescriptorFile(FileCollection descriptorFile) {
        this.ivyDescriptorFile = descriptorFile;
    }

    public void setGradleModuleDescriptorFile(FileCollection descriptorFile) {
        this.gradleModuleDescriptorFile = descriptorFile;
    }

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

    public void from(SoftwareComponent component) {
        if (this.component != null) {
            throw new InvalidUserDataException(String.format("Ivy publication '%s' cannot include multiple components", name));
        }
        this.component = (SoftwareComponentInternal) component;

        configurations.maybeCreate("default");

        Set<PublishArtifact> seenArtifacts = Sets.newHashSet();
        Set<ModuleDependency> seenDependencies = Sets.newHashSet();
        for (UsageContext usageContext : getSortedUsageContexts()) {
            Usage usage = usageContext.getUsage();
            String conf = mapUsage(usage);
            configurations.maybeCreate(conf);
            configurations.getByName("default").extend(conf);

            for (PublishArtifact publishArtifact : usageContext.getArtifacts()) {
                if (!seenArtifacts.contains(publishArtifact)) {
                    seenArtifacts.add(publishArtifact);
                    artifact(publishArtifact).setConf(conf);
                }
            }

            for (ModuleDependency dependency : usageContext.getDependencies()) {
                if (seenDependencies.add(dependency)) {
                // TODO: When we support multiple components or configurable dependencies, we'll need to merge the confs of multiple dependencies with same id.
                    String confMapping = String.format("%s->%s", conf, dependency.getTargetConfiguration() == null ? Dependency.DEFAULT_CONFIGURATION : dependency.getTargetConfiguration());
                    if (dependency instanceof ProjectDependency) {
                        addProjectDependency((ProjectDependency) dependency, confMapping);
                    } else {
                        addExternalDependency((ExternalDependency) dependency, confMapping);
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

    private String mapUsage(Usage usage) {
        if (Usage.JAVA_API.equals(usage.getName())) {
            return "compile";
        }
        if (Usage.JAVA_RUNTIME.equals(usage.getName())) {
            return "runtime";
        }
        return usage.getName();
    }

    private void addProjectDependency(ProjectDependency dependency, String confMapping) {
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(dependency);
        ivyDependencies.add(new DefaultIvyDependency(
                identifier.getGroup(), identifier.getName(), identifier.getVersion(), confMapping, dependency.isTransitive(), Collections.<DependencyArtifact>emptyList(), dependency.getExcludeRules()));
    }

    private void addExternalDependency(ExternalDependency dependency, String confMapping) {
        ivyDependencies.add(new DefaultIvyDependency(dependency, confMapping));
    }

    public void configurations(Action<? super IvyConfigurationContainer> config) {
        config.execute(configurations);
    }

    public IvyConfigurationContainer getConfigurations() {
        return configurations;
    }

    public IvyArtifact artifact(Object source) {
        return ivyArtifacts.artifact(source);
    }

    public IvyArtifact artifact(Object source, Action<? super IvyArtifact> config) {
        return ivyArtifacts.artifact(source, config);
    }

    public void setArtifacts(Iterable<?> sources) {
        ivyArtifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    public DefaultIvyArtifactSet getArtifacts() {
        return ivyArtifacts;
    }

    public String getOrganisation() {
        return publicationIdentity.getOrganisation();
    }

    public void setOrganisation(String organisation) {
        publicationIdentity.setOrganisation(organisation);
    }

    public String getModule() {
        return publicationIdentity.getModule();
    }

    public void setModule(String module) {
        publicationIdentity.setModule(module);
    }

    public String getRevision() {
        return publicationIdentity.getRevision();
    }

    public void setRevision(String revision) {
        publicationIdentity.setRevision(revision);
    }

    public FileCollection getPublishableFiles() {
        return new UnionFileCollection(ivyArtifacts.getFiles(), ivyDescriptorFile, gradleModuleDescriptorFile);
    }

    public IvyPublicationIdentity getIdentity() {
        return publicationIdentity;
    }

    public Set<IvyDependencyInternal> getDependencies() {
        return ivyDependencies;
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        return new IvyNormalizedPublication(name, getIdentity(), assertDescriptorFile(ivyDescriptorFile), maybeGradleDescriptorFile(), ivyArtifacts);
    }

    private File maybeGradleDescriptorFile() {
        if (gradleModuleDescriptorFile == null) {
            // possible if experimental features are disabled
            return null;
        }
        return gradleModuleDescriptorFile.getSingleFile();
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

    private static File assertDescriptorFile(FileCollection ref) {
        if (ref == null) {
            throw new IllegalStateException("descriptorFile not set for publication");
        }
        return ref.getSingleFile();
    }

    public ModuleVersionIdentifier getCoordinates() {
        return new DefaultModuleVersionIdentifier(getOrganisation(), getModule(), getRevision());
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
}
