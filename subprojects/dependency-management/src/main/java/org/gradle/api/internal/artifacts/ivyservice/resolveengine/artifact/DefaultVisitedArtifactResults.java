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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;

public class DefaultVisitedArtifactResults implements VisitedArtifactsResults {
    private final Map<Long, ArtifactSet> artifactsById;
    private final Set<Long> buildableArtifacts;

    public DefaultVisitedArtifactResults(Map<Long, ArtifactSet> artifactsById, Set<Long> buildableArtifacts) {
        this.artifactsById = artifactsById;
        this.buildableArtifacts = buildableArtifacts;
    }

    @Override
    public SelectedArtifactResults select(Spec<? super ComponentIdentifier> componentFilter, Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector) {
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

        return new DefaultSelectedArtifactResults(CompositeArtifactSet.of(allArtifactSets), resolvedArtifactsById.build());
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
        public ResolvedArtifactSet getArtifacts(long id) {
            return resolvedArtifactsById.get(id);
        }
    }
}
