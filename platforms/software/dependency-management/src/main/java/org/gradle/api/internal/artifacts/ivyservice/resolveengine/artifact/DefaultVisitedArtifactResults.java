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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ResolutionStrategy;

import java.util.ArrayList;
import java.util.List;

public class DefaultVisitedArtifactResults implements VisitedArtifactResults {

    // Index of the artifact set == the id of the artifact set
    private final List<ArtifactSet> artifactsById;

    public DefaultVisitedArtifactResults(ImmutableList<ArtifactSet> artifactsById) {
        this.artifactsById = artifactsById;
    }

    @Override
    public SelectedArtifactResults select(
        ArtifactSelectionServices consumerServices,
        ArtifactSelectionSpec spec,
        boolean lenient
    ) {
        List<ResolvedArtifactSet> resolvedArtifactSets = new ArrayList<>(artifactsById.size());
        for (ArtifactSet artifactSet : artifactsById) {
            ResolvedArtifactSet resolvedArtifacts = artifactSet.select(consumerServices, spec);
            if (!lenient || !(resolvedArtifacts instanceof UnavailableResolvedArtifactSet)) {
                resolvedArtifactSets.add(resolvedArtifacts);
            } else {
                resolvedArtifactSets.add(ResolvedArtifactSet.EMPTY);
            }
        }

        return new DefaultSelectedArtifactResults(spec.getSortOrder(), resolvedArtifactSets);
    }

    private static class DefaultSelectedArtifactResults implements SelectedArtifactResults {
        private final ResolvedArtifactSet allArtifacts;
        // Index of the artifact set == the id of the artifact set
        private final List<ResolvedArtifactSet> resolvedArtifactsById;

        DefaultSelectedArtifactResults(ResolutionStrategy.SortOrder sortOrder, List<ResolvedArtifactSet> resolvedArtifactsById) {
            this.resolvedArtifactsById = resolvedArtifactsById;
            if (sortOrder == ResolutionStrategy.SortOrder.DEPENDENCY_FIRST) {
                this.allArtifacts = CompositeResolvedArtifactSet.of(Lists.reverse(resolvedArtifactsById));
            } else {
                this.allArtifacts = CompositeResolvedArtifactSet.of(resolvedArtifactsById);
            }
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return allArtifacts;
        }

        @Override
        public ResolvedArtifactSet getArtifactsWithId(int id) {
            return resolvedArtifactsById.get(id);
        }
    }
}
