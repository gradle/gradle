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
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DomainObjectCollectionInternal;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.DisplayName;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class DefaultConfigurationPublications implements ConfigurationPublications {

    // Parent state
    private final DisplayName displayName;
    private final PublishArtifactSetProvider allArtifacts;
    private final AttributeContainerInternal parentAttributes;

    // Services
    private final ObjectFactory objectFactory;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final FileCollectionFactory fileCollectionFactory;
    private final AttributesFactory attributesFactory;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final TaskDependencyFactory taskDependencyFactory;

    // Mutable state
    private final PublishArtifactSet artifacts;
    private final AttributeContainerInternal attributes;
    private NamedDomainObjectContainer<ConfigurationVariant> variants;
    private DomainObjectSet<Capability> capabilities;
    private Supplier<String> observationReason;

    @Inject
    public DefaultConfigurationPublications(
        DisplayName displayName,
        PublishArtifactSet artifacts,
        PublishArtifactSetProvider allArtifacts,
        AttributeContainerInternal parentAttributes,
        ObjectFactory objectFactory,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        FileCollectionFactory fileCollectionFactory,
        AttributesFactory attributesFactory,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this.displayName = displayName;
        this.artifacts = artifacts;
        this.allArtifacts = allArtifacts;
        this.parentAttributes = parentAttributes;
        this.objectFactory = objectFactory;
        this.artifactNotationParser = artifactNotationParser;
        this.capabilityNotationParser = capabilityNotationParser;
        this.fileCollectionFactory = fileCollectionFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.attributes = attributesFactory.mutable(parentAttributes);
    }

    public void collectVariants(ConfigurationInternal.VariantVisitor visitor) {
        PublishArtifactSet allArtifactSet = allArtifacts.getPublishArtifactSet();
        if (variants == null || variants.isEmpty() || !allArtifactSet.isEmpty()) {
            visitor.visitOwnVariant(displayName, attributes.asImmutable(), allArtifactSet);
        }
        if (variants != null) {
            for (ConfigurationVariantInternal variant : variants.withType(ConfigurationVariantInternal.class)) {
                visitor.visitChildVariant(variant.getName(), variant.getDisplayName(), variant.getAttributes().asImmutable(), variant.getArtifacts());
            }
        }
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
    public void artifacts(Provider<? extends Iterable<? extends Object>> provider) {
        artifacts.addAllLater(provider.map(iterable -> {
            List<PublishArtifact> results = new ArrayList<>();
            iterable.forEach(notation -> results.add(artifactNotationParser.parseNotation(notation)));
            return results;
        }));
    }

    @Override
    public void artifacts(Provider<? extends Iterable<? extends Object>> provider, Action<? super ConfigurablePublishArtifact> configureAction) {
        artifacts.addAllLater(provider.map(iterable -> {
            List<PublishArtifact> results = new ArrayList<>();
            iterable.forEach(notation -> {
                ConfigurablePublishArtifact artifact = artifactNotationParser.parseNotation(notation);
                configureAction.execute(artifact);
                results.add(artifact);
            });
            return results;
        }));
    }

    @Override
    public NamedDomainObjectContainer<ConfigurationVariant> getVariants() {
        if (variants == null) {
            // Create variants container only as required
            variants = domainObjectCollectionFactory.newNamedDomainObjectContainer(ConfigurationVariant.class, this::createVariant);
            ((DomainObjectCollectionInternal<?>) variants).beforeCollectionChanges(variantName -> {
                if (isObserved()) {
                    throw new InvalidUserCodeException("Cannot add secondary artifact set to " + displayName + " after " + observationReason.get() + ".");
                }
            });
        }
        return variants;
    }

    @Override
    public void variants(Action<? super NamedDomainObjectContainer<ConfigurationVariant>> configureAction) {
        configureAction.execute(getVariants());
    }

    @Override
    public void capability(Object notation) {
        if (isObserved()) {
            throw new InvalidUserCodeException("Cannot declare capability '" + notation + "' on " + displayName + " after " + observationReason.get() + ".");
        }

        if (capabilities == null) {
            capabilities = domainObjectCollectionFactory.newDomainObjectSet(Capability.class);
        }
        if (notation instanceof Provider) {
            capabilities.addLater(((Provider<?>) notation).map(capabilityNotationParser::parseNotation));
        } else {
            Capability descriptor = capabilityNotationParser.parseNotation(notation);
            capabilities.add(descriptor);
        }
    }

    @Override
    public Collection<? extends Capability> getCapabilities() {
        return capabilities == null ? Collections.emptyList() : ImmutableList.copyOf(capabilities);
    }

    public void preventFromFurtherMutation(Supplier<String> observationReason) {
        this.observationReason = observationReason;
        if (variants != null) {
            for (ConfigurationVariant variant : variants) {
                ((ConfigurationVariantInternal) variant).preventFurtherMutation();
            }
        }
    }

    private boolean isObserved() {
        return observationReason != null;
    }

    private DefaultVariant createVariant(String name) {
        return objectFactory.newInstance(
            DefaultVariant.class,
            displayName,
            name,
            parentAttributes,
            artifactNotationParser,
            fileCollectionFactory,
            attributesFactory,
            domainObjectCollectionFactory,
            taskDependencyFactory
        );
    }
}
