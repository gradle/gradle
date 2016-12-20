/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;

/**
 * A container of the artifacts visited during graph traversal.
 */
public interface VisitedArtifactSet {
    /**
     * Creates a set that selects the artifacts from this set that match the given criteria. Implementations are lazy, so that the selection happens only when the contents are queried.
     *
     * Not every query is available on the value returned from this method. Details are progressively refined during resolution and more queries become available.
     *
     * @param dependencySpec Select only those artifacts reachable from first level dependencies that match the given spec.
     * @param requestedAttributes Select only those artifacts that match the provided attributes.
     * @param componentSpec Select only those artifacts source from components matching the given spec.
     */
    SelectedArtifactSet select(Spec<? super Dependency> dependencySpec, AttributeContainerInternal requestedAttributes, Spec<? super ComponentIdentifier> componentSpec);
}
