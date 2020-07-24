/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class DefaultTransformedVariantFactory implements TransformedVariantFactory {
    private final TransformationNodeRegistry transformationNodeRegistry;
    private final ConcurrentMap<Key, TransformedExternalArtifactSet> variants = new ConcurrentHashMap<>();

    public DefaultTransformedVariantFactory(TransformationNodeRegistry transformationNodeRegistry) {
        this.transformationNodeRegistry = transformationNodeRegistry;
    }

    @Override
    public ResolvedArtifactSet transformedExternalArtifacts(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, ImmutableAttributes target, Transformation transformation, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        ResolvedVariant.Identifier identifier = sourceVariant.getIdentifier();
        if (identifier == null) {
            // An ad hoc variant, do not cache the result
            return new TransformedExternalArtifactSet(componentIdentifier, sourceVariant.getArtifacts(), target, transformation, dependenciesResolverFactory, transformationNodeRegistry);
        }
        return variants.computeIfAbsent(new Key(identifier, target), key -> new TransformedExternalArtifactSet(componentIdentifier, sourceVariant.getArtifacts(), target, transformation, dependenciesResolverFactory, transformationNodeRegistry));
    }

    @Override
    public ResolvedArtifactSet transformedProjectArtifacts(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, ImmutableAttributes target, Transformation transformation, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        return new TransformedProjectArtifactSet(componentIdentifier, sourceVariant.getArtifacts(), target, transformation, dependenciesResolverFactory, transformationNodeRegistry);
    }

    private static class Key {
        final ResolvedVariant.Identifier variantIdentifier;
        final ImmutableAttributes targetVariant;

        public Key(ResolvedVariant.Identifier variantIdentifier, ImmutableAttributes targetVariant) {
            this.variantIdentifier = variantIdentifier;
            this.targetVariant = targetVariant;
        }

        @Override
        public int hashCode() {
            return variantIdentifier.hashCode() ^ targetVariant.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            Key other = (Key) obj;
            return variantIdentifier.equals(other.variantIdentifier) && targetVariant.equals(other.targetVariant);
        }
    }
}
