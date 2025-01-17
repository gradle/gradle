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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cache for {@link ResolvedVariant} instances.
 *
 * This cache contains ResolvedVariants for the entire build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public class ResolvedVariantCache  {

    private final Map<CacheKey, ResolvedVariant> cache = new ConcurrentHashMap<>();

    /**
     * Get the resolved variant associated with the key, if present.
     *
     * @return null if the corresponding value is not present in the cache
     */
    @Nullable
    public ResolvedVariant get(CacheKey key) {
        return cache.get(key);
    }

    /**
     * Caches resolved variants created by the given function.
     *
     * @return the resolved variant created by the function or a cached instance, if available
     */
    public ResolvedVariant computeIfAbsent(CacheKey key, Function<CacheKey, ResolvedVariant> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    /**
     * A cache key for the resolved variant cache that includes the artifact type registry that
     * transforms the attributes of the variant based on its artifacts. The registry is necessary here,
     * as the artifact type registry in each consuming project may be different, resulting in a
     * different computed attributes set for any given producer variant.
     * <p>
     * We key on the registry since the attribute transformation process is expensive. A registry
     * will always produce the same attributes for a given variant, so we are able to use the
     * variant and the registry as a composite key.
     */
    public static class CacheKey {
        final VariantResolveMetadata.Identifier variantIdentifier;
        final ImmutableArtifactTypeRegistry artifactTypeRegistry;
        private final int hashCode;

        public CacheKey(VariantResolveMetadata.Identifier variantIdentifier, ImmutableArtifactTypeRegistry artifactTypeRegistry) {
            this.variantIdentifier = variantIdentifier;
            this.artifactTypeRegistry = artifactTypeRegistry;
            this.hashCode = computeHashCode(variantIdentifier, artifactTypeRegistry);
        }

        private static int computeHashCode(
            VariantResolveMetadata.Identifier variantIdentifier,
            ImmutableArtifactTypeRegistry artifactTypeRegistry
        ) {
            return 31 * variantIdentifier.hashCode() + artifactTypeRegistry.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            return variantIdentifier.equals(other.variantIdentifier) &&
                // Artifact type registries are interned
                artifactTypeRegistry == other.artifactTypeRegistry;
        }
    }

}
