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
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.util.function.Function;

/**
 * A graph of nodes of a dependency graph and the artifacts they expose.
 * <p>
 * Dependency resolution proceeds in two phases: graph resolution and artifact resolution.
 * Graph resolution builds a graph of nodes using only dependency metadata, like Gradle
 * Module Metadata (GMM) and Maven POMs. The results of Graph resolution are observable
 * via a {@link org.gradle.api.artifacts.result.ResolutionResult}. Graph resolution does
 * not resolve any artifact corresponding to the nodes in the graph.
 * <p>
 * Artifact resolution builds on top of an existing graph, deriving artifacts from each
 * node in the source graph. For each node in the source graph, artifacts are selected
 * to produce a graph {@link ArtifactNode}s exposing the artifacts derived for that node.
 */
public interface ArtifactGraph {

    /**
     * The root node of the artifact graph. The result produced by this method is calculated
     * lazily. Graph resolution is not triggered until the underlying provider is queried.
     */
    Provider<ArtifactNode> getRoot();

    /**
     * Returns a collection containing all artifacts from all nodes in the graph that match
     * the given filter.
     */
    ArtifactCollection getArtifacts(Spec<ArtifactNode> filter);

    /**
     * Returns a collection containing all files from all nodes in the graph that match
     * the given filter.
     */
    FileCollection getFiles(Spec<ArtifactNode> filter);

    /**
     * Return a collection containing all artifacts from all nodes returned by the given selector.
     * <p>
     * The selector is given the root node of the graph. It may then walk the artifact graph
     * starting from the root and return the set of nodes whose artifacts should be included
     * in the result.
     * <p>
     * Calling this method does not trigger graph or artifact resolution until the underlying
     * collection is queried. Once queried, only artifacts from the selected nodes are resolved.
     */
    ArtifactCollection getArtifactsFromRoot(Function<ArtifactNode, Iterable<ArtifactNode>> selector);

    /**
     * Return a collection containing all files from all nodes returned by the given selector.
     * <p>
     * The selector is given the root node of the graph. It may then walk the artifact graph
     * starting from the root and return the set of nodes whose files should be included
     * in the result.
     * <p>
     * Calling this method does not trigger graph or artifact resolution until the underlying
     * collection is queried. Once queried, only files from the selected nodes are resolved.
     */
    FileCollection getFilesFromRoot(Function<ArtifactNode, Iterable<ArtifactNode>> selector);

}
