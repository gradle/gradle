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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

public interface DependencyArtifactsVisitor {
    /**
     * Visits a node in the graph. All nodes are visited prior to visiting the edges
     */
    default void visitNode(DependencyGraphNode node) {}

    /**
     * Visits the artifacts introduced by a particular edge in the graph. Called for every edge in the graph.
     * The nodes are visited in consumer-first order.
     */
    default void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, int artifactSetId, ArtifactSet artifacts) {}

    /**
     * Visits the artifacts introduce by a particular node in the graph. Called *zero or more* times for each node.
     * Currently local files are treated differently to other dependencies.
     * The nodes are visited in consumer-first order
     */
    default void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, int artifactSetId, ArtifactSet artifactSet) {}

    /**
     * Completes visiting.
     */
    default void finishArtifacts(RootGraphNode root) {}
}
