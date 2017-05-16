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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.util.Map;

public class FileDependencyCollectingGraphVisitor implements DependencyGraphVisitor {
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final SetMultimap<Long, FileDependencyArtifactSet> filesByNodeId = LinkedHashMultimap.create();
    private Map<FileCollectionDependency, FileDependencyArtifactSet> rootFiles;

    public FileDependencyCollectingGraphVisitor(ArtifactTypeRegistry artifactTypeRegistry) {
        this.artifactTypeRegistry = artifactTypeRegistry;
    }

    @Override
    public void start(DependencyGraphNode root) {
        rootFiles = Maps.newLinkedHashMap();
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        for (LocalFileDependencyMetadata fileDependency : node.getOutgoingFileEdges()) {
            FileDependencyArtifactSet artifactSet = new FileDependencyArtifactSet(idGenerator.generateId(), fileDependency, artifactTypeRegistry);
            if (node.isRoot()) {
                rootFiles.put(fileDependency.getSource(), artifactSet);
            }
            filesByNodeId.put(node.getNodeId(), artifactSet);
        }
    }

    @Override
    public void finish(DependencyGraphNode root) {
    }

    public VisitedFileDependencyResults complete() {
        return new DefaultVisitedFileDependencyResults(filesByNodeId, rootFiles);
    }
}
