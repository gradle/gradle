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

import java.io.File;

public class ValidatingIvyPublisher implements IvyPublisher {
    private final IvyPublisher delegate;

    public ValidatingIvyPublisher(IvyPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(IvyNormalizedPublication publication, PublicationAwareRepository repository) {
        validateIdentity(publication);
        validateArtifacts(publication);
        delegate.publish(publication, repository);
    }

    private void validateIdentity(IvyNormalizedPublication publication) {
        IvyPublicationIdentity identity = publication.getProjectIdentity();
        Node infoNode = getIvyFileInfoNode(publication.getDescriptorFile());

        field(publication, "organisation", identity.getOrganisation())
                .notEmpty()
                .matches((String) infoNode.attribute("organisation"));
        field(publication, "module name", identity.getModule())
                .notEmpty()
                .matches((String) infoNode.attribute("module"));
        field(publication, "revision", identity.getRevision())
                .notEmpty()
                .matches((String) infoNode.attribute("revision"));
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
            field(publication, "artifact name", artifact.getName())
                    .notEmpty();
            field(publication, "artifact type", artifact.getType())
                    .notEmpty();
            field(publication, "artifact extension", artifact.getExtension())
                    .notNull();
            field(publication, "artifact classifier", artifact.getClassifier())
                    .optionalNotEmpty();

            checkCanPublish(publication.getName(), artifact);
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

    private FieldValidator field(IvyNormalizedPublication publication, String name, String value) {
        return new FieldValidator(publication.getName(), name, value);
    }

    private static class FieldValidator {
        private final String publicationName;
        private final String name;
        private final String value;

        private FieldValidator(String publicationName, String name, String value) {
            this.publicationName = publicationName;
            this.name = name;
            this.value = value;
        }

        public FieldValidator notNull() {
            if (value == null) {
                throw new InvalidIvyPublicationException(publicationName, String.format("%s cannot be null.", name));
            }
            return this;
        }

        public FieldValidator notEmpty() {
            notNull();
            if (value.length() == 0) {
                throw new InvalidIvyPublicationException(publicationName, String.format("%s cannot be empty.", name));
            }
            return this;
        }

        public FieldValidator optionalNotEmpty() {
            if (value != null && value.length() == 0) {
                throw new InvalidIvyPublicationException(publicationName, String.format("%s cannot be an empty string. Use null instead.", name));
            }
            return this;
        }

        public FieldValidator matches(String expectedValue) {
            if (!value.equals(expectedValue)) {
                throw new InvalidIvyPublicationException(publicationName,
                        String.format("supplied %s does not match ivy descriptor (cannot edit %1$s directly in the ivy descriptor file).", name)
                );
            }
            return this;
        }
    }
}
