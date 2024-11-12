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
import org.gradle.api.internal.attributes.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.InMemoryCache;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.function.BiFunction;

/**
 * Cache for {@link ResolvedVariant} instances.
 *
 * This cache contains ResolvedVariants for the entire build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public class ResolvedVariantCache  {

    private final InMemoryCache<CacheKey, ResolvedVariant> cache;

    @Inject
    public ResolvedVariantCache(InMemoryCacheFactory cacheFactory) {
        this.cache = cacheFactory.createCache();
    }

    /**
     * Caches resolved variants created by the given function if the identifier is eligible for caching.
     *
     * @param identifier the identity of the variant to resolve. Used as part of a composite key.
     * @param artifactTypeRegistry the artifact type registry that maps the attributes of the variant. Used as part of a composite key.
     * @param mappingFunction function to create a {@link ResolvedVariant}
     *
     * @return the resolved variant created by the function or a cached instance, if available
     */
    public ResolvedVariant computeIfAbsent(
        VariantResolveMetadata.Identifier identifier,
        ImmutableArtifactTypeRegistry artifactTypeRegistry,
        BiFunction<VariantResolveMetadata.Identifier, ImmutableArtifactTypeRegistry, ? extends ResolvedVariant> mappingFunction
    ) {
        return cache.computeIfAbsent(new CacheKey(identifier, artifactTypeRegistry), key -> mappingFunction.apply(key.variantIdentifier, key.artifactTypeRegistry));
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
        private final VariantResolveMetadata.Identifier variantIdentifier;
        private final ImmutableArtifactTypeRegistry artifactTypeRegistry;
        private final int hashCode;

        public CacheKey(VariantResolveMetadata.Identifier variantIdentifier, ImmutableArtifactTypeRegistry artifactTypeRegistry) {
            this.variantIdentifier = variantIdentifier;
            this.artifactTypeRegistry = artifactTypeRegistry;
            this.hashCode = 31 * variantIdentifier.hashCode() + artifactTypeRegistry.hashCode();
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
