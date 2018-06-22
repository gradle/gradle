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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ValidatingArtifactsVisitor;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.util.List;

public class CompositeDependencyArtifactsVisitor implements ValidatingArtifactsVisitor {
    private final List<DependencyArtifactsVisitor> visitors;

    public CompositeDependencyArtifactsVisitor(List<DependencyArtifactsVisitor> visitors) {
        this.visitors = ImmutableList.copyOf(visitors);
    }

    @Override
    public void startArtifacts(RootGraphNode root) {
        for (DependencyArtifactsVisitor visitor : visitors) {
            visitor.startArtifacts(root);
        }
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        for (DependencyArtifactsVisitor visitor : visitors) {
            visitor.visitNode(node);
        }
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, int artifactSetId, ArtifactSet artifactSet) {
        for (DependencyArtifactsVisitor visitor : visitors) {
            visitor.visitArtifacts(from, fileDependency, artifactSetId, artifactSet);
        }
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, int artifactSetId, ArtifactSet artifacts) {
        for (DependencyArtifactsVisitor visitor : visitors) {
            visitor.visitArtifacts(from, to, artifactSetId, artifacts);
        }
    }

    @Override
    public void finishArtifacts() {
        for (DependencyArtifactsVisitor visitor : visitors) {
            visitor.finishArtifacts();
        }
    }

    @Override
    public void complete() {
        for (DependencyArtifactsVisitor visitor : visitors) {
            if (visitor instanceof ValidatingArtifactsVisitor) {
                ((ValidatingArtifactsVisitor) visitor).complete();
            }
        }
    }
}
