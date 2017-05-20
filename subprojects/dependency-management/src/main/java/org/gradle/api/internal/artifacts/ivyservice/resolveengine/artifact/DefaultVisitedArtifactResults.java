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

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.specs.Spec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.EMPTY;

public class DefaultVisitedArtifactResults implements VisitedArtifactsResults {
    private final ResolutionStrategy.SortOrder sortOrder;
    private final Map<Long, Set<Integer>> artifactsByNodeId;
    // Index of the artifact set == the id of the artifact set
    private final List<ArtifactSet> artifactsById;

    public DefaultVisitedArtifactResults(ResolutionStrategy.SortOrder sortOrder, Map<Long, Set<Integer>> artifactsByNodeId, List<ArtifactSet> artifactsById) {
        this.sortOrder = sortOrder;
        this.artifactsByNodeId = artifactsByNodeId;
        this.artifactsById = artifactsById;
    }

    @Override
    public SelectedArtifactResults select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        if (artifactsById.isEmpty()) {
            return NoArtifactResults.INSTANCE;
        }

        List<ResolvedArtifactSet> resolvedArtifactsById = new ArrayList<ResolvedArtifactSet>(artifactsById.size());
        for (ArtifactSet artifactSet : artifactsById) {
            ResolvedArtifactSet resolvedArtifacts = artifactSet.select(componentFilter, selector);
            resolvedArtifactsById.add(resolvedArtifacts);
        }

        Collection<ResolvedArtifactSet> allArtifactSets = newLinkedHashSet();
        for (Set<Integer> artifactSets : artifactsByNodeId.values()) {
            for (Integer artifactSetId : artifactSets) {
                allArtifactSets.add(resolvedArtifactsById.get(artifactSetId));
            }
        }
        if (sortOrder == ResolutionStrategy.SortOrder.DEPENDENCY_FIRST) {
            List<ResolvedArtifactSet> reversed = new ArrayList<ResolvedArtifactSet>(allArtifactSets.size());
            for (ResolvedArtifactSet artifactSet : allArtifactSets) {
                reversed.add(0, artifactSet);
            }
            allArtifactSets = reversed;
        }

        ResolvedArtifactSet composite = CompositeResolvedArtifactSet.of(allArtifactSets);
        return new DefaultSelectedArtifactResults(composite, resolvedArtifactsById, artifactsByNodeId);
    }

    private static class NoArtifactResults implements SelectedArtifactResults {

        private static final NoArtifactResults INSTANCE = new NoArtifactResults();

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return EMPTY;
        }

        @Override
        public ResolvedArtifactSet getArtifactsForNode(long id) {
            return EMPTY;
        }

        @Override
        public ResolvedArtifactSet getArtifactsWithId(long id) {
            return EMPTY;
        }
    }

    private static class DefaultSelectedArtifactResults implements SelectedArtifactResults {
        private final ResolvedArtifactSet allArtifacts;
        // Index of the artifact set == the id of the artifact set
        private final List<ResolvedArtifactSet> resolvedArtifactsById;
        private final Map<Long, Set<Integer>> artifactsByNodeId;

        DefaultSelectedArtifactResults(ResolvedArtifactSet allArtifacts, List<ResolvedArtifactSet> resolvedArtifactsById, Map<Long, Set<Integer>> artifactsByNodeId) {
            this.allArtifacts = allArtifacts;
            this.resolvedArtifactsById = resolvedArtifactsById;
            this.artifactsByNodeId = artifactsByNodeId;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return allArtifacts;
        }

        @Override
        public ResolvedArtifactSet getArtifactsForNode(long id) {
            Set<Integer> artifactSets = artifactsByNodeId.get(id);
            if (artifactSets == null || artifactSets.isEmpty()) {
                return EMPTY;
            }
            List<ResolvedArtifactSet> resolvedArtifactSets = new ArrayList<ResolvedArtifactSet>(artifactSets.size());
            for (Integer artifactSetId : artifactSets) {
                resolvedArtifactSets.add(resolvedArtifactsById.get(artifactSetId));
            }
            return CompositeResolvedArtifactSet.of(resolvedArtifactSets);
        }

        @Override
        public ResolvedArtifactSet getArtifactsWithId(long id) {
            return resolvedArtifactsById.get((int) id);
        }
    }
}
