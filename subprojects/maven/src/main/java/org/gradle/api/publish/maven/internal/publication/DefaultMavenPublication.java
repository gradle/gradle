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

import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils;
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
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.AbstractMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;
import org.gradle.api.publish.maven.internal.artifact.DerivedMavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenPublicationCoordinates;
import org.gradle.api.publish.maven.internal.validation.MavenPublicationErrorChecker;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public abstract class DefaultMavenPublication implements MavenPublicationInternal {

    private final String name;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final TaskDependencyFactory taskDependencyFactory;

    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final MavenPomInternal pom;
    private final DefaultMavenArtifactSet mainArtifacts;
    private final PublicationArtifactSet<MavenArtifact> metadataArtifacts;
    private final PublicationArtifactSet<MavenArtifact> derivedArtifacts;
    private final PublicationArtifactSet<MavenArtifact> publishableArtifacts;
    private final SetProperty<MavenArtifact> componentArtifacts;
    private final Set<String> silencedVariants = new HashSet<>();
    private MavenArtifact pomArtifact;
    private SingleOutputTaskMavenArtifact moduleMetadataArtifact;
    private TaskProvider<? extends Task> moduleDescriptorGenerator;
    private boolean isPublishWithOriginalFileName;
    private boolean alias;
    private boolean populated;
    private boolean artifactsOverridden;
    private boolean silenceAllPublicationWarnings;
    private boolean withBuildIdentifier;

    @Inject
    public DefaultMavenPublication(
        String name,
        DependencyMetaDataProvider dependencyMetaDataProvider,
        NotationParser<Object, MavenArtifact> mavenArtifactParser,
        ObjectFactory objectFactory,
        FileCollectionFactory fileCollectionFactory,
        ImmutableAttributesFactory immutableAttributesFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator,
        VersionMappingStrategyInternal versionMappingStrategy,
        TaskDependencyFactory taskDependencyFactory,
        ProviderFactory providerFactory
    ) {
        this.name = name;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.versionMappingStrategy = versionMappingStrategy;
        this.taskDependencyFactory = taskDependencyFactory;

        MavenComponentParser mavenComponentParser = objectFactory.newInstance(MavenComponentParser.class, mavenArtifactParser);

        this.componentArtifacts = objectFactory.setProperty(MavenArtifact.class);
        this.componentArtifacts.convention(getComponent().map(mavenComponentParser::parseArtifacts));
        this.componentArtifacts.finalizeValueOnRead();

        this.mainArtifacts = objectFactory.newInstance(DefaultMavenArtifactSet.class, name, mavenArtifactParser, fileCollectionFactory, collectionCallbackActionDecorator);
        this.metadataArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "metadata artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.derivedArtifacts = new DefaultPublicationArtifactSet<>(MavenArtifact.class, "derived artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
        this.publishableArtifacts = new CompositePublicationArtifactSet<>(taskDependencyFactory, MavenArtifact.class, Cast.uncheckedCast(new PublicationArtifactSet<?>[]{mainArtifacts, metadataArtifacts, derivedArtifacts}));

        this.pom = objectFactory.newInstance(DefaultMavenPom.class, objectFactory);
        this.pom.getWriteGradleMetadataMarker().set(providerFactory.provider(this::writeGradleMetadataMarker));
        this.pom.getPackagingProperty().convention(providerFactory.provider(this::determinePackagingFromArtifacts));
        this.pom.getDependencies().set(getComponent().map(component -> {
            MavenComponentParser.ParsedDependencyResult result = mavenComponentParser.parseDependencies(component, versionMappingStrategy, getCoordinates());
            if (!silenceAllPublicationWarnings) {
                result.getWarnings().complete(getDisplayName() + " pom metadata", silencedVariants);
            }
            return result.getDependencies();
        }));

        Module module = dependencyMetaDataProvider.getModule();
        MavenPublicationCoordinates coordinates = pom.getCoordinates();
        coordinates.getGroupId().convention(providerFactory.provider(module::getGroup));
        coordinates.getArtifactId().convention(providerFactory.provider(module::getName));
        coordinates.getVersion().convention(providerFactory.provider(module::getVersion));
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
        if (!artifactsOverridden && componentArtifacts.isPresent()) {
            mainArtifacts.addAll(componentArtifacts.get());
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
        return pom.getCoordinates().getGroupId().get();
    }

    @Override
    public void setGroupId(String groupId) {
        pom.getCoordinates().getGroupId().set(groupId);
    }

    @Override
    public String getArtifactId() {
        return pom.getCoordinates().getArtifactId().get();
    }

    @Override
    public void setArtifactId(String artifactId) {
        pom.getCoordinates().getArtifactId().set(artifactId);
    }

    @Override
    public String getVersion() {
        return pom.getCoordinates().getVersion().get();
    }

    @Override
    public void setVersion(String version) {
        pom.getCoordinates().getVersion().set(version);
    }

    @Override
    public void versionMapping(Action<? super VersionMappingStrategy> configureAction) {
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
            pom.getCoordinates(),
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
        String version = pom.getCoordinates().getVersion().get();
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
