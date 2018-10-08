/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

/**
 * Adapts a {@link ResolvedConfigurationBuilder}, which is responsible for assembling the resolved configuration result, to a {@link DependencyGraphVisitor} and
 * {@link DependencyArtifactsVisitor}.
 */
public class ResolvedConfigurationDependencyGraphVisitor implements DependencyArtifactsVisitor {
    private final ResolvedConfigurationBuilder builder;
    private RootGraphNode root;

    public ResolvedConfigurationDependencyGraphVisitor(ResolvedConfigurationBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        builder.newResolvedDependency(node);
        for (DependencyGraphEdge dependency : node.getIncomingEdges()) {
            if (dependency.getFrom() == root) {
                Dependency moduleDependency = dependency.getOriginalDependency();
                if (moduleDependency != null) {
                    builder.addFirstLevelDependency(moduleDependency, node);
                }
            }
        }
    }

    @Override
    public void startArtifacts(RootGraphNode root) {
        this.root = root;
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, int artifactSetId, ArtifactSet artifactSet) {
        builder.addNodeArtifacts(from, artifactSetId);
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, int artifactSetId, ArtifactSet artifacts) {
        builder.addChild(from, to, artifactSetId);
    }

    @Override
    public void finishArtifacts() {
        builder.done(root);
    }
}
