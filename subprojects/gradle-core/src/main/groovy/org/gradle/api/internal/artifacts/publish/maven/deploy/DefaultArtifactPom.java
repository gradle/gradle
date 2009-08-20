/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.maven.deploy;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.maven.MavenPom;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPom implements ArtifactPom {
    private MavenPom pom;

    private Artifact artifact;

    private File artifactFile;

    public DefaultArtifactPom(MavenPom pom, Artifact artifact, File artifactFile) {
        this.pom = pom;
        addArtifact(artifact, artifactFile);
    }

    public MavenPom getPom() {
        return pom;
    }

    public void setPom(MavenPom pom) {
        this.pom = pom;
    }

    public File getArtifactFile() {
        return artifactFile;
    }

    public void setArtifactFile(File artifactFile) {
        this.artifactFile = artifactFile;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    private void addArtifact(Artifact artifact, File src) {
        throwEceptionIfArtifactOrSrcIsNull(artifact, src);
        if (this.artifact != null) {
            throw new InvalidUserDataException("A pom can't have multiple artifacts.");
        }
        this.artifact = artifact;
        this.artifactFile = src;
        assignArtifactValuesToPom(artifact, pom);
    }

    private void assignArtifactValuesToPom(Artifact artifact, MavenPom pom) {
        if (pom.getGroupId() == null) {
            pom.setGroupId(artifact.getModuleRevisionId().getOrganisation());
        }
        if (pom.getArtifactId() == null) {
            pom.setArtifactId(artifact.getName());
        }
        if (pom.getVersion() == null) {
            pom.setVersion(artifact.getModuleRevisionId().getRevision());
        }
        if (pom.getPackaging() == null) {
            pom.setPackaging(artifact.getType());
        }
        if (pom.getClassifier() == null) {
            pom.setClassifier(artifact.getExtraAttribute(Dependency.CLASSIFIER));
        }
    }

    private void throwEceptionIfArtifactOrSrcIsNull(Artifact artifact, File src) {
        if (artifact == null) {
            throw new InvalidUserDataException("Artifact must not be null.");
        }
        if (src == null) {
            throw new InvalidUserDataException("Src file must not be null.");
        }
    }
}
