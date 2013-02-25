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

package org.gradle.api.publish.ivy.internal.publisher;

import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.QName;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.api.publish.ivy.InvalidIvyPublicationException;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;

import java.io.File;

public class ValidatingIvyPublisher implements IvyPublisher {
    private final IvyPublisher delegate;

    public ValidatingIvyPublisher(IvyPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(IvyNormalizedPublication publication, PublicationAwareRepository repository) {
        validateIdentity(publication);
        validateIvyDescriptorFileCoordinates(publication);
        validateArtifacts(publication);
        delegate.publish(publication, repository);
    }

    private void validateIdentity(IvyNormalizedPublication publication) {
        IvyProjectIdentity identity = publication.getProjectIdentity();
        checkNonEmpty(publication.getName(), "organisation", identity.getOrganisation());
        checkNonEmpty(publication.getName(), "module name", identity.getModule());
        checkNonEmpty(publication.getName(), "revision", identity.getRevision());
    }

    private void checkNonEmpty(String publicationName, String name, String value) {
        if (!GUtil.isTrue(value)) {
            throw new InvalidIvyPublicationException(publicationName, String.format("%s cannot be empty.", name));
        }
    }

    private void validateIvyDescriptorFileCoordinates(IvyNormalizedPublication publication) {
        IvyProjectIdentity identity = publication.getProjectIdentity();
        Node infoNode = getIvyFileInfoNode(publication.getDescriptorFile());
        checkMatches(publication.getName(), "organisation", identity.getOrganisation(), (String) infoNode.attribute("organisation"));
        checkMatches(publication.getName(), "module name", identity.getModule(), (String) infoNode.attribute("module"));
        checkMatches(publication.getName(), "revision", identity.getRevision(), (String) infoNode.attribute("revision"));
    }

    private void checkMatches(String publicationName, String name, String projectIdentityValue, String pomFileValue) {
        if (!projectIdentityValue.equals(pomFileValue)) {
            throw new InvalidIvyPublicationException(publicationName,
                    String.format("supplied %s does not match ivy descriptor (cannot edit %1$s directly in the ivy descriptor file).", name)
            );
        }
    }

    private Node getIvyFileInfoNode(File ivyFile) {
        Node rootNode;
        try {
            // TODO:DAZ Turn on XML validation, and add a schema to the generated ivy file
            rootNode = new XmlParser().parse(ivyFile);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }

        return (Node) rootNode.getAt(new QName("info")).get(0);
    }

    public void validateArtifacts(IvyNormalizedPublication publication) {
        for (IvyArtifact artifact : publication.getArtifacts()) {
            checkOptionalNonEmpty(publication.getName(), "artifact name", artifact.getName());
            checkOptionalNonEmpty(publication.getName(), "artifact type", artifact.getType());
            checkOptionalNonEmpty(publication.getName(), "artifact extension", artifact.getExtension());
            checkOptionalNonEmpty(publication.getName(), "artifact classifier", artifact.getClassifier());

            checkCanPublish(publication.getName(), artifact);
        }
    }

    private void checkOptionalNonEmpty(String publicationName, String name, String value) {
        if (value != null && value.length() == 0) {
            throw new InvalidIvyPublicationException(publicationName, String.format("%s cannot be an empty string. Use null instead.", name));
        }
    }

    private void checkCanPublish(String name, IvyArtifact artifact) {
        File artifactFile = artifact.getFile();
        if (artifactFile == null || !artifactFile.exists()) {
            throw new InvalidIvyPublicationException(name, String.format("artifact file does not exist: '%s'", artifactFile));
        }
        if (artifactFile.isDirectory()) {
            throw new InvalidIvyPublicationException(name, String.format("artifact file is a directory: '%s'", artifactFile));
        }
    }
}
