/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resolve.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.component.model.VariantWithOverloadAttributes;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class DefaultArtifactSelector implements ArtifactSelector {
    private final List<OriginArtifactSelector> selectors;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    private final ResolvedVariantCache resolvedVariantCache;

    public DefaultArtifactSelector(List<OriginArtifactSelector> selectors, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory, ResolvedVariantCache resolvedVariantCache) {
        this.selectors = selectors;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.resolvedVariantCache = resolvedVariantCache;
    }

    @Override
    public ArtifactSet resolveArtifacts(LocalFileDependencyMetadata fileDependencyMetadata) {
        return new FileDependencyArtifactSet(fileDependencyMetadata, artifactTypeRegistry, calculatedValueContainerFactory);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Supplier<Set<? extends VariantResolveMetadata>> allVariants, Set<? extends VariantResolveMetadata> legacyVariants, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
        ModuleVersionIdentifier moduleVersionId = component.getModuleVersionId();
        ModuleSources sources = component.getSources();

        ImmutableSet<ResolvedVariant> legacyResolvedVariants = buildResolvedVariants(moduleVersionId, sources, legacyVariants, exclusions);
        ComponentArtifactResolveVariantState allResolvedVariants = () -> buildResolvedVariants(moduleVersionId, sources, allVariants.get(), exclusions);

        for (OriginArtifactSelector selector : selectors) {
            ArtifactSet artifacts = selector.resolveArtifacts(component, allResolvedVariants, legacyResolvedVariants, exclusions, overriddenAttributes);
            if (artifacts != null) {
                return artifacts;
            }
        }
        throw new IllegalStateException("No artifacts selected.");
    }

    private ImmutableSet<ResolvedVariant> buildResolvedVariants(ModuleVersionIdentifier moduleVersionId, ModuleSources sources, Set<? extends VariantResolveMetadata> allVariants, ExcludeSpec exclusions) {
        ImmutableSet.Builder<ResolvedVariant> resolvedVariantBuilder = ImmutableSet.builder();
        for (VariantResolveMetadata variant : allVariants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant.getIdentifier(), variant.asDescribable(), variant.getAttributes(), variant.getArtifacts(), withImplicitCapability(variant.getCapabilities(), moduleVersionId), exclusions, moduleVersionId, sources, resolvedVariantCache, variant.isEligibleForCaching());
            resolvedVariantBuilder.add(resolvedVariant);
        }
        return resolvedVariantBuilder.build();
    }

    private ResolvedVariant toResolvedVariant(VariantResolveMetadata.Identifier identifier,
                                              DisplayName displayName,
                                              ImmutableAttributes variantAttributes,
                                              ImmutableList<? extends ComponentArtifactMetadata> artifacts,
                                              ImmutableCapabilities capabilities,
                                              ExcludeSpec exclusions,
                                              ModuleVersionIdentifier ownerId,
                                              ModuleSources moduleSources,
                                              ResolvedVariantCache resolvedVariantCache,
                                              boolean eligibleForCaching) {
        // artifactsToResolve are those not excluded by their owning module
        List<? extends ComponentArtifactMetadata> artifactsToResolve = CollectionUtils.filter(artifacts,
                artifact -> !exclusions.excludesArtifact(ownerId.getModule(), artifact.getName())
        );

        boolean hasExcludedArtifact = artifactsToResolve.size() < artifacts.size();
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variantAttributes, artifacts);

        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            return ArtifactSetFactory.toResolvedVariant(null, displayName, attributes, artifactsToResolve, capabilities, ownerId, moduleSources, artifactResolver);
        } else if (!eligibleForCaching) {
            return ArtifactSetFactory.toResolvedVariant(identifier, displayName, attributes, artifactsToResolve, capabilities, ownerId, moduleSources, artifactResolver);
        } else {
            // This is a bit of a hack because we allow the artifactType registry to be different in every resolution scope.
            // This means it's not safe to assume a variant resolved in one consumer can be reused in another consumer with the same key.
            // Most of the time the artifactType registry has the same effect on the variant's attributes, but this isn't guaranteed.
            // It might be better to tighten this up by either requiring a single artifactType registry for the entire build or eliminating this feature
            // entirely.
            VariantWithOverloadAttributes key = new VariantWithOverloadAttributes(identifier, attributes);
            return resolvedVariantCache.computeIfAbsent(key, id -> ArtifactSetFactory.toResolvedVariant(identifier, displayName, attributes, artifactsToResolve, capabilities, ownerId, moduleSources, artifactResolver));
        }
    }

    private static ImmutableCapabilities withImplicitCapability(CapabilitiesMetadata capabilitiesMetadata, ModuleVersionIdentifier moduleVersionId) {
        // TODO: This doesn't seem right. We should know the capability of the variant before we get here instead of assuming that it's the same as the owner
        if (capabilitiesMetadata.getCapabilities().isEmpty()) {
            return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(moduleVersionId));
        } else if (capabilitiesMetadata instanceof ImmutableCapabilities) {
            return (ImmutableCapabilities) capabilitiesMetadata;
        } else {
            return ImmutableCapabilities.of(capabilitiesMetadata.getCapabilities());
        }
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes) {
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(component.getAttributes(), artifacts);
        return ArtifactSetFactory.adHocVariant(component.getId(), component.getModuleVersionId(), artifacts, component.getSources(), component.getAttributesSchema(), artifactResolver, attributes, overriddenAttributes);
    }
}
