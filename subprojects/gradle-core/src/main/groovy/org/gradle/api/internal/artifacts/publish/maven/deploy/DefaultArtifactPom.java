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

import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.maven.project.MavenProject;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.maven.MavenPom;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactPom implements ArtifactPom {
    private static final Set<String> PACKAGING_TYPES = Sets.newHashSet("war", "jar", "ear");
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

    public void writePom(File pomFile) {
        try {
            pomFile.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(pomFile);
            try {
                getPom().writeTo(writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void addArtifact(Artifact artifact, File src) {
        throwExceptionIfArtifactOrSrcIsNull(artifact, src);
        if (hasClassifier(artifact)) {
            addClassifierArtifact(artifact, src);
            assignArtifactValuesToPom(artifact, pom, false);
            return;
        }

        if (this.artifact != null) {
            // Choose the 'main' artifact based on its type.
            if (!PACKAGING_TYPES.contains(artifact.getType())) {
                addClassifierArtifact(artifact, src);
                return;
            }
            if (PACKAGING_TYPES.contains(this.artifact.getType())) {
                throw new InvalidUserDataException("A pom can't have multiple main artifacts. " +
                        "Already assigned artifact: " + this.artifact + " Artifact trying to assign: " + artifact);
            }
            addClassifierArtifact(this.artifact, this.artifactFile);
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
        if (pom.getGroupId().equals(MavenProject.EMPTY_PROJECT_GROUP_ID)) {
            pom.setGroupId(artifact.getModuleRevisionId().getOrganisation());
        }
        if (pom.getArtifactId().equals(MavenProject.EMPTY_PROJECT_ARTIFACT_ID)) {
            pom.setArtifactId(artifact.getName());
        }
        if (pom.getVersion().equals(MavenProject.EMPTY_PROJECT_VERSION)) {
            pom.setVersion(artifact.getModuleRevisionId().getRevision());
        }
        if (setType) {
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
