/*
 * Copyright 2026 the original author or authors.
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

public class CompositeArtifactSet implements ArtifactSet {

    private final ImmutableList<ArtifactSet> artifactSets;

    private CompositeArtifactSet(ImmutableList<ArtifactSet> artifactSets) {
        this.artifactSets = artifactSets;
    }

    public static ArtifactSet of(ImmutableList<ArtifactSet> sets) {
        if (sets.isEmpty()) {
            return EMPTY;
        }
        if (sets.size() == 1) {
            return sets.get(0);
        }
        return new CompositeArtifactSet(sets);
    }

    @Override
    public ResolvedArtifactSet select(
        ArtifactSelectionServices consumerServices,
        ArtifactSelectionSpec spec
    ) {
        ImmutableList.Builder<ResolvedArtifactSet> builder = ImmutableList.builder();
        for (ArtifactSet artifactSet : artifactSets) {
            builder.add(artifactSet.select(consumerServices, spec));
        }
        return CompositeResolvedArtifactSet.of(builder.build());
    }

}
