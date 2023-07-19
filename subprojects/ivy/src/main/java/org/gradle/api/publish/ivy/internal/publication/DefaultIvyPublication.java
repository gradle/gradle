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

import com.google.common.collect.Streams;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.VersionMappingStrategy;
import org.gradle.api.publish.internal.CompositePublicationArtifactSet;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.InvalidIvyPublicationException;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyConfigurationContainer;
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec;
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet;
import org.gradle.api.publish.ivy.internal.artifact.DerivedIvyArtifact;
import org.gradle.api.publish.ivy.internal.artifact.SingleOutputTaskIvyArtifact;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency;
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.artifact.NormalizedIvyArtifact;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyPublicationCoordinates publicationCoordinates;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final TaskDependencyFactory taskDependencyFactory;
    private final ImmutableAttributesFactory immutableAttributesFactory;

    private final IvyModuleDescriptorSpecInternal descriptor;
    private final IvyConfigurationContainer configurations;
    private final DefaultIvyArtifactSet mainArtifacts;
    private final PublicationArtifactSet<IvyArtifact> metadataArtifacts;
    private final PublicationArtifactSet<IvyArtifact> derivedArtifacts;
    private final PublicationArtifactSet<IvyArtifact> publishableArtifacts;
    private final SetProperty<IvyArtifact> componentArtifacts;
    private final SetProperty<IvyConfiguration> componentConfigurations;
    private final Set<String> silencedVariants = new HashSet<>();
    private IvyArtifact ivyDescriptorArtifact;
    private TaskProvider<? extends Task> moduleDescriptorGenerator;
    private SingleOutputTaskIvyArtifact gradleModuleDescriptorArtifact;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;
    private boolean silenceAllPublicationWarnings;
    private boolean withBuildIdentifier;

    @Inject
    public DefaultIvyPublication(
        String name,
        Instantiator instantiator,
        ObjectFactory objectFactory,
        IvyPublicationCoordinates publicationCoordinates,
        NotationParser<Object, IvyArtifact> ivyArtifactNotationParser,
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
        this.publicationCoordinates = publicationCoordinates;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.versionMappingStrategy = versionMappingStrategy;
        this.taskDependencyFactory = taskDependencyFactory;

        IvyComponentParser ivyComponentParser = new IvyComponentParser(
            instantiator,
            platformSupport,
            projectDependencyResolver,
            ivyArtifactNotationParser,
            documentationRegistry,
            collectionCallbackActionDecorator
        );

        this.componentArtifacts = objectFactory.setProperty(IvyArtifact.class);
        this.componentArtifacts.convention(getComponent().map(ivyComponentParser::parseArtifacts));
        this.componentArtifacts.finalizeValueOnRead();

        this.componentConfigurations = objectFactory.setProperty(IvyConfiguration.class);
        this.componentConfigurations.convention(getComponent().map(ivyComponentParser::parseConfigurations));
        this.componentConfigurations.finalizeValueOnRead();

        this.mainArtifacts = instantiator.newInstance(DefaultIvyArtifactSet.class, name, ivyArtifactNotationParser, fileCollectionFactory, collectionCallbackActionDecorator);
        this.metadataArtifacts = new DefaultPublicationArtifactSet<>(IvyArtifact.class, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.derivedArtifacts = new DefaultPublicationArtifactSet<>(IvyArtifact.class, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.publishableArtifacts = new CompositePublicationArtifactSet<>(taskDependencyFactory, IvyArtifact.class, Cast.uncheckedCast(new PublicationArtifactSet<?>[]{mainArtifacts, metadataArtifacts, derivedArtifacts}));

        this.configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator, collectionCallbackActionDecorator);

        this.descriptor = objectFactory.newInstance(DefaultIvyModuleDescriptorSpec.class, objectFactory, publicationCoordinates);
        this.descriptor.setStatus(Module.DEFAULT_STATUS);
        this.descriptor.getWriteGradleMetadataMarker().set(providerFactory.provider(this::writeGradleMetadataMarker));
        this.descriptor.getGlobalExcludes().set(getComponent().map(ivyComponentParser::parseGlobalExcludes));
        this.descriptor.getConfigurations().set(this.configurations);
        this.descriptor.getArtifacts().set(providerFactory.provider(this::getArtifacts));
        this.descriptor.getDependencies().set(getComponent().<Set<IvyDependency>>map(component -> {
            IvyComponentParser.DependencyResult result = ivyComponentParser.parseDependencies(component, versionMappingStrategy);
            if (!silenceAllPublicationWarnings) {
                result.getWarnings().complete(getDisplayName() + " ivy metadata", silencedVariants);
            }
            return result.getDependencies();
        }));
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
        ivyDescriptorArtifact = new SingleOutputTaskIvyArtifact(descriptorGenerator, publicationCoordinates, "xml", "ivy", null, taskDependencyFactory);
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
        gradleModuleDescriptorArtifact = new SingleOutputTaskIvyArtifact(moduleDescriptorGenerator, publicationCoordinates, "module", "json", null, taskDependencyFactory);
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
        if (!artifactsOverridden && componentArtifacts.isPresent()) {
            mainArtifacts.addAll(componentArtifacts.get());
        }
        if (componentConfigurations.isPresent()) {
            configurations.addAll(componentConfigurations.get());
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
        return descriptor.getCoordinates().getOrganisation().get();
    }

    @Override
    public void setOrganisation(String organisation) {
        descriptor.getCoordinates().getOrganisation().set(organisation);
    }

    @Override
    public String getModule() {
        return descriptor.getCoordinates().getModule().get();
    }

    @Override
    public void setModule(String module) {
        descriptor.getCoordinates().getModule().set(module);
    }

    @Override
    public String getRevision() {
        return descriptor.getCoordinates().getRevision().get();
    }

    @Override
    public void setRevision(String revision) {
        descriptor.getCoordinates().getRevision().set(revision);
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
            getIvyDescriptorFile(),
            all
        );
    }

    private static <T> Set<T> linkedHashSetOf(Stream<T> stream) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        stream.forEach(set::add);
        return set;
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

    private String getArtifactFileName(@Nullable String classifier, String extension) {
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
