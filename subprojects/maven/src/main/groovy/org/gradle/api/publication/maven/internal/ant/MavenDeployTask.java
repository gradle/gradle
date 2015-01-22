/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.Proxy;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainer;

import java.io.File;
import java.util.List;

/**
 * We could also use reflection to get hold of the container property. But this would make it harder to use a Mock for this class.
 */
public class MavenDeployTask extends BaseMavenPublishTask {
    private RemoteRepository remoteRepository;

    private RemoteRepository remoteSnapshotRepository;

    private boolean uniqueVersion = true;

    protected MavenDeployTask(File pomFile) {
        super(pomFile);
    }

    protected void doPublish(Artifact artifact, File pomFile, ArtifactRepository localRepo) {
        ArtifactDeployer deployer = (ArtifactDeployer) lookup(ArtifactDeployer.ROLE);
        ArtifactRepository deploymentRepository = getRemoteArtifactRepository(artifact);
        log("Deploying to " + deploymentRepository.getUrl(), Project.MSG_INFO);

        try {
            deployer.deploy(pomFile, artifact, deploymentRepository, localRepo);
        } catch (ArtifactDeploymentException e) {
            throw new BuildException("Error deploying artifact '" + artifact.getDependencyConflictId() + "': " + e.getMessage(), e);
        }
    }

    private ArtifactRepository getRemoteArtifactRepository(Artifact artifact) {
        if (remoteSnapshotRepository == null) {
            remoteSnapshotRepository = remoteRepository;
        }

        ArtifactRepository deploymentRepository;
        if (artifact.isSnapshot() && remoteSnapshotRepository != null) {
            deploymentRepository = createDeploymentArtifactRepository(remoteSnapshotRepository);
        } else if (remoteRepository != null) {
            deploymentRepository = createDeploymentArtifactRepository(remoteRepository);
        } else {
            throw new BuildException("A remoteRepository element is required to deploy");
        }

        return deploymentRepository;
    }

    /**
     * Create a core-Maven deployment ArtifactRepository from a Maven Ant Tasks's RemoteRepository definition.
     *
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepository
     */
    protected ArtifactRepository createDeploymentArtifactRepository(RemoteRepository repository) {
        if (repository.getId().equals(repository.getUrl())) {
            // MANTTASKS-103: avoid default id set to the url, since it is used for maven-metadata-<id>.xml
            repository.setId("remote");
        }

        updateRepositoryWithSettings(repository);

        ArtifactRepositoryLayout repositoryLayout =
                (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, repository.getLayout());

        ArtifactRepositoryFactory repositoryFactory = null;

        ArtifactRepository artifactRepository;

        try {
            repositoryFactory = getArtifactRepositoryFactory(repository);

            artifactRepository =
                    repositoryFactory.createDeploymentArtifactRepository(repository.getId(), repository.getUrl(),
                            repositoryLayout, uniqueVersion);
        } finally {
            releaseArtifactRepositoryFactory(repositoryFactory);
        }

        return artifactRepository;
    }

    protected void updateRepositoryWithSettings(RemoteRepository repository) {
        // TODO: actually, we need to not funnel this through the ant repository - we should pump settings into wagon
        // manager at the start like m2 does, and then match up by repository id
        // As is, this could potentially cause a problem with 2 remote repositories with different authentication info

        Mirror mirror = getMirror(getSettings().getMirrors(), repository);
        if (mirror != null) {
            repository.setUrl(mirror.getUrl());
            repository.setId(mirror.getId());
        }

        if (repository.getAuthentication() == null) {
            Server server = getSettings().getServer(repository.getId());
            if (server != null) {
                repository.addAuthentication(new Authentication(server));
            }
        }

        if (repository.getProxy() == null) {
            org.apache.maven.settings.Proxy proxy = getSettings().getActiveProxy();
            if (proxy != null) {
                repository.addProxy(new Proxy(proxy));
            }
        }
    }

    /**
     * This method finds a matching mirror for the selected repository. If there is an exact match, this will be used. If there is no exact match, then the list of mirrors is examined to see if a
     * pattern applies.
     *
     * @param mirrors The available mirrors.
     * @param repository See if there is a mirror for this repository.
     * @return the selected mirror or null if none is found.
     */
    private Mirror getMirror(List<Mirror> mirrors, RemoteRepository repository) {
        String repositoryId = repository.getId();

        if (repositoryId != null) {
            for (Mirror mirror : mirrors) {
                if (repositoryId.equals(mirror.getMirrorOf())) {
                    return mirror;
                }
            }

            for (Mirror mirror : mirrors) {
                if (matchPattern(repository, mirror.getMirrorOf())) {
                    return mirror;
                }
            }
        }

        return null;
    }

    public void setRepositories(RemoteRepository repository, RemoteRepository snapshotRepository) {
        this.remoteRepository = repository;
        this.remoteSnapshotRepository = snapshotRepository;
    }

    public void setUniqueVersion(boolean uniqueVersion) {
        this.uniqueVersion = uniqueVersion;
    }

    @Override
    public synchronized Settings getSettings() {
        return super.getSettings();
    }

    @Override
    public synchronized PlexusContainer getContainer() {
        return super.getContainer();
    }


}
