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

import org.gradle.api.artifacts.ResolvedArtifact;

import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newLinkedHashSet;

public class DefaultResolvedArtifactResults implements ResolvedArtifactResults {
    // Transient state: held between resolving graph and resolving actual artifacts
    private Map<Long, ArtifactSet> artifactSets = newLinkedHashMap();

    // Artifact State : held for the life of a build
    private Set<ResolvedArtifact> artifacts;
    private Map<Long, Set<ResolvedArtifact>> resolvedArtifactsById;

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        assertArtifactsResolved();
        return newLinkedHashSet(artifacts);
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts(long id) {
        assertArtifactsResolved();
        Set<ResolvedArtifact> a = resolvedArtifactsById.get(id);
        assert a != null : "Unable to find artifacts for id: " + id;
        return a;
    }

    public void addArtifactSet(ArtifactSet artifactSet) {
        artifactSets.put(artifactSet.getId(), artifactSet);
    }

    public void resolveNow() {
        if (artifacts == null) {
            artifacts = newLinkedHashSet();
            resolvedArtifactsById = newLinkedHashMap();
            for (Map.Entry<Long, ArtifactSet> entry : artifactSets.entrySet()) {
                Set<ResolvedArtifact> resolvedArtifacts = entry.getValue().getArtifacts();
                artifacts.addAll(resolvedArtifacts);
                resolvedArtifactsById.put(entry.getKey(), resolvedArtifacts);
            }

            // Release ResolvedArtifactSet instances so we're not holding onto state
            artifactSets = null;
        }
    }

    private void assertArtifactsResolved() {
        if (artifacts == null) {
            throw new IllegalStateException("Cannot access artifacts before they are explicitly resolved.");
        }
    }
}
