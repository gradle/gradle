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
package org.gradle.api.publication.maven.internal.action;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ant.Authentication;
import org.apache.maven.artifact.ant.Proxy;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class MavenDeployAction extends AbstractMavenPublishAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenDeployAction.class);

    private RemoteRepository remoteRepository;

    private RemoteRepository remoteSnapshotRepository;

    private boolean uniqueVersion = true;

    public MavenDeployAction(File pomFile) {
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
        ArtifactDeployer deployer = lookup(ArtifactDeployer.class);
        ArtifactRepository deploymentRepository = getRemoteArtifactRepository(artifact);

        LOGGER.info("Deploying to " + deploymentRepository.getUrl());

        try {
            deployer.deploy(artifactFile, artifact, deploymentRepository, localRepo);
        } catch (ArtifactDeploymentException e) {
            throw new GradleException("Error deploying artifact '" + artifact.getDependencyConflictId() + "': " + e.getMessage(), e);
        }
    }

    private ArtifactRepository getRemoteArtifactRepository(Artifact artifact) {
        RemoteRepository deploymentRepository = remoteRepository;
        if (artifact.isSnapshot() && remoteSnapshotRepository != null) {
            deploymentRepository = remoteSnapshotRepository;
        }

        if (deploymentRepository == null) {
            throw new GradleException("Must specify a repository for deployment");
        }

        // The repository id is used for `maven-metadata-${repository.id}.xml`, and to match credentials to repository.
        initWagonManagerWithRepositorySettings("remote", deploymentRepository);
        return new DefaultArtifactRepository("remote", deploymentRepository.getUrl(), new DefaultRepositoryLayout(), uniqueVersion);
    }

    private void initWagonManagerWithRepositorySettings(String repositoryId, RemoteRepository repository) {
        Authentication authentication = repository.getAuthentication();
        if (authentication != null) {
            wagonManager.addAuthenticationInfo(repositoryId, authentication.getUserName(),
                    authentication.getPassword(), authentication.getPrivateKey(),
                    authentication.getPassphrase());
        }

        Proxy proxy = repository.getProxy();
        if (proxy != null) {
            wagonManager.addProxy(proxy.getType(), proxy.getHost(), proxy.getPort(), proxy.getUserName(),
                    proxy.getPassword(), proxy.getNonProxyHosts());
        }
    }
}
