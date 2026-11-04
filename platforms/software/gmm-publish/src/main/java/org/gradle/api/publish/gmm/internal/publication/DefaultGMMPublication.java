/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.publish.gmm.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.gmm.artifact.GMMArtifact;
import org.gradle.api.publish.gmm.internal.GMMProjectIdentity;
import org.gradle.api.publish.internal.DefaultPublicationArtifactSet;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

/**
 * A publication that consists only of a Gradle Module Metadata file.
 * <p>
 * The metadata file references the component's artifacts by their absolute location; the artifacts themselves are not published.
 */
public abstract class DefaultGMMPublication implements GMMPublicationInternal {
    private final String name;
    private final GMMProjectIdentity projectIdentity;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final PublicationArtifactSet<GMMArtifact> publishableArtifacts;

    private @Nullable TaskProvider<? extends GenerateModuleMetadata> moduleDescriptorGenerator;
    private boolean alias;
    private boolean withBuildIdentifier;

    @Inject
    public DefaultGMMPublication(
        String name,
        GMMProjectIdentity projectIdentity,
        VersionMappingStrategyInternal versionMappingStrategy,
        FileCollectionFactory fileCollectionFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator
    ) {
        this.name = name;
        this.projectIdentity = projectIdentity;
        this.versionMappingStrategy = versionMappingStrategy;
        this.publishableArtifacts = new DefaultPublicationArtifactSet<>(GMMArtifact.class, "publishable artifacts for " + name, fileCollectionFactory, collectionCallbackActionDecorator);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("Gradle Module Metadata publication", name);
    }

    @Override
    public abstract Property<SoftwareComponentInternal> getComponent();

    @Override
    public void from(SoftwareComponent component) {
        if (getComponent().isPresent()) {
            throw new InvalidUserDataException(String.format("Gradle Module Metadata publication '%s' cannot include multiple components", name));
        }
        getComponent().set((SoftwareComponentInternal) component);
        getComponent().finalizeValue();
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
    public boolean isLegacy() {
        return false;
    }

    @Override
    public void setModuleDescriptorGenerator(TaskProvider<? extends GenerateModuleMetadata> moduleMetadataGenerator) {
        this.moduleDescriptorGenerator = moduleMetadataGenerator;
    }

    @Override
    public @Nullable TaskProvider<? extends GenerateModuleMetadata> getModuleDescriptorGenerator() {
        return moduleDescriptorGenerator;
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
    public ImmutableAttributes getAttributes() {
        return ImmutableAttributes.EMPTY;
    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return DefaultModuleVersionIdentifier.newId(projectIdentity.getGroupId().get(), projectIdentity.getArtifactId().get(), projectIdentity.getVersion().get());
    }

    @Override
    public <T> @Nullable T getCoordinates(Class<T> type) {
        if (type.isAssignableFrom(ModuleVersionIdentifier.class)) {
            return type.cast(getCoordinates());
        }
        return null;
    }

    @Override
    public PublishedFile getPublishedFile(PublishArtifact source) {
        String publishedUrl = source.getFile().toURI().toString();
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
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return versionMappingStrategy;
    }

    @Override
    public PublicationArtifactSet<GMMArtifact> getPublishableArtifacts() {
        return publishableArtifacts;
    }

    @Override
    public void allPublishableArtifacts(Action<? super GMMArtifact> action) {
        publishableArtifacts.all(action);
    }

    @Override
    public void whenPublishableArtifactRemoved(Action<? super GMMArtifact> action) {
        publishableArtifacts.whenObjectRemoved(action);
    }

    @Override
    public GMMArtifact addDerivedArtifact(GMMArtifact originalArtifact, DerivedArtifact file) {
        throw new UnsupportedOperationException("Derived artifacts, such as signatures, are not supported for " + getDisplayName());
    }

    @Override
    public void removeDerivedArtifact(GMMArtifact artifact) {
        // Derived artifacts are not supported, so there is nothing to remove
    }
}
