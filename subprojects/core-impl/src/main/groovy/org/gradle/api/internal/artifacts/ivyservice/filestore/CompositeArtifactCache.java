/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.filestore;

import java.util.LinkedList;
import java.util.List;

public class CompositeArtifactCache<C> implements ArtifactCache<C> {

    private final List<ArtifactCache<C>> composites;

    public CompositeArtifactCache(List<ArtifactCache<C>> composites) {
        this.composites = composites;
    }

    public CachedArtifactCandidates findCandidates(C criterion) {
        List<CachedArtifact> cachedArtifacts = new LinkedList<CachedArtifact>();
        for (ArtifactCache<C> cache : composites) {
            cachedArtifacts.addAll(cache.findCandidates(criterion).getAll());
        }
        return new DefaultCachedArtifactCandidates(cachedArtifacts);
    }
}
