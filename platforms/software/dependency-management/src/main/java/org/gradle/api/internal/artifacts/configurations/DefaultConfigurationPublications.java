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
import org.gradle.api.attributes.FallbackVariant;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DomainObjectCollectionInternal;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParser;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
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
    private final PublishArtifactNotationParser artifactNotationParser;
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
        PublishArtifactNotationParser artifactNotationParser,
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
        boolean secondaryVariantsExist = variants != null && !variants.isEmpty();

        ImmutableAttributes primaryAttrs = addFallbackIfNecessary(attributes.asImmutable(), true, allArtifactSet, secondaryVariantsExist);
        visitor.visitOwnVariant(displayName, primaryAttrs, allArtifactSet);

        if (secondaryVariantsExist) {
            for (ConfigurationVariantInternal variant : variants.withType(ConfigurationVariantInternal.class)) {
                ImmutableAttributes secondaryAttrs = addFallbackIfNecessary(variant.getAttributes().asImmutable(), false, allArtifactSet, secondaryVariantsExist);
                visitor.visitChildVariant(variant.getName(), variant.getDisplayName(), secondaryAttrs, variant.getArtifacts());
            }
        }
    }

    /**
     * Augments a variant's attributes with the {@link FallbackVariant#FALLBACK_VARIANT_ATTRIBUTE}
     * marker when the enclosing configuration is shaped as a "fallback configuration".
     * <p>
     * A configuration is a fallback configuration when its primary variant declares no
     * artifacts but the configuration defines one or more secondary variants. In that case
     * the primary is augmented with {@link FallbackVariant#TRUE} and each secondary with
     * {@link FallbackVariant#FALSE}, so that the default schema disambiguation rule prefers
     * the secondaries over the primary during variant selection. This method is invoked
     * once per variant of the configuration (primary and each secondary), always with the
     * same {@code primaryArtifacts} set, so the "is fallback?" decision is consistent across
     * all calls for a given configuration.
     * <p>
     * <strong>B1 constraint (static-empty):</strong> "no declared artifacts" is determined by
     * {@link PublishArtifactSet#isEmpty()} on the primary's set at the time this method is
     * called &mdash; that is, no {@link org.gradle.api.artifacts.PublishArtifact} has been
     * registered. Artifacts contributed by lazy providers that later evaluate to an empty
     * file set do NOT qualify the primary as a fallback; the lazy declaration counts as
     * "non-empty" here.
     * <p>
     * User-supplied attributes take precedence: if {@code attrs} already carries the
     * {@link FallbackVariant#FALLBACK_VARIANT_ATTRIBUTE} the value is preserved as-is and
     * the method returns {@code attrs} unchanged. This lets a build author opt into or out
     * of the marker explicitly on a per-variant basis.
     *
     * @param attrs the variant's attributes before augmentation
     * @param isPrimary {@code true} if {@code attrs} belong to the configuration's primary variant
     * @param primaryArtifacts the configuration's primary variant artifact set (same instance for every call within one configuration)
     * @param secondaryVariantsExist whether the enclosing configuration declares secondary variants
     * @return the input attributes, possibly augmented with {@link FallbackVariant#FALLBACK_VARIANT_ATTRIBUTE}
     */
    private ImmutableAttributes addFallbackIfNecessary(
        ImmutableAttributes attrs,
        boolean isPrimary,
        PublishArtifactSet primaryArtifacts,
        boolean secondaryVariantsExist
    ) {
        // Order matters: check `secondaryVariantsExist` first so we short-circuit before
        // calling isEmpty() on the artifact set. isEmpty() forces realization of any
        // lazy artifact providers, which we must avoid during variant computation.
        // TODO: A configuration that declares both secondary variants and a lazy artifact
        //  provider on its primary will still realize the provider here. A
        //  constant-time emptiness check on PublishArtifactSet would let us also skip that
        //  realization; see ElementSource.constantTimeIsEmpty().
        boolean configIsFallback = secondaryVariantsExist && primaryArtifacts.isEmpty();
        if (!configIsFallback) {
            return attrs;
        }
        if (attrs.findEntry(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE.getName()) != null) {
            return attrs;
        }
        String value = isPrimary ? FallbackVariant.TRUE : FallbackVariant.FALSE;
        return attributesFactory.concat(attrs, FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE, objectFactory.named(FallbackVariant.class, value));
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
