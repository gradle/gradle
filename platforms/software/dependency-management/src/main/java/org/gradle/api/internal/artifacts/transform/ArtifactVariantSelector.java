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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.GraphVariantSelector;

/**
 * Selects an artifact set from a set of candidate artifact sets. Each set of candidates artifacts is
 * provided by a variant in the dependency graph. Candidate artifact sets are selected based on
 * attribute matching.
 *
 * This class is intentionally named similarly to {@link GraphVariantSelector}, as it has a
 * similar purpose. While the graph selector selects between variants of a component, the
 * artifact selector selects between artifact sets of a variant.
 */
public interface ArtifactVariantSelector {

    /**
     * Selects a matching artifact set from a given set of candidate artifact sets.
     *
     * On failure, returns a set that forwards the failure to the {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor}.
     */
    ResolvedArtifactSet select(
        ResolvedVariantSet candidates,
        ImmutableAttributes requestAttributes,
        boolean allowNoMatchingVariants
    );

}
