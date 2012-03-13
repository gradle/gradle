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

public interface ExternalArtifactCache {
    CachedArtifactCandidates findCandidates(ArtifactRevisionId artifactId);

    interface CachedArtifactCandidates {
        ArtifactRevisionId getArtifactId();

        /**
         * Indicates whether there is any chance that a match may be found by sha1.
         *
         * This allows clients to avoid obtaining the sha1 if it's not going to be useful.
         *
         * @return Whether there's any chance a match can be found by sha1.
         */
        boolean isMightFindBySha1();

        /**
         * Finds the best/first cached artifact within the candidates that matches the given sha1
         *
         * @param sha1 The sha1 to use to search
         * @return A cached artifact if there is one matching the checksum, otherwise null
         */
        CachedArtifact findBySha1(HashValue sha1);
    }
}
