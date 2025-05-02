/*
 * Copyright 2015 the original author or authors.
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

/**
 * Represents a container of artifacts, each corresponding to a single node in the graph.
 *
 * Instances are retained during the lifetime of a build, so should avoid retaining unnecessary state.
 */
public interface ArtifactSet {
    /**
     * Selects the artifacts of this set that meet the given criteria.
     * Implementation should be eager where possible, so that selection happens
     * immediately, but may be lazy.
     */
    ResolvedArtifactSet select(
        ArtifactSelectionServices consumerServices,
        ArtifactSelectionSpec spec
    );
}
