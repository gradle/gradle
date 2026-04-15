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

package org.gradle.api.internal.artifacts.result.artifact;

import org.gradle.api.artifacts.component.ComponentSelector;

/**
 * A directed edge in an {@link ArtifactGraph}.
 * <p>
 * Two subtypes may be present depending on the graph structure:
 * <ul>
 *     <li>{@link ResolvedArtifactEdge} if a dependency was successfully resolved.</li>
 *     <li>{@link UnresolvedArtifactEdge} if there was a failure to resolve the dependency.</li>
 * </ul>
 */
sealed public interface ArtifactEdge permits ResolvedArtifactEdge, UnresolvedArtifactEdge {

    /**
     * The selector responsible for selecting a component and variant for this edge.
     */
    ComponentSelector getRequested();

}
