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
package org.gradle.api.publication.maven.internal;

import com.google.common.collect.Sets;
import org.apache.commons.lang.ObjectUtils;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.maven.project.MavenProject;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;

import java.io.File;
import java.util.*;

public class DefaultArtifactPom implements ArtifactPom {
    private static final Set<String> PACKAGING_TYPES = Sets.newHashSet("war", "jar", "ear");
    private final MavenPom pom;
    private final Map<ArtifactKey, PublishArtifact> artifacts = new HashMap<ArtifactKey, PublishArtifact>();

    private PublishArtifact artifact;

    private final Set<PublishArtifact> classifiers = new HashSet<PublishArtifact>();

    public DefaultArtifactPom(MavenPom pom) {
        this.pom = pom;
    }

    public MavenPom getPom() {
        return pom;
    }

    public PublishArtifact getArtifact() {
        return artifact;
    }

    public Set<PublishArtifact> getAttachedArtifacts() {
        return Collections.unmodifiableSet(classifiers);
    }

    public PublishArtifact writePom(final File pomFile) {
        getPom().writeTo(pomFile);
        return new PomArtifact(pomFile);
    }

    public void addArtifact(Artifact artifact, File src) {
        throwExceptionIfArtifactOrSrcIsNull(artifact, src);
        PublishArtifact publishArtifact = new MavenArtifact(artifact, src);
        ArtifactKey artifactKey = new ArtifactKey(publishArtifact);
        if (this.artifacts.containsKey(artifactKey)) {
            throw new InvalidUserDataException(String.format("A POM cannot have multiple artifacts with the same type and classifier. Already have %s, trying to add %s.", this.artifacts.get(
                    artifactKey), publishArtifact));
        }

        if (publishArtifact.getClassifier() != null) {
            addArtifact(publishArtifact);
            assignArtifactValuesToPom(artifact, pom, false);
            return;
        }

        if (this.artifact != null) {
            // Choose the 'main' artifact based on its type.
            if (!PACKAGING_TYPES.contains(artifact.getType())) {
                addArtifact(publishArtifact);
                return;
            }
            if (PACKAGING_TYPES.contains(this.artifact.getType())) {
                throw new InvalidUserDataException("A POM can not have multiple main artifacts. " + "Already have " + this.artifact + ", trying to add " + publishArtifact);
            }
            addArtifact(this.artifact);
        }

        this.artifact = publishArtifact;
        this.artifacts.put(artifactKey, publishArtifact);
        assignArtifactValuesToPom(artifact, pom, true);
    }

    private void addArtifact(PublishArtifact artifact) {
        classifiers.add(artifact);
        artifacts.put(new ArtifactKey(artifact), artifact);
    }

    private String getClassifier(Artifact artifact) {
        return artifact.getExtraAttribute("classifier");
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

    private static class ArtifactKey {
        private final String type;
        private final String classifier;

        private ArtifactKey(PublishArtifact artifact) {
            this.type = artifact.getType();
            this.classifier = artifact.getClassifier();
        }

        @Override
        public boolean equals(Object o) {
            ArtifactKey other = (ArtifactKey) o;
            return ObjectUtils.equals(type, other.type) && ObjectUtils.equals(classifier, other.classifier);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hashCode(type) ^ ObjectUtils.hashCode(classifier);
        }
    }

    private abstract class AbstractMavenArtifact extends AbstractPublishArtifact {
        private final File file;

        protected AbstractMavenArtifact(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        public String getName() {
            return pom.getArtifactId();
        }

        public Date getDate() {
            return null;
        }
    }

    private class MavenArtifact extends AbstractMavenArtifact {
        private final Artifact artifact;

        private MavenArtifact(Artifact artifact, File file) {
            super(file);
            this.artifact = artifact;
        }

        public String getClassifier() {
            return DefaultArtifactPom.this.getClassifier(artifact);
        }

        public String getExtension() {
            return artifact.getExt();
        }

        public String getType() {
            return artifact.getType();
        }
    }

    private class PomArtifact extends AbstractMavenArtifact {
        public PomArtifact(File pomFile) {
            super(pomFile);
        }

        public String getExtension() {
            return "pom";
        }

        public String getType() {
            return "pom";
        }

        public String getClassifier() {
            return null;
        }

        public Date getDate() {
            return null;
        }
    }
}
