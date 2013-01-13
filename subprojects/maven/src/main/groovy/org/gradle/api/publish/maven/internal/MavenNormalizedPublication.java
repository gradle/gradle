/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.publish.maven.InvalidMavenPublicationException;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class MavenNormalizedPublication implements MavenProjectIdentity {

    private final MavenProjectIdentity projectIdentity;
    private final File pomFile;
    private final Set<MavenArtifact> artifacts;

    public MavenNormalizedPublication(MavenProjectIdentity projectIdentity, File pomFile, Set<MavenArtifact> artifacts) {
        this.projectIdentity = projectIdentity;
        this.pomFile = pomFile;
        this.artifacts = artifacts;
    }

    public String getArtifactId() {
        return projectIdentity.getArtifactId();
    }

    public String getGroupId() {
        return projectIdentity.getGroupId();
    }

    public String getVersion() {
        return projectIdentity.getVersion();
    }

    public String getPackaging() {
        if (projectIdentity.getPackaging() != null) {
            return projectIdentity.getPackaging();
        }
        return getMainArtifact() == null ? "pom" : getMainArtifact().getExtension();
    }

    public File getPomFile() {
        return pomFile;
    }

    public MavenArtifact getMainArtifact() {
        Set<MavenArtifact> candidateMainArtifacts = CollectionUtils.filter(artifacts, new Spec<MavenArtifact>() {
            public boolean isSatisfiedBy(MavenArtifact element) {
                return element.getClassifier() == null || element.getClassifier().length() == 0;
            }
        });
        if (candidateMainArtifacts.isEmpty()) {
            return null;
        }
        if (candidateMainArtifacts.size() > 1) {
            throw new InvalidMavenPublicationException("Cannot determine main artifact: multiple artifacts found with empty classifier.");
        }
        return candidateMainArtifacts.iterator().next();
    }

    public Set<MavenArtifact> getAdditionalArtifacts() {
        if (artifacts.isEmpty()) {
            return Collections.emptySet();
        }
        MavenArtifact mainArtifact = getMainArtifact();
        Set<ArtifactKey> keys = new HashSet<ArtifactKey>();
        Set<MavenArtifact> additionalArtifacts = new LinkedHashSet<MavenArtifact>();
        for (MavenArtifact artifact : artifacts) {
            if (artifact == mainArtifact) {
                continue;
            }
            ArtifactKey key = new ArtifactKey(artifact);
            if (keys.contains(key)) {
                throw new InvalidMavenPublicationException(String.format("Cannot publish 2 artifacts with the identical extension '%s' and classifier '%s'.",
                        artifact.getExtension(), artifact.getClassifier()));
            }
            keys.add(key);
            additionalArtifacts.add(artifact);
        }
        return additionalArtifacts;
    }

    public Set<MavenArtifact> getArtifacts() {
        return artifacts;
    }

    public void validateModel() {
        getMainArtifact();
        getAdditionalArtifacts();
    }

    public void validateArtifacts() {
        for (MavenArtifact artifact : artifacts) {
            if (artifact.getFile() == null || !artifact.getFile().exists()) {
                throw new InvalidMavenPublicationException(String.format("Attempted to publish an artifact that does not exist: '%s'", artifact.getFile()));
            }
        }
    }

    private static class ArtifactKey {
        private final String extension;
        private final String classifier;

        public ArtifactKey(MavenArtifact artifact) {
            this.extension = artifact.getExtension();
            this.classifier = artifact.getClassifier();
        }

        @Override
        public boolean equals(Object o) {
            ArtifactKey other = (ArtifactKey) o;
            return ObjectUtils.equals(extension, other.extension) && ObjectUtils.equals(classifier, other.classifier);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hashCode(extension) ^ ObjectUtils.hashCode(classifier);
        }
    }
}
