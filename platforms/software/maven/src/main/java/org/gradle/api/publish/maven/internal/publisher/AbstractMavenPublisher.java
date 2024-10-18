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
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.artifacts.repositories.transport.NetworkOperationBackOffAndRetry;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashFunction;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.local.ByteArrayReadableContent;
import org.gradle.internal.resource.local.FileReadableContent;
import org.gradle.internal.xml.XmlTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@NonNullApi
abstract class AbstractMavenPublisher implements MavenPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenPublisher.class);

    private final NetworkOperationBackOffAndRetry<ExternalResourceReadResult<Metadata>> metadataRetryCaller = new NetworkOperationBackOffAndRetry<>();
    private static final String POM_FILE_ENCODING = "UTF-8";
    private static final String SNAPSHOT_VERSION = "SNAPSHOT";
    private static final Pattern VERSION_FILE_PATTERN = Pattern.compile("^(.*)-([0-9]{8}.[0-9]{6})-([0-9]+)$");
    private final Factory<File> temporaryDirFactory;
    private final XmlTransformer xmlTransformer = new XmlTransformer();

    AbstractMavenPublisher(Factory<File> temporaryDirFactory) {
        this.temporaryDirFactory = temporaryDirFactory;
    }

    protected void publish(MavenNormalizedPublication publication, ExternalResourceRepository repository, URI rootUri, boolean localRepo) {
        String groupId = publication.getGroupId();
        String artifactId = publication.getArtifactId();
        String version = publication.getVersion();

        ModuleArtifactPublisher artifactPublisher = new ModuleArtifactPublisher(repository, localRepo, rootUri, groupId, artifactId, version);
        SnapshotMetadataResult snapshotMetadataResult = computeSnapshotMetadata(publication, repository, version, artifactPublisher, groupId, artifactId);

        if (snapshotMetadataResult != null && !localRepo) {
            // Use the timestamped version for all published artifacts
            artifactPublisher.artifactVersion = snapshotMetadataResult.getVersion();
        }

        publishArtifactsAndMetadata(publication, artifactPublisher);

        publishPublicationMetadata(repository, version, artifactPublisher, groupId, artifactId, snapshotMetadataResult);
    }

    @Nullable
    private SnapshotMetadataResult computeSnapshotMetadata(MavenNormalizedPublication publication, ExternalResourceRepository repository, String version, ModuleArtifactPublisher artifactPublisher, String groupId, String artifactId) {
        if (isSnapshot(version)) {
            ExternalResourceName snapshotMetadataPath = artifactPublisher.getSnapshotMetadataLocation();
            Metadata snapshotMetadata = createSnapshotMetadata(publication, groupId, artifactId, version, repository, snapshotMetadataPath);
            return new SnapshotMetadataResult(snapshotMetadataPath, snapshotMetadata);
        }
        return null;
    }

    private void publishPublicationMetadata(ExternalResourceRepository repository, String version, ModuleArtifactPublisher artifactPublisher, String groupId, String artifactId, @Nullable SnapshotMetadataResult snapshotMetadataResult) {
        if (snapshotMetadataResult != null) {
            artifactPublisher.publish(snapshotMetadataResult.snapshotMetadataPath, writeMetadataToTmpFile(snapshotMetadataResult.snapshotMetadata, "snapshot-maven-metadata.xml"));
        }

        ExternalResourceName externalResource = artifactPublisher.getMetadataLocation();
        Metadata metadata = createMetadata(groupId, artifactId, version, repository, externalResource);
        artifactPublisher.publish(externalResource, writeMetadataToTmpFile(metadata, "module-maven-metadata.xml"));
    }

    private static void publishArtifactsAndMetadata(MavenNormalizedPublication publication, ModuleArtifactPublisher artifactPublisher) {
        if (publication.getMainArtifact() != null) {
            artifactPublisher.publish(null, publication.getMainArtifact().getExtension(), publication.getMainArtifact().getFile());
        }
        artifactPublisher.publish(null, "pom", publication.getPomArtifact().getFile());
        for (MavenArtifact artifact : publication.getAdditionalArtifacts()) {
            artifactPublisher.publish(artifact.getClassifier(), artifact.getExtension(), artifact.getFile());
        }
    }

    private Metadata createMetadata(String groupId, String artifactId, String version, ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        Versioning versioning = getExistingVersioning(repository, metadataResource);
        if (!versioning.getVersions().contains(version)) {
            versioning.addVersion(version);
        }
        versioning.setLatest(version);
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
            if (recessive != null && recessive.getVersioning() != null) {
                return recessive.getVersioning();
            }
        }

        return new Versioning();
    }

    private File writeMetadataToTmpFile(Metadata metadata, String fileName) {
        File metadataFile = new File(temporaryDirFactory.create(), fileName);
        xmlTransformer.transform(metadataFile, POM_FILE_ENCODING, writer -> {
            try {
                new MetadataXpp3Writer().write(writer, metadata);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return metadataFile;
    }

    private boolean isSnapshot(@Nullable String version) {
        if (version != null) {
            if (version.regionMatches(true, version.length() - SNAPSHOT_VERSION.length(),
                SNAPSHOT_VERSION, 0, SNAPSHOT_VERSION.length())) {
                return true;
            } else {
                return VERSION_FILE_PATTERN.matcher(version).matches();
            }
        }
        return false;
    }

    @Nullable
    ExternalResourceReadResult<Metadata> readExistingMetadata(ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        return metadataRetryCaller.withBackoffAndRetry(new Callable<ExternalResourceReadResult<Metadata>>() {
            @Override
            @Nullable
            public ExternalResourceReadResult<Metadata> call() {
                return repository.resource(metadataResource).withContentIfPresent(inputStream -> {
                    try {
                        return new MetadataXpp3Reader().read(inputStream, false);
                    } catch (Exception e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                });
            }

            @Override
            public String toString() {
                return "GET " + metadataResource.getDisplayName();
            }
        });

    }

    protected abstract Metadata createSnapshotMetadata(MavenNormalizedPublication publication, String groupId, String artifactId, String version, ExternalResourceRepository repository, ExternalResourceName metadataResource);

    @NonNullApi
    private static class SnapshotMetadataResult {
        public final ExternalResourceName snapshotMetadataPath;
        public final Metadata snapshotMetadata;

        public SnapshotMetadataResult(ExternalResourceName snapshotMetadataPath, Metadata snapshotMetadata) {
            this.snapshotMetadataPath = snapshotMetadataPath;
            this.snapshotMetadata = snapshotMetadata;
        }

        /**
         * The timestamped version is hidden deep in `Metadata.versioning.snapshotVersions`
         *
         * @return The snapshot timestamped version
         */
        public String getVersion() {
            return snapshotMetadata.getVersioning().getSnapshotVersions().get(0).getVersion();
        }
    }

    /**
     * Publishes artifacts for a single Maven module.
     */
    private static class ModuleArtifactPublisher {
        private final NetworkOperationBackOffAndRetry<Void> networkOperationCaller = new NetworkOperationBackOffAndRetry<>();
        private final ExternalResourceRepository repository;
        private final boolean localRepo;
        private final URI rootUri;
        private final String groupPath;
        private final String artifactId;
        private final String moduleVersion;
        private String artifactVersion;

        ModuleArtifactPublisher(ExternalResourceRepository repository, boolean localRepo, URI rootUri, String groupId, String artifactId, String moduleVersion) {
            this.repository = repository.withProgressLogging();
            this.localRepo = localRepo;
            this.rootUri = rootUri;
            this.groupPath = groupId.replace('.', '/');
            this.artifactId = artifactId;
            this.moduleVersion = moduleVersion;
            this.artifactVersion = moduleVersion;
        }

        /**
         * Return the location of the module `maven-metadata.xml`, which lists all published versions for a Maven module.
         */
        ExternalResourceName getMetadataLocation() {
            String path = groupPath + '/' + artifactId + '/' + getMetadataFileName();
            return new ExternalResourceName(rootUri, path);
        }

        /**
         * Return the location of the snapshot `maven-metadata.xml`, which contains details of the latest published snapshot for a Maven module.
         */
        ExternalResourceName getSnapshotMetadataLocation() {
            String path = groupPath + '/' + artifactId + '/' + moduleVersion + '/' + getMetadataFileName();
            return new ExternalResourceName(rootUri, path);
        }

        private String getMetadataFileName() {
            if (localRepo) {
                return "maven-metadata-local.xml";
            }
            return "maven-metadata.xml";
        }

        /**
         * Publishes a single module artifact, based on classifier and extension.
         */
        void publish(@Nullable String classifier, String extension, File content) {
            StringBuilder path = new StringBuilder(128);
            path.append(groupPath).append('/');
            path.append(artifactId).append('/');
            path.append(moduleVersion).append('/');
            path.append(artifactId).append('-').append(artifactVersion);

            if (classifier != null) {
                path.append('-').append(classifier);
            }
            if (extension.length() > 0) {
                path.append('.').append(extension);
            }

            ExternalResourceName externalResource = new ExternalResourceName(rootUri, path.toString());
            publish(externalResource, content);
        }

        void publish(ExternalResourceName externalResource, File content) {
            if (!localRepo) {
                LOGGER.info("Uploading {} to {}", externalResource.getShortDisplayName(), externalResource.getPath());
            }
            putResource(externalResource, new FileReadableContent(content));
            if (!localRepo) {
                publishChecksums(externalResource, content);
            }
        }

        private void publishChecksums(ExternalResourceName destination, File content) {
            publishChecksum(destination, content, Hashing.sha1());
            publishChecksum(destination, content, Hashing.md5());
            if (!ExternalResourceResolver.disableExtraChecksums()) {
                publishPossiblyUnsupportedChecksum(destination, content, Hashing.sha256());
                publishPossiblyUnsupportedChecksum(destination, content, Hashing.sha512());
            }
        }

        private void publishPossiblyUnsupportedChecksum(ExternalResourceName destination, File content, HashFunction hashFunction) {
            try {
                publishChecksum(destination, content, hashFunction);
            } catch (Exception ex) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.warn("Cannot upload checksum for " + content.getName() + " because the remote repository doesn't support " + hashFunction + ". This will not fail the build.", ex);
                } else {
                    LOGGER.warn("Cannot upload checksum for " + content.getName() + " because the remote repository doesn't support " + hashFunction + ". This will not fail the build.");
                }
            }
        }

        private void publishChecksum(ExternalResourceName destination, File content, HashFunction hashFunction) {
            byte[] checksum = createChecksumFile(content, hashFunction);
            putResource(destination.append("." + hashFunction.getAlgorithm().toLowerCase(Locale.ROOT).replaceAll("-", "")), new ByteArrayReadableContent(checksum));
        }

        private byte[] createChecksumFile(File src, HashFunction hashFunction) {
            HashCode hash;
            try {
                hash = hashFunction.hashFile(src);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String formattedHashString = hash.toZeroPaddedString(hashFunction.getHexDigits());
            return formattedHashString.getBytes(StandardCharsets.US_ASCII);
        }

        private void putResource(ExternalResourceName externalResource, ReadableContent readableContent) {
            networkOperationCaller.withBackoffAndRetry(new Callable<Void>() {
                @Override
                public Void call() {
                    repository.resource(externalResource).put(readableContent);
                    return null;
                }

                @Override
                public String toString() {
                    return "PUT " + externalResource.getDisplayName();
                }
            });
        }
    }

}
