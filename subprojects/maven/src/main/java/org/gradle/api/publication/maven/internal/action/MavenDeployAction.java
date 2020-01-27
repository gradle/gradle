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

import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.GradleException;
import org.gradle.api.publish.PublishRetrierException;
import org.gradle.api.publish.internal.DefaultPublishRetrier;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.util.repository.DefaultProxySelector;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class MavenDeployAction extends AbstractMavenPublishAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenDeployAction.class);

    private final static String MAX_DEPLOY_ATTEMPTS = "org.gradle.internal.remote.repository.deploy.max.attempts";
    private final static String INITIAL_BACKOFF_MS = "org.gradle.internal.remote.repository.deploy.initial.backoff";
    private RemoteRepository remoteRepository;
    private RemoteRepository remoteSnapshotRepository;
    private final int maxDeployAttempts;
    private final int initialBackOff;

    public MavenDeployAction(String packaging, MavenProjectIdentity projectIdentity, List<File> wagonJars) {
        super(packaging, projectIdentity, wagonJars);
        this.maxDeployAttempts = Integer.getInteger(MAX_DEPLOY_ATTEMPTS, 3);
        this.initialBackOff = Integer.getInteger(INITIAL_BACKOFF_MS, 1000);
    }

    public void setRepositories(RemoteRepository repository, RemoteRepository snapshotRepository) {
        this.remoteRepository = repository;
        this.remoteSnapshotRepository = snapshotRepository;
    }

    @Override
    protected void publishArtifacts(Collection<Artifact> artifacts, final RepositorySystem repositorySystem, final RepositorySystemSession session) throws DeploymentException {
        RemoteRepository gradleRepo = remoteRepository;
        if (artifacts.iterator().next().isSnapshot() && remoteSnapshotRepository != null) {
            gradleRepo = remoteSnapshotRepository;
        }
        if (gradleRepo == null) {
            throw new GradleException("Must specify a repository for deployment");
        }

        org.sonatype.aether.repository.RemoteRepository aetherRepo = createRepository(gradleRepo);

        final DeployRequest request = new DeployRequest();
        request.setRepository(aetherRepo);
        for (Artifact artifact : artifacts) {
            request.addArtifact(artifact);
        }

        LOGGER.info("Deploying to {}", gradleRepo.getUrl());

        DefaultPublishRetrier deployRetrier = new DefaultPublishRetrier(
            () -> {
                repositorySystem.deploy(session, request);
                return null;
                },
            remoteRepository.getUrl(),
            maxDeployAttempts,
            initialBackOff
        );

        try {
            deployRetrier.publishWithRetry();
        } catch (PublishRetrierException e) {
            throw new DeploymentException(e.getMessage(), e);
        }
    }

    private org.sonatype.aether.repository.RemoteRepository createRepository(RemoteRepository gradleRepo) {
        org.sonatype.aether.repository.RemoteRepository repo = new org.sonatype.aether.repository.RemoteRepository("remote", gradleRepo.getLayout(), gradleRepo.getUrl());

        org.apache.maven.artifact.ant.Authentication auth = gradleRepo.getAuthentication();
        if (auth != null) {
            repo.setAuthentication(new Authentication(auth.getUserName(), auth.getPassword(), auth.getPrivateKey(), auth.getPassphrase()));
        }

        org.apache.maven.artifact.ant.Proxy proxy = gradleRepo.getProxy();
        if (proxy != null) {
            DefaultProxySelector proxySelector = new DefaultProxySelector();
            Authentication proxyAuth = new Authentication(proxy.getUserName(), proxy.getPassword());
            proxySelector.add(new Proxy(proxy.getType(), proxy.getHost(), proxy.getPort(), proxyAuth), proxy.getNonProxyHosts());
            repo.setProxy(proxySelector.getProxy(repo));
        }

        return repo;
    }

}
