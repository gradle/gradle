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

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DisconnectedDescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DisconnectedIvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.publish.internal.PublicationFieldValidator;
import org.gradle.api.publish.ivy.InvalidIvyPublicationException;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata;
import org.gradle.internal.resource.local.FileResourceRepository;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ValidatingIvyPublisher implements IvyPublisher {
    private final DescriptorParseContext parserSettings = new DisconnectedDescriptorParseContext();
    private final IvyPublisher delegate;
    private final DisconnectedIvyXmlModuleDescriptorParser moduleDescriptorParser;

    public ValidatingIvyPublisher(IvyPublisher delegate, ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileResourceRepository fileResourceRepository, IvyMutableModuleMetadataFactory metadataFactory) {
        this.delegate = delegate;
        moduleDescriptorParser = new DisconnectedIvyXmlModuleDescriptorParser(new IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository, metadataFactory);
    }

    @Override
    public void publish(IvyNormalizedPublication publication, IvyArtifactRepository repository) {
        validateMetadata(publication);
        validateArtifacts(publication);
        checkNoDuplicateArtifacts(publication);
        delegate.publish(publication, repository);
    }

    private void validateMetadata(IvyNormalizedPublication publication) {
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

        MutableIvyModuleResolveMetadata metadata = parseIvyFile(publication);
        ModuleVersionIdentifier moduleId = metadata.getModuleVersionId();
        organisation.matches(moduleId.getGroup());
        moduleName.matches(moduleId.getName());
        revision.matches(moduleId.getVersion());

        field(publication, "branch", metadata.getBranch())
                .optionalNotEmpty()
                .doesNotContainSpecialCharacters(true);

        field(publication, "status", metadata.getStatus())
                .optionalNotEmpty()
                .validInFileName();
    }

    private MutableIvyModuleResolveMetadata parseIvyFile(IvyNormalizedPublication publication) {
        try {
            MetaDataParser.ParseResult<MutableIvyModuleResolveMetadata> parseResult = moduleDescriptorParser.parseMetaData(parserSettings, publication.getIvyDescriptorFile(), true);
            return parseResult.getResult();
        } catch (MetaDataParseException pe) {
            throw new InvalidIvyPublicationException(publication.getName(), pe.getLocalizedMessage(), pe);
        }
    }

    private void validateArtifacts(IvyNormalizedPublication publication) {
        for (final IvyArtifact artifact : publication.getAllArtifacts()) {
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

    private void checkNoDuplicateArtifacts(IvyNormalizedPublication publication) {
        Set<IvyArtifact> verified = new HashSet<IvyArtifact>();

        for (final IvyArtifact artifact : publication.getAllArtifacts()) {
            checkNotDuplicate(publication, verified, artifact.getName(), artifact.getExtension(), artifact.getType(), artifact.getClassifier());
            verified.add(artifact);
        }

        // Check that ivy.xml isn't duplicated
        checkNotDuplicate(publication, verified, "ivy", "xml", "xml", null);
    }

    private void checkNotDuplicate(IvyNormalizedPublication publication, Set<IvyArtifact> verified, String name, String extension, String type, String classifier) {
        for (IvyArtifact alreadyVerified : verified) {
            if (hasCoordinates(alreadyVerified, name, extension, type, classifier)) {
                String message = String.format(
                        "multiple artifacts with the identical name, extension, type and classifier ('%s', %s', '%s', '%s').",
                        name, extension, type, classifier
                );
                throw new InvalidIvyPublicationException(publication.getName(), message);
            }
        }
    }

    private boolean hasCoordinates(IvyArtifact one, String name, String extension, String type, String classifier) {
        return ObjectUtils.equals(one.getName(), name)
                && ObjectUtils.equals(one.getType(), type)
                && ObjectUtils.equals(one.getExtension(), extension)
                && ObjectUtils.equals(one.getClassifier(), classifier);
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
