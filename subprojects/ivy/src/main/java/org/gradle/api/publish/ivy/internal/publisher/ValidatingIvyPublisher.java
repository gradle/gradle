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

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DisconnectedParserSettings;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.api.publish.internal.PublicationFieldValidator;
import org.gradle.api.publish.ivy.InvalidIvyPublicationException;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.net.URL;
import java.text.ParseException;

public class ValidatingIvyPublisher implements IvyPublisher {
    private final ParserSettings parserSettings = new DisconnectedParserSettings();
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

        IvyFieldValidator organisation = field(publication, "organisation", identity.getOrganisation())
                .notEmpty()
                .validInFileName();
        IvyFieldValidator moduleName = field(publication, "module name", identity.getModule())
                .notEmpty()
                .validInFileName();
        IvyFieldValidator revision = field(publication, "revision", identity.getRevision())
                .notEmpty()
                .validInFileName();

        ModuleRevisionId moduleId = parseIvyFile(publication);
        organisation.matches(moduleId.getOrganisation());
        moduleName.matches(moduleId.getName());
        revision.matches(moduleId.getRevision());
    }

    private ModuleRevisionId parseIvyFile(IvyNormalizedPublication publication) {

        try {
            URL ivyFileLocation = publication.getDescriptorFile().toURI().toURL();
            return new IvyXmlModuleDescriptorParser().parseDescriptor(parserSettings, ivyFileLocation, true).getModuleRevisionId();
        } catch (ParseException pe) {
            throw new InvalidIvyPublicationException(publication.getName(), pe.getLocalizedMessage(), pe);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    public void validateArtifacts(IvyNormalizedPublication publication) {
        for (IvyArtifact artifact : publication.getArtifacts()) {
            field(publication, "artifact name", artifact.getName())
                    .notEmpty().validInFileName();
            field(publication, "artifact type", artifact.getType())
                    .notEmpty().validInFileName();
            field(publication, "artifact extension", artifact.getExtension())
                    .notNull().validInFileName();
            field(publication, "artifact classifier", artifact.getClassifier())
                    .optionalNotEmpty().validInFileName();

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

    private IvyFieldValidator field(IvyNormalizedPublication publication, String name, String value) {
        return new IvyFieldValidator(publication.getName(), name, value);
    }

    private static class IvyFieldValidator extends PublicationFieldValidator<IvyFieldValidator> {
        private IvyFieldValidator(String publicationName, String name, String value) {
            super(IvyFieldValidator.class, publicationName, name, value);
        }

        public IvyFieldValidator matches(String expectedValue) {
            if (!value.equals(expectedValue)) {
                throw new InvalidIvyPublicationException(publicationName,
                        String.format("supplied %s does not match ivy descriptor (cannot edit %1$s directly in the ivy descriptor file).", name)
                );
            }
            return this;
        }

        @Override
        protected InvalidUserDataException failure(String message) {
            throw new InvalidIvyPublicationException(publicationName, message);
        }
    }
}
