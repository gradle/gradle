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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.io.File;

class ChecksumEntry extends VerificationEntry {

    private final ChecksumKind checksumKind;
    private final int hashCode;

    // This field is mutable and is just a performance optimization
    // to avoid creating an extra map in the end, so it does NOT
    // participate in equals/hashcode
    private String checksum;

    ChecksumEntry(ModuleComponentArtifactIdentifier id, ArtifactVerificationOperation.ArtifactKind artifactKind, File file, ChecksumKind checksumKind) {
        super(id, artifactKind, file);
        this.checksumKind = checksumKind;
        this.hashCode = precomputeHashCode();
    }

    private int precomputeHashCode() {
        int result = id.hashCode();
        result = 31 * result + getFile().getName().hashCode();
        result = 31 * result + getArtifactKind().hashCode();
        result = 31 * result + checksumKind.hashCode();
        return result;
    }

    ChecksumKind getChecksumKind() {
        return checksumKind;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    @Override
    int getOrder() {
        return checksumKind.ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChecksumEntry that = (ChecksumEntry) o;

        if (!id.equals(that.id)) {
            return false;
        }
        if (!getArtifactKind().equals(that.getArtifactKind())) {
            return false;
        }
        if (!getFile().equals(that.getFile())) {
            return false;
        }
        return checksumKind == that.checksumKind;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
