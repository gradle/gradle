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

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Set;

/**
 * State that is used for artifact resolution based on a variant that is selected during graph resolution.
 *
 * <p>Instances of this type are located using {@link VariantGraphResolveState}.</p>
 */
public interface VariantArtifactResolveState {

    /**
     * Get the set of artifacts from this variant matching the provided {@link IvyArtifactName}s.
     *
     * This is used to resolve artifacts declared as part of a dependency.
     */
    ImmutableList<ComponentArtifactMetadata> getAdhocArtifacts(List<IvyArtifactName> dependencyArtifacts);

    /**
     * The artifact sets provided by this variant.
     * <p>
     * Each variant in a graph can expose a number of different artifact sets. Each
     * artifact set should contain the same content, but may be transformed in some way.
     * For example, zipped and unzipped versions of the same content.
     */
    Set<? extends VariantResolveMetadata> getArtifactVariants();

}
