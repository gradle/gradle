/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;

public class ImmutableArtifactVerificationMetadata implements ArtifactVerificationMetadata {
    private final String artifactName;
    private final List<Checksum> checksums;
    private final Set<String> trustedPgpKeys;
    private final Set<IgnoredKey> ignoredPgpKeys;
    private final int hashCode;

    public ImmutableArtifactVerificationMetadata(String artifactName, List<Checksum> checksums, Set<String> trustedPgpKeys, Set<IgnoredKey> ignoredPgpKeys) {
        this.artifactName = artifactName;
        this.checksums = ImmutableList.copyOf(checksums);
        this.trustedPgpKeys = ImmutableSet.copyOf(trustedPgpKeys);
        this.ignoredPgpKeys = ImmutableSet.copyOf(ignoredPgpKeys);
        this.hashCode = computeHashCode();
    }

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    @Override
    public List<Checksum> getChecksums() {
        return checksums;
    }

    @Override
    public Set<String> getTrustedPgpKeys() {
        return trustedPgpKeys;
    }

    @Override
    public Set<IgnoredKey> getIgnoredPgpKeys() {
        return ignoredPgpKeys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableArtifactVerificationMetadata that = (ImmutableArtifactVerificationMetadata) o;

        if (!artifactName.equals(that.artifactName)) {
            return false;
        }
        if (!checksums.equals(that.checksums)) {
            return false;
        }
        if (!ignoredPgpKeys.equals(that.ignoredPgpKeys)) {
            return false;
        }
        return trustedPgpKeys.equals(that.trustedPgpKeys);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int result = artifactName.hashCode();
        result = 31 * result + checksums.hashCode();
        result = 31 * result + trustedPgpKeys.hashCode();
        result = 31 * result + ignoredPgpKeys.hashCode();
        return result;
    }
}
