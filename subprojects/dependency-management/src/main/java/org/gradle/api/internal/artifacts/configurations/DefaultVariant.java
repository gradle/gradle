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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.Set;

public class DefaultVariant implements ConfigurationVariant {
    private final String name;
    private final AttributeContainerInternal attributes;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final PublishArtifactSet artifacts;

    public DefaultVariant(String name,
                          AttributeContainerInternal parentAttributes,
                          NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
                          FileCollectionFactory fileCollectionFactory,
                          ImmutableAttributesFactory cache) {
        this.name = name;
        attributes = new DefaultMutableAttributeContainer(cache, parentAttributes);
        this.artifactNotationParser = artifactNotationParser;
        artifacts = new DefaultPublishArtifactSet(name + " artifacts", new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact.class), fileCollectionFactory);
    }

    @Override
    public String getName() {
        return name;
    }

    public OutgoingVariant convertToOutgoingVariant() {
        return new OutgoingVariant() {
            @Override
            public AttributeContainerInternal getAttributes() {
                return attributes;
            }

            @Override
            public Set<? extends PublishArtifact> getArtifacts() {
                return artifacts;
            }

            @Override
            public Set<? extends OutgoingVariant> getChildren() {
                return ImmutableSet.of();
            }
        };
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
}
