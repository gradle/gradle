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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.operations.BuildOperationProcessor;

import java.util.Map;
import java.util.Set;

public class FileDependencyCollectingGraphVisitor implements DependencyGraphVisitor {
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final BuildOperationProcessor buildOperationProcessor;
    private final SetMultimap<Long, FileDependencyArtifactSet> filesByNodeId = LinkedHashMultimap.create();
    private Map<FileCollectionDependency, FileDependencyArtifactSet> rootFiles;

    public FileDependencyCollectingGraphVisitor(ImmutableAttributesFactory immutableAttributesFactory, BuildOperationProcessor buildOperationProcessor) {
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    @Override
    public void start(DependencyGraphNode root) {
        Set<LocalFileDependencyMetadata> fileDependencies = ((LocalConfigurationMetadata) root.getMetadata()).getFiles();
        rootFiles = Maps.newLinkedHashMap();
        for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
            FileDependencyArtifactSet artifactSet = new FileDependencyArtifactSet(idGenerator.generateId(), fileDependency, immutableAttributesFactory);
            rootFiles.put(fileDependency.getSource(), artifactSet);
            filesByNodeId.put(root.getNodeId(), artifactSet);
        }
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        // If this node has an incoming transitive dependency, then include its file dependencies in the result. Otherwise ignore
        ConfigurationMetadata configurationMetadata = node.getMetadata();
        if (configurationMetadata instanceof LocalConfigurationMetadata) {
            LocalConfigurationMetadata localConfigurationMetadata = (LocalConfigurationMetadata) configurationMetadata;
            for (DependencyGraphEdge edge : node.getIncomingEdges()) {
                if (edge.isTransitive()) {
                    Set<LocalFileDependencyMetadata> fileDependencies = localConfigurationMetadata.getFiles();
                    for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
                        filesByNodeId.put(node.getNodeId(), new FileDependencyArtifactSet(idGenerator.generateId(), fileDependency, immutableAttributesFactory));
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void finish(DependencyGraphNode root) {
    }

    public VisitedFileDependencyResults complete() {
        return new DefaultVisitedFileDependencyResults(filesByNodeId, rootFiles, buildOperationProcessor);
    }
}
