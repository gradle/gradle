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
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.FreezableAttributeContainer;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;
import java.util.List;

public abstract class DefaultVariant implements ConfigurationVariantInternal {

    private final String name;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;

    private final DisplayName displayName;
    private final FreezableAttributeContainer attributes;
    private final PublishArtifactSet artifacts;

    private Factory<List<PublishArtifact>> lazyArtifacts;

    @Inject
    public DefaultVariant(
        Describable parentDisplayName,
        String name,
        AttributeContainerInternal parentAttributes,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        FileCollectionFactory fileCollectionFactory,
        AttributesFactory attributesFactory,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this.name = name;
        this.artifactNotationParser = artifactNotationParser;

        this.displayName = Describables.of(parentDisplayName, "variant", name);
        this.attributes = new FreezableAttributeContainer(attributesFactory.mutable(parentAttributes), displayName);
        this.artifacts = new DefaultPublishArtifactSet(displayName, domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact.class), fileCollectionFactory, taskDependencyFactory);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DisplayName getDisplayName() {
        return displayName;
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
        return getDisplayName().getDisplayName();
    }

    @Override
    public void artifactsProvider(Factory<List<PublishArtifact>> artifacts) {
        this.lazyArtifacts = artifacts;
    }

    @Override
    public void preventFurtherMutation() {
        attributes.freeze();
    }
}
