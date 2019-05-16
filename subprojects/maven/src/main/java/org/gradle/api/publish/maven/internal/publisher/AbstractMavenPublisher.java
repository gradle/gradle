/*
 * Copyright 2019 the original author or authors.
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

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.ByteArrayReadableContent;
import org.gradle.internal.resource.local.FileReadableContent;
import org.gradle.internal.xml.XmlTransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;

import static org.apache.maven.artifact.ArtifactUtils.isSnapshot;

abstract class AbstractMavenPublisher implements MavenPublisher {
    private static final String POM_FILE_ENCODING = "UTF-8";
    private final Factory<File> temporaryDirFactory;
    protected final RepositoryTransportFactory repositoryTransportFactory;
    private final XmlTransformer xmlTransformer = new XmlTransformer();

    public AbstractMavenPublisher(Factory<File> temporaryDirFactory, RepositoryTransportFactory repositoryTransportFactory) {
        this.temporaryDirFactory = temporaryDirFactory;
        this.repositoryTransportFactory = repositoryTransportFactory;
    }

    protected void publish(MavenNormalizedPublication publication, ExternalResourceRepository repository, URI rootUri, boolean localRepo) {
        MavenProjectIdentity projectIdentity = publication.getProjectIdentity();
        String groupId = projectIdentity.getGroupId().get();
        String artifactId = projectIdentity.getArtifactId().get();
        String version = projectIdentity.getVersion().get();

        ModuleArtifactPublisher artifactPublisher = new ModuleArtifactPublisher(repository, localRepo, rootUri, groupId, artifactId, version);

        if (publication.getMainArtifact() != null) {
            artifactPublisher.publish(null, publication.getPackaging(), publication.getMainArtifact().getFile());
        }
        artifactPublisher.publish(null, "pom", publication.getPomArtifact().getFile());
        for (MavenArtifact artifact : publication.getAdditionalArtifacts()) {
            artifactPublisher.publish(artifact.getClassifier(), artifact.getExtension(), artifact.getFile());
        }

        ExternalResourceName externalResource = artifactPublisher.getMetadataPath();
        Metadata metadata = createMetadata(groupId, artifactId, version, repository, externalResource);
        artifactPublisher.publish(externalResource, writeMetadataToTmpFile(metadata, "module-maven-metadata.xml"));
    }

    private Metadata createMetadata(String groupId, String artifactId, String version, ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        Versioning versioning = getExistingVersioning(repository, metadataResource);
        if (!versioning.getVersions().contains(version)) {
            versioning.addVersion(version);
        }
        if (!isSnapshot(version)) {
            versioning.setRelease(version);
        }
        versioning.updateTimestamp();

        Metadata metadata = new Metadata();
        metadata.setGroupId(groupId);
        metadata.setArtifactId(artifactId);
        metadata.setVersioning(versioning);
        return metadata;
    }

    private Versioning getExistingVersioning(ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        ExternalResourceReadResult<Metadata> existing = readExistingMetadata(repository, metadataResource);

        if (existing != null) {
            Metadata recessive = existing.getResult();
            if (recessive.getVersioning() != null) {
                return recessive.getVersioning();
            }
        }
        return new Versioning();
    }

    protected ExternalResourceReadResult<Metadata> readExistingMetadata(ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        return repository.resource(metadataResource).withContentIfPresent(new Transformer<Metadata, InputStream>() {
            @Override
            public Metadata transform(InputStream inputStream) {
                try {
                    return new MetadataXpp3Reader().read(inputStream, false);
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
    }

    private File writeMetadataToTmpFile(Metadata metadata, String fileName) {
        File metadataFile = new File(temporaryDirFactory.create(), fileName);
        xmlTransformer.transform(metadataFile, POM_FILE_ENCODING, new Action<Writer>() {
            public void execute(Writer writer) {
                try {
                    new MetadataXpp3Writer().write(writer, metadata);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return metadataFile;
    }

    private static class ModuleArtifactPublisher {
        private final ExternalResourceRepository repository;
        private final boolean localRepo;
        private final URI rootUri;
        private final String groupId;
        private final String artifactId;
        private final String moduleVersion;
        private String artifactVersion;

        public ModuleArtifactPublisher(ExternalResourceRepository repository, boolean localRepo, URI rootUri, String groupId, String artifactId, String moduleVersion) {
            this.repository = repository;
            this.localRepo = localRepo;
            this.rootUri = rootUri;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.moduleVersion = moduleVersion;
            this.artifactVersion = moduleVersion;
        }

        public ExternalResourceName getArtifactPath(String classifier, String extension) {
            return new ExternalResourceName(rootUri, getArtifactPath(groupId, artifactId, moduleVersion, artifactVersion, classifier, extension));
        }

        public ExternalResourceName getMetadataPath() {
            StringBuilder path = new StringBuilder(128);
            path.append(groupId.replace('.', '/')).append('/');
            path.append(artifactId).append('/');
            path.append(getMetadataFileName());

            return new ExternalResourceName(rootUri, path.toString());
        }

        private String getMetadataFileName() {
            if (localRepo) {
                return "maven-metadata-local.xml";
            }
            return "maven-metadata.xml";
        }

        private String getArtifactPath(String groupId, String artifactId, String moduleVersion, String artifactVersion, String classifier, String extension) {
            StringBuilder path = new StringBuilder(128);
            path.append(groupId.replace('.', '/')).append('/');
            path.append(artifactId).append('/');
            path.append(moduleVersion).append('/');

            path.append(artifactId).append('-').append(artifactVersion);

            if (classifier != null) {
                path.append('-').append(classifier);
            }

            if (extension.length() > 0) {
                path.append('.').append(extension);
            }

            return path.toString();
        }

        public void publish(String classifier, String extension, File content) {
            ExternalResourceName externalResource = getArtifactPath(classifier, extension);
            publish(externalResource, content);
        }

        public void publish(ExternalResourceName externalResource, File content) {
            repository.withProgressLogging().resource(externalResource).put(new FileReadableContent(content));
            if (!localRepo) {
                publishChecksums(repository, content, externalResource);
            }
        }

        private void publishChecksums(ExternalResourceRepository repository, File source, ExternalResourceName destination) {
            byte[] sha1 = createChecksumFile(source, "SHA1", 40);
            repository.resource(destination.append(".sha1")).put(new ByteArrayReadableContent(sha1));

            byte[] md5 = createChecksumFile(source, "MD5", 32);
            repository.resource(destination.append(".md5")).put(new ByteArrayReadableContent(md5));
        }

        private byte[] createChecksumFile(File src, String algorithm, int checksumLength) {
            HashValue hash = HashUtil.createHash(src, algorithm);
            String formattedHashString = hash.asZeroPaddedHexString(checksumLength);
            try {
                return formattedHashString.getBytes("US-ASCII");
            } catch (UnsupportedEncodingException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
