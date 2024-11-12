/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for {@link ArtifactSet} for nodes of a resolved graph.
 *
 * This cache contains ArtifactSets for the entire build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public class VariantArtifactSetCache {

    private static final ExcludeSpec EXCLUDE_NOTHING = new DefaultExcludeFactory().nothing();

    private final Map<Long, ArtifactSet> cache = new ConcurrentHashMap<>();

    /**
     * Caches the implicit artifact set for the given node of the given component.
     * <p>
     * This cache provides the _implicit_ artifact set for a variant -- the artifact set to
     * use when the dependency pointing to the variant does not modify the artifacts. This means
     * the attributes, capabilities, excludes, and requested artifacts are all empty.
     * <p>
     * Results are undefined if the component does not belong to the given node.
     *
     * @param component The component
     * @param variant The variant
     *
     * @return The resolved artifact set
     */
    public ArtifactSet getImplicitVariant(ComponentGraphResolveState component, VariantGraphResolveState variant) {

        // We use the variant's instance ID as the key here. This ID is unique for each variant in the build tree.
        // For variants derived from project components, this cache is quite efficient. We only have a single project
        // component for any given project in the build tree.

        // For external components, this cache is much less efficient. We create a new external component instance
        // (and thus variant instance) for each _repository_ instance. We can greatly improve the efficiency of this
        // cache by deduplicating repositories across projects.

        // For now, we limit this cache to be used for project components only.
        if (!(component instanceof LocalComponentGraphResolveState)) {
            return createImplicitVariant(component, variant);
        }

        // Check for a present value first without locking.
        Long key = variant.getInstanceId();
        ArtifactSet result = cache.get(key);
        if (result != null) {
            return result;
        }

        return cache.computeIfAbsent(key, id -> createImplicitVariant(component, variant));
    }

    private static VariantResolvingArtifactSet createImplicitVariant(ComponentGraphResolveState component, VariantGraphResolveState variant) {
        return new VariantResolvingArtifactSet(
            component,
            variant,
            ImmutableAttributes.EMPTY,
            Collections.emptyList(),
            EXCLUDE_NOTHING,
            Collections.emptySet()
        );
    }

}
