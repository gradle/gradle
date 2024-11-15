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
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.DisplayName;
import org.gradle.internal.FinalizableValue;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultConfigurationPublications implements ConfigurationPublications, FinalizableValue {
    private final DisplayName displayName;
    private final ConfigurationInternal owner;
    private final PublishArtifactSetProvider allArtifacts;

    // Services
    private final Instantiator instantiator;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final FileCollectionFactory fileCollectionFactory;
    private final AttributesFactory attributesFactory;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final TaskDependencyFactory taskDependencyFactory;

    // Mutable State
    private final AttributeContainerInternal attributes;
    private NamedDomainObjectContainer<ConfigurationVariant> variants;
    private ConfigurationVariantFactory variantFactory;
    private DomainObjectSet<Capability> capabilities;
    private boolean canCreate = true;

    public DefaultConfigurationPublications(
        DisplayName displayName,
        ConfigurationInternal owner,
        PublishArtifactSetProvider allArtifacts,
        Instantiator instantiator,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        FileCollectionFactory fileCollectionFactory,
        AttributesFactory attributesFactory,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this.displayName = displayName;
        this.owner = owner;
        this.allArtifacts = allArtifacts;

        this.attributes = attributesFactory.mutable(owner.getAttributes());

        // Services
        this.instantiator = instantiator;
        this.artifactNotationParser = artifactNotationParser;
        this.capabilityNotationParser = capabilityNotationParser;
        this.fileCollectionFactory = fileCollectionFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    public void collectVariants(ConfigurationInternal.VariantVisitor visitor) {

        // Do not nag about deprecation when collecting the artifacts for the variant metadata.
        // We will nag later on if variant is actually consumed.
        PublishArtifactSet allArtifactSet = DeprecationLogger.whileDisabled(allArtifacts::getPublishArtifactSet);

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
        return owner.getArtifacts();
    }

    @Override
    public void artifact(Object notation) {
        owner.getArtifacts().add(artifactNotationParser.parseNotation(notation));
    }

    @Override
    public void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction) {
        ConfigurablePublishArtifact publishArtifact = artifactNotationParser.parseNotation(notation);
        owner.getArtifacts().add(publishArtifact);
        configureAction.execute(publishArtifact);
    }

    @Override
    public void artifacts(Provider<? extends Iterable<? extends Object>> provider) {
        owner.getArtifacts().addAllLater(provider.map(iterable -> {
            List<PublishArtifact> results = new ArrayList<>();
            iterable.forEach(notation -> results.add(artifactNotationParser.parseNotation(notation)));
            return results;
        }));
    }

    @Override
    public void artifacts(Provider<? extends Iterable<? extends Object>> provider, Action<? super ConfigurablePublishArtifact> configureAction) {
        owner.getArtifacts().addAllLater(provider.map(iterable -> {
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

            if (owner.isDeprecatedForConsumption()) {
                DeprecationLogger.deprecateBehaviour("Adding variants to " + owner.getDisplayName() + ".")
                    .withContext(owner.getDisplayName() + " is deprecated for consumption. Artifacts and variants should not be added to non-consumable configurations.")
                    .willBecomeAnErrorInGradle9()
                    .withUpgradeGuideSection(8, "deprecated_configuration_usage")
                    .nagUser();
            }

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
            if (capabilities == null) {
                capabilities = domainObjectCollectionFactory.newDomainObjectSet(Capability.class);
            }
            if (notation instanceof Provider) {
                capabilities.addLater(((Provider<?>) notation).map(capabilityNotationParser::parseNotation));
            } else {
                Capability descriptor = capabilityNotationParser.parseNotation(notation);
                capabilities.add(descriptor);
            }
        } else {
            throw new InvalidUserCodeException("Cannot declare capability '" + notation + "' after dependency " + displayName + " has been resolved");
        }
    }

    @Override
    public Collection<? extends Capability> getCapabilities() {
        return capabilities == null ? Collections.emptyList() : ImmutableList.copyOf(capabilities);
    }

    @Override
    public void preventFromFurtherMutation() {
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
                return instantiator.newInstance(
                    DefaultVariant.class, displayName, name, owner.getAttributes(), artifactNotationParser, fileCollectionFactory, attributesFactory, domainObjectCollectionFactory, taskDependencyFactory
                );
            } else {
                throw new InvalidUserCodeException("Cannot create variant '" + name + "' after dependency " + displayName + " has been resolved");
            }
        }
    }
}
