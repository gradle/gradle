/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher;

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.maven.InvalidMavenPublicationException;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactKey;
import org.gradle.internal.UncheckedException;
import org.gradle.mvn3.org.apache.maven.model.Model;
import org.gradle.mvn3.org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.gradle.mvn3.org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

public class ValidatingMavenPublisher implements MavenPublisher {
    private static final java.lang.String ID_REGEX = "[A-Za-z0-9_\\-.]+";
    private final MavenPublisher delegate;

    public ValidatingMavenPublisher(MavenPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        validateIdentity(publication);
        validatePomFileCoordinates(publication);
        validateArtifacts(publication);

        delegate.publish(publication, artifactRepository);
    }

    private void validateIdentity(MavenNormalizedPublication publication) {
        MavenProjectIdentity projectIdentity = publication.getProjectIdentity();
        checkValidMavenIdentifier(publication.getName(), "groupId", projectIdentity.getGroupId());
        checkValidMavenIdentifier(publication.getName(), "artifactId", projectIdentity.getArtifactId());
        checkNonEmpty(publication.getName(), "version", projectIdentity.getVersion());
    }

    private void checkValidMavenIdentifier(String publicationName, String name, String value) {
        checkNonEmpty(publicationName, name, value);
        if (!value.matches(ID_REGEX)) {
            throw new InvalidMavenPublicationException(String.format("Invalid publication '%s': %s is not a valid Maven identifier (%s)", publicationName, name, ID_REGEX));
        }
    }

    private void checkNonEmpty(String publicationName, String name, String value) {
        if (!GUtil.isTrue(value)) {
            throw new InvalidMavenPublicationException(String.format("Invalid publication '%s': %s cannot be empty", publicationName, name));
        }
    }

    private void validatePomFileCoordinates(MavenNormalizedPublication publication) {
        MavenProjectIdentity projectIdentity = publication.getProjectIdentity();
        Model model = parsePomFileIntoMavenModel(publication);
        checkMatches(publication.getName(), "groupId", projectIdentity.getGroupId(), model.getGroupId());
        checkMatches(publication.getName(), "artifactId", projectIdentity.getArtifactId(), model.getArtifactId());
        checkMatches(publication.getName(), "version", projectIdentity.getVersion(), model.getVersion());
    }

    private void checkMatches(String publicationName, String name, String projectIdentityValue, String pomFileValue) {
        if (!projectIdentityValue.equals(pomFileValue)) {
            throw new InvalidMavenPublicationException(String.format(
                    "Invalid publication '%s': supplied %s does not match POM file (cannot edit %2$s directly in the POM file).",
                    publicationName, name));
        }
    }

    private Model parsePomFileIntoMavenModel(MavenNormalizedPublication publication) {
        try {
            FileReader reader = new FileReader(publication.getPomFile());
            Model model = new MavenXpp3Reader().read(reader);
            model.setPomFile(publication.getPomFile());
            return model;
        } catch (XmlPullParserException parseException) {
            throw new InvalidMavenPublicationException(
                    String.format("Invalid publication '%s': POM file is invalid. Check any modifications you have made to the POM file.", publication.getName()),
                    parseException);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    private void validateArtifacts(MavenNormalizedPublication publication) {
        Set<MavenArtifactKey> keys = new HashSet<MavenArtifactKey>();
        for (MavenArtifact artifact : publication.getArtifacts()) {
            checkArtifactAttribute(publication.getName(), "extension", artifact.getExtension());
            checkArtifactAttribute(publication.getName(), "classifier", artifact.getClassifier());

            checkCanPublish(publication.getName(), artifact);

            MavenArtifactKey key = new MavenArtifactKey(artifact);
            if (keys.contains(key)) {
                throw new InvalidMavenPublicationException(
                        String.format(
                                "Cannot publish maven publication '%s': multiple artifacts with the identical extension '%s' and classifier '%s'.",
                                publication.getName(), artifact.getExtension(), artifact.getClassifier()
                        ));
            }
            keys.add(key);
        }
    }

    private void checkArtifactAttribute(String publicationName, String name, String value) {
        if (value != null && value.length() == 0) {
            throw new InvalidMavenPublicationException(String.format(
                    "Invalid publication '%s': artifact %s cannot be an empty string. Use null instead.", publicationName, name));
        }
    }

    private void checkCanPublish(String publicationName, MavenArtifact artifact) {
        File artifactFile = artifact.getFile();
        if (artifactFile == null || !artifactFile.exists()) {
            throw new InvalidMavenPublicationException(String.format("Cannot publish maven publication '%s': artifact file does not exist: '%s'", publicationName, artifactFile));
        }
        if (artifactFile.isDirectory()) {
            throw new InvalidMavenPublicationException(String.format("Cannot publish maven publication '%s': artifact file is a directory: '%s'", publicationName, artifactFile));
        }
    }
}
