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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ImmutableArtifactVerificationMetadata implements ArtifactVerificationMetadata {
    private final String artifactName;
    private final Map<ChecksumKind, String> checksums;

    public ImmutableArtifactVerificationMetadata(String artifactName, Map<ChecksumKind, String> checksums) {
        this.artifactName = artifactName;
        this.checksums = ImmutableMap.copyOf(checksums);
    }

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    @Override
    public Map<ChecksumKind, String> getChecksums() {
        return checksums;
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
        return checksums.equals(that.checksums);
    }

    @Override
    public int hashCode() {
        int result = artifactName.hashCode();
        result = 31 * result + checksums.hashCode();
        return result;
    }
}
