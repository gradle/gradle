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

import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;
import org.gradle.util.hash.HashValue;

import java.util.*;

public class DefaultCachedArtifactCandidates implements CachedArtifactCandidates {

    private final List<CachedArtifact> cachedArtifacts;

    public DefaultCachedArtifactCandidates(List<CachedArtifact> cachedArtifacts) {
        this.cachedArtifacts = cachedArtifacts;
    }

    public boolean isEmpty() {
        return cachedArtifacts.isEmpty();
    }

    public CachedArtifact getMostRecent() {
        CachedArtifact mostRecent = null;
        for (CachedArtifact cachedArtifact : cachedArtifacts) {
            if (mostRecent == null || cachedArtifact.getLastModified() > mostRecent.getLastModified())  {
                mostRecent = cachedArtifact;
            }
        }

        return mostRecent;
    }

    public Iterator<CachedArtifact> iterator() {
        return cachedArtifacts.iterator();
    }

    public List<CachedArtifact> getAll() {
        return new ArrayList<CachedArtifact>(cachedArtifacts);
    }

    public CachedArtifact findByHashValue(final HashValue hashValue) {
        return CollectionUtils.findFirst(cachedArtifacts, new Spec<CachedArtifact>() {
            public boolean isSatisfiedBy(CachedArtifact element) {
                return element.getSha1().equals(hashValue);
            }
        });
    }
}
