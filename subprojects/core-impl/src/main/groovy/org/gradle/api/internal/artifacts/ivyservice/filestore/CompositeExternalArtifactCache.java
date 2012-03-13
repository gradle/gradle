/*
 * Copyright 2011 the original author or authors.
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

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.util.hash.HashValue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CompositeExternalArtifactCache implements ExternalArtifactCache {
    private final List<ExternalArtifactCache> delegates = new ArrayList<ExternalArtifactCache>();
    
    void addExternalArtifactCache(ExternalArtifactCache externalArtifactCache) {
        delegates.add(externalArtifactCache);
    }
    
    public CachedArtifactCandidates findCandidates(final ArtifactRevisionId artifactId) {
        final List<CachedArtifactCandidates> allCandidates = new LinkedList<CachedArtifactCandidates>();
        
        for (ExternalArtifactCache delegate : delegates) {
            allCandidates.add(delegate.findCandidates(artifactId));
        }

        return new CachedArtifactCandidates() {
            public ArtifactRevisionId getArtifactId() {
                return artifactId;
            }

            public boolean isMightFindBySha1() {
                for (CachedArtifactCandidates candidates : allCandidates) {
                    if (candidates.isMightFindBySha1()) {
                        return true;
                    }
                }

                return false;
            }

            public CachedArtifact findBySha1(HashValue sha1) {
                CachedArtifact found;
                for (CachedArtifactCandidates candidates : allCandidates) {
                    found = candidates.findBySha1(sha1);
                    if (found != null) {
                        return found;
                    }
                }

                return null;
            }
        };
    }
}
