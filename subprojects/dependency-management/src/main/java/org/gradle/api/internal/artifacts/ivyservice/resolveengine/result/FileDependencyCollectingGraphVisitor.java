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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalFileDependencyBackedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class FileDependencyCollectingGraphVisitor implements DependencyGraphVisitor, VisitedFileDependencyResults, SelectedFileDependencyResults {
    private final SetMultimap<Long, ResolvedArtifactSet> filesByConfiguration = LinkedHashMultimap.create();
    private Map<FileCollectionDependency, ResolvedArtifactSet> rootFiles;

    @Override
    public void start(DependencyGraphNode root) {
        Set<LocalFileDependencyMetadata> fileDependencies = ((LocalConfigurationMetadata) root.getMetadata()).getFiles();
        rootFiles = Maps.newLinkedHashMap();
        for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
            LocalFileDependencyBackedArtifactSet artifacts = new LocalFileDependencyBackedArtifactSet(fileDependency);
            rootFiles.put(fileDependency.getSource(), artifacts);
            filesByConfiguration.put(root.getNodeId(), artifacts);
        }
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
    }

    @Override
    public void visitEdges(DependencyGraphNode resolvedConfiguration) {
        // If this node has an incoming transitive dependency, then include its file dependencies in the result. Otherwise ignore
        ConfigurationMetadata configurationMetadata = resolvedConfiguration.getMetadata();
        if (configurationMetadata instanceof LocalConfigurationMetadata) {
            LocalConfigurationMetadata localConfigurationMetadata = (LocalConfigurationMetadata) configurationMetadata;
            for (DependencyGraphEdge edge : resolvedConfiguration.getIncomingEdges()) {
                if (edge.isTransitive()) {
                    Set<LocalFileDependencyMetadata> fileDependencies = localConfigurationMetadata.getFiles();
                    for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
                        filesByConfiguration.put(resolvedConfiguration.getNodeId(), new LocalFileDependencyBackedArtifactSet(fileDependency));
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void finish(DependencyGraphNode root) {
    }

    @Override
    public SelectedFileDependencyResults select(Transformer<HasAttributes, Collection<? extends HasAttributes>> selector) {
        // Filter later
        return this;
    }

    @Override
    public Map<FileCollectionDependency, ResolvedArtifactSet> getFirstLevelFiles() {
        return rootFiles;
    }

    @Override
    public ResolvedArtifactSet getFiles(Long node) {
        return CompositeArtifactSet.of(filesByConfiguration.get(node));
    }

    @Override
    public ResolvedArtifactSet getFiles() {
        return CompositeArtifactSet.of(filesByConfiguration.values());
    }
}
