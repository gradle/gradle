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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.DisplayName;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultConfigurationPublications implements ConfigurationPublications {
    private final DisplayName displayName;
    private final PublishArtifactSet artifacts;
    private final PublishArtifactSetProvider allArtifacts;
    private final AttributeContainerInternal parentAttributes;
    private final AttributeContainerInternal attributes;
    private final Instantiator instantiator;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final FileCollectionFactory fileCollectionFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private NamedDomainObjectContainer<ConfigurationVariant> variants;
    private ConfigurationVariantFactory variantFactory;
    private List<Capability> capabilities;
    private boolean canCreate = true;

    public DefaultConfigurationPublications(DisplayName displayName,
                                            PublishArtifactSet artifacts,
                                            PublishArtifactSetProvider allArtifacts,
                                            AttributeContainerInternal parentAttributes,
                                            Instantiator instantiator,
                                            NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
                                            NotationParser<Object, Capability> capabilityNotationParser,
                                            FileCollectionFactory fileCollectionFactory,
                                            ImmutableAttributesFactory attributesFactory,
                                            DomainObjectCollectionFactory domainObjectCollectionFactory) {
        this.displayName = displayName;
        this.artifacts = artifacts;
        this.allArtifacts = allArtifacts;
        this.parentAttributes = parentAttributes;
        this.instantiator = instantiator;
        this.artifactNotationParser = artifactNotationParser;
        this.capabilityNotationParser = capabilityNotationParser;
        this.fileCollectionFactory = fileCollectionFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.attributes = attributesFactory.mutable(parentAttributes);
    }

    public void collectVariants(ConfigurationInternal.VariantVisitor visitor) {
        visitor.visitArtifacts(artifacts);
        PublishArtifactSet allArtifactSet = allArtifacts.getPublishArtifactSet();
        if (variants == null || variants.isEmpty() || !allArtifactSet.isEmpty()) {
            visitor.visitOwnVariant(displayName, attributes.asImmutable(), getCapabilities(), allArtifactSet);
        }
        if (variants != null) {
            for (DefaultVariant variant : variants.withType(DefaultVariant.class)) {
                variant.visit(visitor, getCapabilities());
            }
        }
    }

    public OutgoingVariant convertToOutgoingVariant() {
        return new OutgoingVariant() {
            @Override
            public DisplayName asDescribable() {
                return displayName;
            }

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
                PublishArtifactSet allArtifactSet = allArtifacts.getPublishArtifactSet();
                LeafOutgoingVariant leafOutgoingVariant = new LeafOutgoingVariant(displayName, attributes, allArtifactSet);
                if (variants == null || variants.isEmpty()) {
                    return Collections.singleton(leafOutgoingVariant);
                }
                boolean hasArtifacts = !allArtifactSet.isEmpty();
                Set<OutgoingVariant> result = Sets.newLinkedHashSetWithExpectedSize(hasArtifacts ? 1 + variants.size() : variants.size());
                if (hasArtifacts) {
                    result.add(leafOutgoingVariant);
                }
                for (DefaultVariant variant : variants.withType(DefaultVariant.class)) {
                    result.add(variant.convertToOutgoingVariant());
                }
                return result;
            }
        };
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public ConfigurationPublications attributes(Action<? super AttributeContainer> action) {
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

    @Override
    public NamedDomainObjectContainer<ConfigurationVariant> getVariants() {
        if (variants == null) {
            // Create variants container only as required
            variantFactory = new ConfigurationVariantFactory();
            variants = domainObjectCollectionFactory.newNamedDomainObjectContainer(ConfigurationVariant.class, variantFactory);
        }
        return variants;
    }

    @Override
    public void variants(Action<? super NamedDomainObjectContainer<ConfigurationVariant>> configureAction) {
        configureAction.execute(getVariants());
    }

    @Override
    public void capability(Object notation) {
        if (canCreate) {
            Capability descriptor = capabilityNotationParser.parseNotation(notation);
            if (capabilities == null) {
                capabilities = Lists.newArrayListWithExpectedSize(1); // it's rare that a component would declare more than 1 capability
            }
            capabilities.add(descriptor);
        } else {
            throw new InvalidUserCodeException("Cannot declare capability '" + notation + "' after dependency " + displayName + " has been resolved");
        }
    }

    @Override
    public Collection<? extends Capability> getCapabilities() {
        return capabilities == null ? Collections.emptyList() : ImmutableList.copyOf(capabilities);
    }

    void preventFromFurtherMutation() {
        canCreate = false;
        if (variants != null) {
            for (ConfigurationVariant variant : variants) {
                ((ConfigurationVariantInternal) variant).preventFurtherMutation();
            }
        }
    }

    private class ConfigurationVariantFactory implements NamedDomainObjectFactory<ConfigurationVariant> {
        @Override
        public ConfigurationVariant create(String name) {
            if (canCreate) {
                return instantiator.newInstance(DefaultVariant.class, displayName, name, parentAttributes, artifactNotationParser, fileCollectionFactory, attributesFactory, domainObjectCollectionFactory);
            } else {
                throw new InvalidUserCodeException("Cannot create variant '" + name + "' after dependency " + displayName + " has been resolved");
            }
        }
    }
}
