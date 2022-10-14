/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributeContainerWithErrorMessage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class DefaultVariant implements ConfigurationVariantInternal {
    private final Describable parentDisplayName;
    private final String name;
    private AttributeContainerInternal attributes;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final PublishArtifactSet artifacts;
    private Factory<List<PublishArtifact>> lazyArtifacts;
    @Nullable private String description;

    public DefaultVariant(Describable parentDisplayName,
                          String name,
                          AttributeContainerInternal parentAttributes,
                          NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
                          FileCollectionFactory fileCollectionFactory,
                          ImmutableAttributesFactory cache,
                          DomainObjectCollectionFactory domainObjectCollectionFactory,
                          TaskDependencyFactory taskDependencyFactory) {
        this.parentDisplayName = parentDisplayName;
        this.name = name;
        attributes = cache.mutable(parentAttributes);
        this.artifactNotationParser = artifactNotationParser;
        artifacts = new DefaultPublishArtifactSet(getAsDescribable(), domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact.class), fileCollectionFactory, taskDependencyFactory);
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public String getName() {
        return name;
    }

    public OutgoingVariant convertToOutgoingVariant() {
        return new LeafOutgoingVariant(getAsDescribable(), attributes, getArtifacts());
    }

    public void visit(ConfigurationInternal.VariantVisitor visitor, Collection<? extends Capability> capabilities) {
        visitor.visitChildVariant(name, getAsDescribable(), attributes.asImmutable(), capabilities, getArtifacts());
    }

    private DisplayName getAsDescribable() {
        return Describables.of(parentDisplayName, "variant", name);
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return attributes;
    }

    @Override
    public ConfigurationVariant attributes(Action<? super AttributeContainer> action) {
        action.execute(attributes);
        return this;
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        if (lazyArtifacts != null) {
            artifacts.addAll(lazyArtifacts.create());
            lazyArtifacts = null;
        }
        return artifacts;
    }

    @Override
    public void artifact(Object notation) {
        artifacts.add(artifactNotationParser.parseNotation(notation));
    }

    @Override
    public void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction) {
        ConfigurablePublishArtifact publishArtifact = artifactNotationParser.parseNotation(notation);
        artifacts.add(publishArtifact);
        configureAction.execute(publishArtifact);
    }

    @Override
    public String toString() {
        return getAsDescribable().getDisplayName();
    }

    @Override
    public void artifactsProvider(Factory<List<PublishArtifact>> artifacts) {
        this.lazyArtifacts = artifacts;
    }

    @Override
    public void preventFurtherMutation() {
        attributes = new ImmutableAttributeContainerWithErrorMessage(attributes.asImmutable(), parentDisplayName);
    }
}
