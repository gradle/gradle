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
import org.gradle.api.publish.maven.internal.MavenProjectIdentity;
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
        validateIdentity(publication.getProjectIdentity());
        validatePomFileCoordinates(publication.getProjectIdentity(), publication.getPomFile());
        validateArtifacts(publication);

        delegate.publish(publication, artifactRepository);
    }

    private void validateIdentity(MavenProjectIdentity projectIdentity) {
        checkValidMavenIdentifier("groupId", projectIdentity.getGroupId());
        checkValidMavenIdentifier("artifactId", projectIdentity.getArtifactId());
        checkNonEmpty("version", projectIdentity.getVersion());
    }

    private void checkValidMavenIdentifier(String name, String value) {
        checkNonEmpty(name, value);
        if (!value.matches(ID_REGEX)) {
            throw new InvalidMavenPublicationException(String.format("The %s value is not a valid Maven identifier (%s)", name, ID_REGEX));
        }
    }

    private void checkNonEmpty(String name, String value) {
        if (!GUtil.isTrue(value)) {
            throw new InvalidMavenPublicationException(String.format("The %s value cannot be empty", name));
        }
    }

    private void validatePomFileCoordinates(MavenProjectIdentity projectIdentity, File pomFile) {
        Model model = parsePomFileIntoMavenModel(pomFile);
        checkMatches("groupId", projectIdentity.getGroupId(), model.getGroupId());
        checkMatches("artifactId", projectIdentity.getArtifactId(), model.getArtifactId());
        checkMatches("version", projectIdentity.getVersion(), model.getVersion());
    }

    private void checkMatches(String name, String projectIdentityValue, String pomFileValue) {
        if (!projectIdentityValue.equals(pomFileValue)) {
            throw new InvalidMavenPublicationException(String.format("Publication %1$s does not match POM file value. Cannot edit %1$s directly in the POM file.", name));
        }
    }

    private Model parsePomFileIntoMavenModel(File pomFile) {
        try {
            FileReader reader = new FileReader(pomFile);
            Model model = new MavenXpp3Reader().read(reader);
            model.setPomFile(pomFile);
            return model;
        } catch (XmlPullParserException parseException) {
            throw new InvalidMavenPublicationException("POM file is invalid. Check any modifications you have made to the POM file.", parseException);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    private void validateArtifacts(MavenNormalizedPublication publication) {
        Set<MavenArtifactKey> keys = new HashSet<MavenArtifactKey>();
        for (MavenArtifact artifact : publication.getArtifacts()) {
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
