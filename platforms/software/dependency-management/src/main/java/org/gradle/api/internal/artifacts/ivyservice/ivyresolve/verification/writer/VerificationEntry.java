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
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.io.File;
import java.util.Comparator;

abstract class VerificationEntry implements Comparable<VerificationEntry> {
    private static final Comparator<VerificationEntry> ENTRY_COMPARATOR = Comparator.comparing(VerificationEntry::getGroup)
        .thenComparing(VerificationEntry::getModule)
        .thenComparing(VerificationEntry::getVersion)
        .thenComparing(VerificationEntry::getFile)
        .thenComparing(VerificationEntry::getArtifactKind)
        .thenComparing(VerificationEntry::getOrder);

    protected final ModuleComponentArtifactIdentifier id;
    protected final ArtifactVerificationOperation.ArtifactKind artifactKind;
    protected final File file;

    protected VerificationEntry(ModuleComponentArtifactIdentifier id, ArtifactVerificationOperation.ArtifactKind artifactKind, File file) {
        this.id = id;
        this.artifactKind = artifactKind;
        this.file = file;
    }

    public ModuleComponentArtifactIdentifier getId() {
        return id;
    }

    String getGroup() {
        return id.getComponentIdentifier().getGroup();
    }

    String getModule() {
        return id.getComponentIdentifier().getModule();
    }

    String getVersion() {
        return id.getComponentIdentifier().getVersion();
    }

    public ArtifactVerificationOperation.ArtifactKind getArtifactKind() {
        return artifactKind;
    }

    public File getFile() {
        return file;
    }

    abstract int getOrder();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VerificationEntry that = (VerificationEntry) o;

        if (!id.equals(that.id)) {
            return false;
        }
        if (artifactKind != that.artifactKind) {
            return false;
        }
        return file.equals(that.file);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + artifactKind.hashCode();
        result = 31 * result + file.hashCode();
        return result;
    }

    @Override
    public int compareTo(VerificationEntry other) {
        return ENTRY_COMPARATOR.compare(this, other);
    }
}
