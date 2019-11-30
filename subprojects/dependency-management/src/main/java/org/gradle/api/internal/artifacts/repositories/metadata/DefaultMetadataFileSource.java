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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.io.File;
import java.math.BigInteger;

/**
 * This module source stores information about the original
 * descriptor.
 */
public class DefaultMetadataFileSource implements MetadataFileSource {
    private final ModuleComponentArtifactIdentifier artifactId;
    private final File artifactFile;
    private final BigInteger sha1;

    public DefaultMetadataFileSource(ModuleComponentArtifactIdentifier artifactId, File artifactFile, BigInteger sha1) {
        this.artifactId = artifactId;
        this.artifactFile = artifactFile;
        this.sha1 = sha1;
    }

    @Override
    public File getArtifactFile() {
        return artifactFile;
    }

    @Override
    public ModuleComponentArtifactIdentifier getArtifactId() {
        return artifactId;
    }

    @Override
    public BigInteger getSha1() {
        return sha1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultMetadataFileSource that = (DefaultMetadataFileSource) o;

        if (!artifactId.equals(that.artifactId)) {
            return false;
        }
        return sha1.equals(that.sha1);
    }

    @Override
    public int hashCode() {
        int result = artifactId.hashCode();
        result = 31 * result + sha1.hashCode();
        return result;
    }
}
