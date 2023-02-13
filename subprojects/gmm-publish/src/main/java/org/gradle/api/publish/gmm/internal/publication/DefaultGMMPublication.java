/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.gmm.GMMArtifact;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * The default {@link Publication} of Gradle Module Metadata.
 *
 * @since 8.1
 */
@Incubating
public abstract class DefaultGMMPublication implements GMMPublicationInternal {
    private final String name;
    private final TaskDependencyFactory taskDependencyFactory;
    private final DocumentationRegistry documentationRegistry;

    private SoftwareComponentInternal component;

    @Inject
    public DefaultGMMPublication(
            String name,
            TaskDependencyFactory taskDependencyFactory,
            DocumentationRegistry documentationRegistry
    ) {
        this.name = name;
        this.taskDependencyFactory = taskDependencyFactory;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public SoftwareComponentInternal getComponent() {
        return component;
    }

    @Override
    public void from(SoftwareComponent component) {
        if (this.component != null) {
            throw new InvalidUserDataException(String.format("GMM publication '%s' cannot include multiple components", name));
        }
        this.component = (SoftwareComponentInternal) component;
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("Gradle Module Metadata publication", name);
    }

    @Override
    public boolean isAlias() {
        return false;
    }

    @Override
    public boolean isLegacy() {
        return false;
    }







    @Nullable
    @Override
    public <T> T getCoordinates(Class<T> type) {
        return null;
    }

    @Override
    public void withoutBuildIdentifier() {

    }

    @Override
    public void withBuildIdentifier() {

    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return null;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return null;
    }

    @Override
    public void setAlias(boolean alias) {

    }

    @Override
    public PublicationArtifactSet<GMMArtifact> getPublishableArtifacts() {
        return null;
    }

    @Override
    public void allPublishableArtifacts(Action<? super GMMArtifact> action) {

    }

    @Override
    public void whenPublishableArtifactRemoved(Action<? super GMMArtifact> action) {

    }

    @Nullable
    @Override
    public GMMArtifact addDerivedArtifact(GMMArtifact originalArtifact, DerivedArtifact file) {
        return null;
    }

    @Override
    public void removeDerivedArtifact(GMMArtifact artifact) {

    }

    @Override
    public PublishedFile getPublishedFile(PublishArtifact source) {
        return null;
    }

    @Override
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return null;
    }

    @Override
    public boolean isPublishBuildId() {
        return false;
    }

    @Override
    public void setModuleDescriptorGenerator(TaskProvider<? extends Task> moduleMetadataGenerator) {

    }
}
