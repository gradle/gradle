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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.model.GraphVariantSelector;

/**
 * Selects artifacts from a set of resolved variants. This can but does not necessarily require an additional
 * round of attribute matching to select a variant containing artifacts.
 *
 * This class is intentionally named similarly to {@link GraphVariantSelector}, as it has a
 * similar purpose.  An instance of {@link ResolutionFailureHandler} should be provided
 * to allow the caller to handle failures in a consistent way - all matching failures should be reported via
 * calls to that instance.
 */
public interface ArtifactVariantSelector {

    /**
     * Selects matching artifacts from a given set of candidates.
     *
     * On failure, returns a set that forwards the failure to the {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor}.
     */
    ResolvedArtifactSet select(ResolvedVariantSet candidates, ImmutableAttributes requestAttributes, boolean allowNoMatchingVariants, ResolvedArtifactTransformer resolvedArtifactTransformer);

    interface ResolvedArtifactTransformer {
        ResolvedArtifactSet asTransformed(
            ResolvedVariant sourceVariant,
            VariantDefinition variantDefinition,
            TransformUpstreamDependenciesResolver dependenciesResolver,
            TransformedVariantFactory transformedVariantFactory
        );
    }
}
