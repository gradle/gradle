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

package org.gradle.internal.component.model;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import java.util.List;
import java.util.Set;

/**
 * State that is used for artifact resolution based on a variant that is selected during graph resolution.
 *
 * <p>Instances of this type are located using {@link ComponentArtifactResolveState}.</p>
 */
public interface VariantArtifactResolveState {
    /**
     * Return the name of this variant.
     */
    String getName();

    /**
     * Resolve the artifacts specified by the given {@link IvyArtifactName}s.
     *
     * This is used to resolve artifacts declared as part of a dependency.
     *
     * <p>Note that this may be expensive, for example it may block waiting for access to the source project or for network or IO requests to the source repository.
     */
    ResolvedVariant resolveAdhocArtifacts(VariantArtifactResolver variantResolver, List<IvyArtifactName> dependencyArtifacts);

    /**
     * Get all artifact variants, or "sub-variants" of this variant.
     */
    Set<? extends VariantResolveMetadata> getArtifactVariants();
}
