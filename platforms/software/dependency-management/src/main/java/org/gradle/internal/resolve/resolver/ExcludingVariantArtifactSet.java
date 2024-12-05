/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A {@link ResolvedVariant} that applies artifact exclusions to a delegate {@link ResolvedVariant}.
 */
public class ExcludingVariantArtifactSet implements ResolvedVariant, VariantResolveMetadata.Identifier {

    private final ResolvedVariant delegate;
    private final ModuleIdentifier moduleId;
    private final ExcludeSpec exclusions;

    private final VariantResolveMetadata.Identifier id;

    public ExcludingVariantArtifactSet(ResolvedVariant delegate, ModuleIdentifier moduleId, ExcludeSpec exclusions) {
        this.delegate = delegate;
        this.moduleId = moduleId;
        this.exclusions = exclusions;

        this.id = new ExcludingIdentifier(delegate.getIdentifier(), moduleId, exclusions);
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(delegate.asDescribable(), exclusions);
    }

    @Nullable
    @Override
    public VariantResolveMetadata.Identifier getIdentifier() {
        return id;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return delegate.getCapabilities();
    }

    @Override
    public ResolvedArtifactSet getArtifacts() {
        ResolvedArtifactSet artifacts = delegate.getArtifacts();
        return new FilteringResolvedArtifactSet(artifacts, this::include);
    }

    private boolean include(ResolvableArtifact artifact) {
        return !exclusions.excludesArtifact(moduleId, artifact.getArtifactName());
    }

    private static class ExcludingIdentifier implements VariantResolveMetadata.Identifier {
        private final VariantResolveMetadata.Identifier identifier;
        private final ModuleIdentifier moduleId;
        private final ExcludeSpec exclusions;

        public ExcludingIdentifier(
            @Nullable VariantResolveMetadata.Identifier identifier,
            ModuleIdentifier moduleId,
            ExcludeSpec exclusions
        ) {
            this.identifier = identifier;
            this.moduleId = moduleId;
            this.exclusions = exclusions;
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(identifier);
            result = 31 * result + moduleId.hashCode();
            result = 31 * result + exclusions.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            ExcludingIdentifier other = (ExcludingIdentifier) obj;
            return areIdsEqual(identifier, other.identifier) &&
                moduleId.equals(other.moduleId) &&
                exclusions.equals(other.exclusions);
        }

        private static boolean areIdsEqual(
            @Nullable VariantResolveMetadata.Identifier id1,
            @Nullable VariantResolveMetadata.Identifier id2
        ) {
            // Artifact sets without ID are adhoc.
            // We cannot compare them by ID so assume they are not equal.
            if (id1 == null || id2 == null) {
                return false;
            }

            return id1.equals(id2);
        }
    }
}
