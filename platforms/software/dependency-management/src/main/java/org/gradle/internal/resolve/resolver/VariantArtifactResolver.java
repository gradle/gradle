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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;

public interface VariantArtifactResolver {
    /**
     * Creates an adhoc resolved variant which resolves the provided artifacts of the component.
     */
    ResolvedVariant resolveAdhocVariant(ComponentArtifactResolveMetadata component, ImmutableList<? extends ComponentArtifactMetadata> artifacts);

    /**
     * Resolves the given variant metadata to its artifacts.
     */
    ResolvedVariant resolveVariant(ComponentArtifactResolveMetadata component, VariantResolveMetadata variant);

    /**
     * Applies the provided exclusions and resolves the given variant metadata to its artifacts.
     */
    ResolvedVariant resolveVariant(ComponentArtifactResolveMetadata component, VariantResolveMetadata variant, ExcludeSpec exclusions);
}
