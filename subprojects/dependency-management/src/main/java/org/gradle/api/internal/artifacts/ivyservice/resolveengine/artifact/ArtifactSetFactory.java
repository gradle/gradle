/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ArtifactSetFactory {
    public static ArtifactSet createFromVariantMetadata(ComponentIdentifier componentIdentifier,
                                                        ComponentArtifactResolveVariantState allVariants,
                                                        Set<ResolvedVariant> legacyVariants,
                                                        AttributesSchemaInternal schema,
                                                        ImmutableAttributes selectionAttributes
    ) {
        return new DefaultArtifactSet(componentIdentifier, schema, selectionAttributes, allVariants, legacyVariants);
    }
    public static ArtifactSet adHocVariant(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, Collection<? extends ComponentArtifactMetadata> artifacts, ModuleSources moduleSources, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, ImmutableAttributes variantAttributes, ImmutableAttributes selectionAttributes) {
        VariantResolveMetadata.Identifier identifier = null;
        if (artifacts.size() == 1) {
            identifier = new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId());
        }
        ResolvedVariant resolvedVariant = toResolvedVariant(identifier, Describables.of(componentIdentifier), variantAttributes, ImmutableList.copyOf(artifacts), ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(ownerId)), ownerId, moduleSources, artifactResolver);
        return new DefaultArtifactSet(componentIdentifier, schema, selectionAttributes, () -> Collections.singleton(resolvedVariant), Collections.singleton(resolvedVariant));
    }

    public static ResolvedVariant toResolvedVariant(@Nullable VariantResolveMetadata.Identifier identifier,
                                                    DisplayName displayName,
                                                    ImmutableAttributes variantAttributes,
                                                    List<? extends ComponentArtifactMetadata> artifacts,
                                                    CapabilitiesMetadata capabilitiesMetadata,
                                                    ModuleVersionIdentifier ownerId,
                                                    ModuleSources moduleSources,
                                                    ArtifactResolver artifactResolver
    ) {
        return ArtifactBackedResolvedVariant.create(identifier, displayName, variantAttributes, capabilitiesMetadata, supplyLazilyResolvedArtifacts(ownerId, moduleSources, artifacts, artifactResolver));
    }

    private static Supplier<Collection<? extends ResolvableArtifact>> supplyLazilyResolvedArtifacts(ModuleVersionIdentifier ownerId, ModuleSources moduleSources, List<? extends ComponentArtifactMetadata> artifacts, ArtifactResolver artifactResolver) {
        return () -> {
            ImmutableSet.Builder<ResolvableArtifact> resolvedArtifacts = ImmutableSet.builder();
            for (ComponentArtifactMetadata artifact : artifacts) {
                DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
                artifactResolver.resolveArtifact(ownerId, artifact, moduleSources, result);
                if (artifact.isOptionalArtifact()) {
                    try {
                        // probe if the artifact exists
                        result.getResult().getFile();
                    } catch (Exception e) {
                        // Optional artifact is not available
                        continue;
                    }
                }
                resolvedArtifacts.add(result.getResult());
            }
            return resolvedArtifacts.build();
        };
    }

    private static class SingleArtifactVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final ComponentArtifactIdentifier artifactIdentifier;

        public SingleArtifactVariantIdentifier(ComponentArtifactIdentifier artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public int hashCode() {
            return artifactIdentifier.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            SingleArtifactVariantIdentifier other = (SingleArtifactVariantIdentifier) obj;
            return artifactIdentifier.equals(other.artifactIdentifier);
        }
    }

}
