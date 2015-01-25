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
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.tools.ant.BuildException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * We could also use reflection to get hold of the container property. But this would make it harder to use a Mock for this class.
 */
public class MavenDeploy extends AbstractMavenPublish {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenDeploy.class);

    private RemoteRepository remoteRepository;

    private RemoteRepository remoteSnapshotRepository;

    private boolean uniqueVersion = true;

    public MavenDeploy(File pomFile) {
        super(pomFile);
    }

    public void setRepositories(RemoteRepository repository, RemoteRepository snapshotRepository) {
        this.remoteRepository = repository;
        this.remoteSnapshotRepository = snapshotRepository;
    }

    public void setUniqueVersion(boolean uniqueVersion) {
        this.uniqueVersion = uniqueVersion;
    }

    protected void publishArtifact(Artifact artifact, File artifactFile, ArtifactRepository localRepo) {
        ArtifactDeployer deployer = (ArtifactDeployer) lookup(ArtifactDeployer.ROLE);
        ArtifactRepository deploymentRepository = getRemoteArtifactRepository(artifact);

        LOGGER.info("Deploying to " + deploymentRepository.getUrl());

        try {
            deployer.deploy(artifactFile, artifact, deploymentRepository, localRepo);
        } catch (ArtifactDeploymentException e) {
            throw new BuildException("Error deploying artifact '" + artifact.getDependencyConflictId() + "': " + e.getMessage(), e);
        }
    }

    private ArtifactRepository getRemoteArtifactRepository(Artifact artifact) {

        if (artifact.isSnapshot() && remoteSnapshotRepository != null) {
            return createDeploymentArtifactRepository(remoteSnapshotRepository);
        }

        if (remoteRepository == null) {
            throw new GradleException("Must specify a repository for deployment");
        }

        return createDeploymentArtifactRepository(remoteRepository);
    }

    /**
     * Create a core-Maven deployment ArtifactRepository from a Maven Ant Tasks's RemoteRepository definition.
     *
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepository
     */
    private ArtifactRepository createDeploymentArtifactRepository(RemoteRepository repository) {
        if (repository.getId().equals(repository.getUrl())) {
            // MANTTASKS-103: avoid default id set to the url, since it is used for maven-metadata-<id>.xml
            repository.setId("remote");
        }

        ArtifactRepositoryLayout repositoryLayout = (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, repository.getLayout());
        ArtifactRepositoryFactory repositoryFactory = null;

        try {
            repositoryFactory = getArtifactRepositoryFactory(repository);
            return repositoryFactory.createDeploymentArtifactRepository(repository.getId(), repository.getUrl(), repositoryLayout, uniqueVersion);
        } finally {
            releaseArtifactRepositoryFactory(repositoryFactory);
        }
    }

    private void releaseArtifactRepositoryFactory(ArtifactRepositoryFactory repositoryFactory) {
        try {
            getContainer().release(repositoryFactory);
        } catch (ComponentLifecycleException e) {
            // TODO: Warn the user, or not?
        }
    }

    public void addWagonJar(File jar) {
        try {
            getContainer().addJarResource(jar);
        } catch (PlexusContainerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a core-Maven ArtifactRepositoryFactory from a Maven-Ant RemoteRepository definition, eventually configured with authentication and proxy information.
     *
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepositoryFactory
     */
    private ArtifactRepositoryFactory getArtifactRepositoryFactory(RemoteRepository repository) {
        WagonManager manager = (WagonManager) lookup(WagonManager.ROLE);

        Authentication authentication = repository.getAuthentication();
        if (authentication != null) {
            manager.addAuthenticationInfo(repository.getId(), authentication.getUserName(),
                    authentication.getPassword(), authentication.getPrivateKey(),
                    authentication.getPassphrase());
        }

        Proxy proxy = repository.getProxy();
        if (proxy != null) {
            manager.addProxy(proxy.getType(), proxy.getHost(), proxy.getPort(), proxy.getUserName(),
                    proxy.getPassword(), proxy.getNonProxyHosts());
        }

        return (ArtifactRepositoryFactory) lookup(ArtifactRepositoryFactory.ROLE);
    }
}
