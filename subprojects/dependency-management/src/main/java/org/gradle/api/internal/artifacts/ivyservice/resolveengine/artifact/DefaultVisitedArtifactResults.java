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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.specs.Spec;

import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.EMPTY;

public class DefaultVisitedArtifactResults implements VisitedArtifactsResults {
    private final Map<Long, ArtifactSet> artifactsById;
    private final Set<Long> buildableArtifacts;

    public DefaultVisitedArtifactResults(Map<Long, ArtifactSet> artifactsById, Set<Long> buildableArtifacts) {
        this.artifactsById = artifactsById;
        this.buildableArtifacts = buildableArtifacts;
    }

    @Override
    public SelectedArtifactResults select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        Set<ResolvedArtifactSet> allArtifactSets = newLinkedHashSet();
        ImmutableMap.Builder<Long, ResolvedArtifactSet> resolvedArtifactsById = ImmutableMap.builder();

        for (Map.Entry<Long, ArtifactSet> entry : artifactsById.entrySet()) {
            ArtifactSet artifactSet = entry.getValue();
            long id = entry.getKey();
            ResolvedArtifactSet resolvedArtifacts = artifactSet.select(componentFilter, selector);
            if (!buildableArtifacts.contains(id)) {
                resolvedArtifacts = NoBuildDependenciesArtifactSet.of(resolvedArtifacts);
            }
            allArtifactSets.add(resolvedArtifacts);
            resolvedArtifactsById.put(id, resolvedArtifacts);
        }

        if (allArtifactSets.isEmpty()) {
            return NoArtifactResults.INSTANCE;
        }

        ResolvedArtifactSet composite = CompositeArtifactSet.of(allArtifactSets);
        return new DefaultSelectedArtifactResults(composite, resolvedArtifactsById.build());
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
        private final Map<Long, ResolvedArtifactSet> resolvedArtifactsById;

        DefaultSelectedArtifactResults(ResolvedArtifactSet allArtifacts, Map<Long, ResolvedArtifactSet> resolvedArtifactsById) {
            this.allArtifacts = allArtifacts;
            this.resolvedArtifactsById = resolvedArtifactsById;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return allArtifacts;
        }

        @Override
        public ResolvedArtifactSet getArtifactsForNode(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolvedArtifactSet getArtifactsWithId(long id) {
            return resolvedArtifactsById.get(id);
        }
    }
}
