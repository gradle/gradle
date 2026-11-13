/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>Dependency resolution scopes (project configurations and detached resolvers) each create their own
 * {@link TransformedVariantFactory} and would otherwise rebuild identical transformed artifact sets per scope.
 *
 * We cache them here across scopes. Keys are content-based: they incorporate the component,
 * the source variant, the target attributes, and a token of the transform chain built from each step's
 * secondary input hash (which folds in the implementation class, its classloader hierarchy, and the isolated
 * parameter values) and its from/to attributes.
 *
 * Transform steps must be isolated before a key is computed. The capabilities and source variant id captured by the value are from the keyed component and variant metadata,
 * so they need no key ingredient of their own.
 *
 * <p>The resolved source artifacts ({@link ArtifactIdentities}) is identity-based. External artifacts are deduplicated by the build-tree-scoped per-repository
 * resolved-artifact caches. So scopes resolving the same artifact from the same repository see the same {@link ResolvableArtifact} instances, while a
 * same-GAV module served by a different repository yields different instances and must not share a transformed set.
 *
 * <p>Chains that require upstream dependencies also key on the per-scope dependencies resolver, so such
 * entries never match across scopes — the same behavior as the per-scope factory caches.
 */
@ServiceScope(Scope.BuildTree.class)
public class SharedTransformedVariantCache {

    private final ConcurrentMap<SharedVariantKey, ResolvedArtifactSet> variants = new ConcurrentHashMap<>();

    /**
     * Get the transformed artifact set associated with the key, if present.
     *
     * @return null if the corresponding value is not present in the cache
     */
    @Nullable
    public ResolvedArtifactSet get(SharedVariantKey key) {
        return variants.get(key);
    }

    /**
     * Associate the given transformed artifact set with the key if no value is present.
     *
     * @return the existing value, or null if the given value was inserted
     */
    @Nullable
    public ResolvedArtifactSet putIfAbsent(SharedVariantKey key, ResolvedArtifactSet value) {
        return variants.putIfAbsent(key, value);
    }

    /**
     * A cache key for a transformed external variant. Mostly content-based
     */
    public static class SharedVariantKey {
        private final ComponentIdentifier componentIdentifier;
        private final VariantResolveMetadata.Identifier sourceVariant;
        private final ArtifactIdentities sourceArtifacts;
        private final ImmutableAttributes target;
        private final ImmutableList<TransformStepToken> chainToken;

        public SharedVariantKey(
            ComponentIdentifier componentIdentifier,
            VariantResolveMetadata.Identifier sourceVariant,
            ArtifactIdentities sourceArtifacts,
            ImmutableAttributes target,
            ImmutableList<TransformStepToken> chainToken
        ) {
            this.componentIdentifier = componentIdentifier;
            this.sourceVariant = sourceVariant;
            this.sourceArtifacts = sourceArtifacts;
            this.target = target;
            this.chainToken = chainToken;
        }

        @Override
        public int hashCode() {
            int result = componentIdentifier.hashCode();
            result = 31 * result + sourceVariant.hashCode();
            result = 31 * result + sourceArtifacts.hashCode();
            result = 31 * result + target.hashCode();
            result = 31 * result + chainToken.hashCode();
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
            SharedVariantKey other = (SharedVariantKey) obj;
            return componentIdentifier.equals(other.componentIdentifier)
                && sourceVariant.equals(other.sourceVariant)
                && sourceArtifacts.equals(other.sourceArtifacts)
                && target.equals(other.target)
                && chainToken.equals(other.chainToken);
        }
    }

    /**
     * A key for a variant transformed by a chain that requires upstream dependencies.
     * The dependencies resolver is per resolution scope, so these keys never match across scopes.
     */
    public static class SharedVariantWithUpstreamDependenciesKey extends SharedVariantKey {
        private final TransformUpstreamDependenciesResolver dependenciesResolver;

        public SharedVariantWithUpstreamDependenciesKey(
            ComponentIdentifier componentIdentifier,
            VariantResolveMetadata.Identifier sourceVariant,
            ArtifactIdentities sourceArtifacts,
            ImmutableAttributes target,
            ImmutableList<TransformStepToken> chainToken,
            TransformUpstreamDependenciesResolver dependenciesResolver
        ) {
            super(componentIdentifier, sourceVariant, sourceArtifacts, target, chainToken);
            this.dependenciesResolver = dependenciesResolver;
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + dependenciesResolver.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            SharedVariantWithUpstreamDependenciesKey other = (SharedVariantWithUpstreamDependenciesKey) obj;
            return dependenciesResolver.equals(other.dependenciesResolver);
        }
    }

    /**
     * The identity of the resolved source artifacts of a variant, compared by reference.
     * See the class documentation for why this is identity-based and sufficient.
     */
    public static class ArtifactIdentities {
        private final ImmutableList<ResolvableArtifact> artifacts;

        public ArtifactIdentities(List<ResolvableArtifact> artifacts) {
            this.artifacts = ImmutableList.copyOf(artifacts);
        }

        @Override
        public int hashCode() {
            int result = 1;
            for (ResolvableArtifact artifact : artifacts) {
                result = 31 * result + System.identityHashCode(artifact);
            }
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
            ArtifactIdentities other = (ArtifactIdentities) obj;
            if (artifacts.size() != other.artifacts.size()) {
                return false;
            }
            for (int i = 0; i < artifacts.size(); i++) {
                if (artifacts.get(i) != other.artifacts.get(i)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The content identity of a single transform step: the secondary input hash of the isolated transform
     * (implementation class, classloader hierarchy, and parameter value fingerprints) plus the from/to
     * attributes of the step.
     */
    public static class TransformStepToken {
        private final HashCode secondaryInputHash;
        private final ImmutableAttributes fromAttributes;
        private final ImmutableAttributes toAttributes;

        public TransformStepToken(Transform transform) {
            this.secondaryInputHash = transform.getSecondaryInputHash();
            this.fromAttributes = transform.getFromAttributes();
            this.toAttributes = transform.getToAttributes();
        }

        @Override
        public int hashCode() {
            int result = secondaryInputHash.hashCode();
            result = 31 * result + fromAttributes.hashCode();
            result = 31 * result + toAttributes.hashCode();
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
            TransformStepToken other = (TransformStepToken) obj;
            return secondaryInputHash.equals(other.secondaryInputHash)
                && fromAttributes.equals(other.fromAttributes)
                && toAttributes.equals(other.toAttributes);
        }
    }
}
