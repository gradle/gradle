/*
 * Copyright 2010 the original author or authors.
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
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPom implements ArtifactPom {
    private final MavenPom pom;

    private Artifact artifact;

    private File artifactFile;

    private final Set<ClassifierArtifact> classifiers = new HashSet<ClassifierArtifact>();

    public DefaultArtifactPom(MavenPom pom) {
        this.pom = pom;
    }

    public MavenPom getPom() {
        return pom;
    }

    public File getArtifactFile() {
        return artifactFile;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public Set<ClassifierArtifact> getClassifiers() {
        return Collections.unmodifiableSet(classifiers);
    }

    public void addArtifact(Artifact artifact, File src) {
        throwExceptionIfArtifactOrSrcIsNull(artifact, src);
        if (hasClassifier(artifact)) {
            addClassifierArtifact(artifact, src);
            assignArtifactValuesToPom(artifact, pom, false);
            return;
        }
        if (this.artifact != null) {
            throw new InvalidUserDataException("A pom can't have multiple main artifacts. " +
                    "Already assigned artifact: " + this.artifact + " Artifact trying to assign: " + artifact);
        }
        this.artifact = artifact;
        this.artifactFile = src;
        assignArtifactValuesToPom(artifact, pom, true);
    }

    private void addClassifierArtifact(Artifact artifact, File artifactFile) {
        String classifier = getClassifier(artifact);
        ClassifierArtifact classifierArtifact = new ClassifierArtifact(classifier,
                artifact.getType(), artifactFile);
        if (classifiers.contains(classifierArtifact)) {
            throw new InvalidUserDataException("A pom can't have multiple artifacts for the same classifier=" + classifier +
                    " Artifact trying to assign: " + artifact); 
        }
        classifiers.add(classifierArtifact);
    }

    private boolean hasClassifier(Artifact artifact) {
        return getClassifier(artifact) != null;
    }

    private String getClassifier(Artifact artifact) {
        return artifact.getExtraAttribute(Dependency.CLASSIFIER);
    }

    private void assignArtifactValuesToPom(Artifact artifact, MavenPom pom, boolean setType) {
        if (pom.getGroupId() == null) {
            pom.setGroupId(artifact.getModuleRevisionId().getOrganisation());
        }
        if (pom.getArtifactId() == null) {
            pom.setArtifactId(artifact.getName());
        }
        if (pom.getVersion() == null) {
            pom.setVersion(artifact.getModuleRevisionId().getRevision());
        }
        if (setType && pom.getPackaging() == null) {
            pom.setPackaging(artifact.getType());
        }
    }

    private void throwExceptionIfArtifactOrSrcIsNull(Artifact artifact, File src) {
        if (artifact == null) {
            throw new InvalidUserDataException("Artifact must not be null.");
        }
        if (src == null) {
            throw new InvalidUserDataException("Src file must not be null.");
        }
    }
}
