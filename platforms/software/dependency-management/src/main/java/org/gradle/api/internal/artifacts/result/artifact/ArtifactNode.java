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

import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;

import java.util.Collection;
import java.util.List;

/**
 * A node of an {@link ArtifactGraph}.
 * <p>
 * Each node in an artifact graph is derived from a node in the original dependency graph.
 * For each node in the original graph, there is exactly one artifact node in the derived
 * graph.
 * <p>
 * An artifact node exposes the artifacts produced by a given node in the original graph
 * and also exposes the edges to other artifact nodes that this node depends on.
 */
public interface ArtifactNode {

    /**
     * The attributes of the variant in the original graph that this artifact node
     * derives from.
     */
    AttributeContainer getAttributes();

    /**
     * The capabilities of the variant in the original graph that this artifact node
     * derives from.
     */
    Collection<? extends Capability> getCapabilities();

    /**
     * The edges to other nodes in the artifact graph that this node depends on.
     */
    List<? extends ArtifactEdge> getDependencies();

    /**
     * The artifacts produced by the variant in the original graph that this artifact node
     * derives from.
     * <p>
     * The returned artifact collection is lazy. Artifacts are not resolved until the
     * underling collection is queried.
     */
    ArtifactCollection getArtifacts();

    /**
     * The files produced by the variant in the original graph that this artifact node
     * derives from.
     * <p>
     * The returned file collection is lazy. Files are not resolved until the
     * underling collection is queried.
     */
    FileCollection getFiles();

}
