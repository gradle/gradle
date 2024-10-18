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
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.internal.Factory;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@NonNullApi
public class MavenRemotePublisher extends AbstractMavenPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRemotePublisher.class);
    private final BuildCommencedTimeProvider timeProvider;

    public MavenRemotePublisher(Factory<File> temporaryDirFactory, BuildCommencedTimeProvider timeProvider) {
        super(temporaryDirFactory);
        this.timeProvider = timeProvider;
    }

    @Override
    public void publish(MavenNormalizedPublication publication, @Nullable MavenArtifactRepository artifactRepository) {
        assert artifactRepository != null;
        URI repositoryUrl = artifactRepository.getUrl();
        LOGGER.info("Publishing to repository '{}' ({})", artifactRepository.getName(), repositoryUrl);

        String protocol = repositoryUrl.getScheme().toLowerCase(Locale.ROOT);
        DefaultMavenArtifactRepository realRepository = (DefaultMavenArtifactRepository) artifactRepository;
        RepositoryTransport transport = realRepository.getTransport(protocol);
        ExternalResourceRepository repository = transport.getRepository();

        publish(publication, repository, repositoryUrl, false);
    }

    @Override
    protected Metadata createSnapshotMetadata(MavenNormalizedPublication publication, String groupId, String artifactId, String version, ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        Metadata metadata = new Metadata();
        metadata.setModelVersion("1.1.0");
        metadata.setGroupId(groupId);
        metadata.setArtifactId(artifactId);
        metadata.setVersion(version);

        String timestamp = createSnapshotTimestamp();

        Snapshot snapshot = new Snapshot();
        snapshot.setBuildNumber(getNextBuildNumber(repository, metadataResource));
        snapshot.setTimestamp(timestamp);

        Versioning versioning = new Versioning();
        versioning.setSnapshot(snapshot);
        versioning.setLastUpdated(snapshot.getTimestamp().replace(".", ""));

        String timestampVersion = version.replace("SNAPSHOT", snapshot.getTimestamp() + "-" + snapshot.getBuildNumber());
        for (NormalizedMavenArtifact artifact : publication.getAllArtifacts()) {
            SnapshotVersion sv = new SnapshotVersion();
            sv.setClassifier(artifact.getClassifier());
            sv.setExtension(artifact.getExtension());
            sv.setVersion(timestampVersion);
            sv.setUpdated(versioning.getLastUpdated());

            versioning.getSnapshotVersions().add(sv);
        }

        metadata.setVersioning(versioning);

        return metadata;
    }

    private String createSnapshotTimestamp() {
        DateFormat utcDateFormatter = new SimpleDateFormat("yyyyMMdd.HHmmss");
        utcDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date currentTime = new Date(timeProvider.getCurrentTime());
        return utcDateFormatter.format(currentTime);
    }

    private int getNextBuildNumber(ExternalResourceRepository repository, ExternalResourceName metadataResource) {
        ExternalResourceReadResult<Metadata> existing = readExistingMetadata(repository, metadataResource);

        if (existing != null) {
            Metadata recessive = existing.getResult();
            if (recessive != null) {
                Versioning versioning = recessive.getVersioning();
                if (versioning != null) {
                    Snapshot snapshot = versioning.getSnapshot();
                    if (snapshot != null && snapshot.getBuildNumber() > 0) {
                        return snapshot.getBuildNumber() + 1;
                    }
                }
            }
        }
        return 1;
    }
}
