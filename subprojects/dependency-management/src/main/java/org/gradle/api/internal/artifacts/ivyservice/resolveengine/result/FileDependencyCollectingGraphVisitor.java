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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileDependencyCollectingGraphVisitor implements DependencyGraphVisitor, VisitedFileDependencyResults {
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final SetMultimap<Long, ArtifactSet> filesByConfiguration = LinkedHashMultimap.create();
    private Map<FileCollectionDependency, ArtifactSet> rootFiles;

    public FileDependencyCollectingGraphVisitor(ImmutableAttributesFactory immutableAttributesFactory) {
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    @Override
    public void start(DependencyGraphNode root) {
        Set<LocalFileDependencyMetadata> fileDependencies = ((LocalConfigurationMetadata) root.getMetadata()).getFiles();
        rootFiles = Maps.newLinkedHashMap();
        for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
            FileDependencyArtifactSet artifactSet = new FileDependencyArtifactSet(idGenerator.generateId(), fileDependency, immutableAttributesFactory);
            rootFiles.put(fileDependency.getSource(), artifactSet);
            filesByConfiguration.put(root.getNodeId(), artifactSet);
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
                        filesByConfiguration.put(resolvedConfiguration.getNodeId(), new FileDependencyArtifactSet(idGenerator.generateId(), fileDependency, immutableAttributesFactory));
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
    public SelectedFileDependencyResults select(Spec<? super ComponentIdentifier> componentFilter, Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector) {
        // Wrap each file dependency in a set that performs variant selection and transformation
        // Also merge together the artifact sets for each configuration node
        ImmutableMap.Builder<Long, ResolvedArtifactSet> filesByConfigBuilder = ImmutableMap.builder();
        for (Long key : filesByConfiguration.keySet()) {
            Set<ArtifactSet> artifactsForConfiguration = filesByConfiguration.get(key);
            List<ResolvedArtifactSet> selectedArtifacts = new ArrayList<ResolvedArtifactSet>(artifactsForConfiguration.size());
            for (ArtifactSet artifactSet : artifactsForConfiguration) {
                selectedArtifacts.add(artifactSet.select(componentFilter, selector));
            }
            filesByConfigBuilder.put(key, CompositeArtifactSet.of(selectedArtifacts));
        }
        ImmutableMap<Long, ResolvedArtifactSet> filesByConfig = filesByConfigBuilder.build();

        ResolvedArtifactSet allFiles = CompositeArtifactSet.of(filesByConfig.values());

        ImmutableMap.Builder<FileCollectionDependency, ResolvedArtifactSet> rootFilesBuilder = ImmutableMap.builder();
        for (Map.Entry<FileCollectionDependency, ArtifactSet> entry : rootFiles.entrySet()) {
            rootFilesBuilder.put(entry.getKey(), entry.getValue().select(componentFilter, selector));
        }

        return new DefaultFileDependencyResults(rootFilesBuilder.build(), allFiles, filesByConfig);
    }

    private static class DefaultFileDependencyResults implements SelectedFileDependencyResults {
        private final Map<FileCollectionDependency, ResolvedArtifactSet> rootFiles;
        private final Map<Long, ResolvedArtifactSet> filesByConfiguration;
        private final ResolvedArtifactSet allArtifacts;

        DefaultFileDependencyResults(Map<FileCollectionDependency, ResolvedArtifactSet> rootFiles, ResolvedArtifactSet allArtifacts, Map<Long, ResolvedArtifactSet> filesByConfiguration) {
            this.rootFiles = rootFiles;
            this.allArtifacts = allArtifacts;
            this.filesByConfiguration = filesByConfiguration;
        }

        @Override
        public Map<FileCollectionDependency, ResolvedArtifactSet> getFirstLevelFiles() {
            return rootFiles;
        }

        @Override
        public ResolvedArtifactSet getArtifacts(long id) {
            ResolvedArtifactSet artifacts = filesByConfiguration.get(id);
            return artifacts == null ? ResolvedArtifactSet.EMPTY : artifacts;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return allArtifacts;
        }
    }
}
