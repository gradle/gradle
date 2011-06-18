/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.publication.maven.internal

import org.apache.maven.repository.internal.DefaultServiceLocator
import org.apache.maven.repository.internal.MavenRepositorySystemSession
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.MavenPublisher
import org.gradle.api.publication.maven.MavenRepository
import org.gradle.util.SystemProperties
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.artifact.Artifact
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory
import org.sonatype.aether.connector.wagon.WagonProvider
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory
import org.sonatype.aether.deployment.DeployRequest
import org.sonatype.aether.installation.InstallRequest
import org.sonatype.aether.repository.Authentication
import org.sonatype.aether.repository.LocalRepository
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.util.artifact.SubArtifact

class DefaultMavenPublisher implements MavenPublisher {
    private final RepositorySystem repositorySystem
    private final RepositorySystemSession session

    DefaultMavenPublisher() {
        this(new LocalRepository("$SystemProperties.userHome/.m2/repository"))
    }

    DefaultMavenPublisher(LocalRepository localRepository) {
        repositorySystem = createRepositorySystem()
        session = createRepositorySystemSession(repositorySystem, localRepository)
    }

    void install(MavenPublication publication) {
        def request = new InstallRequest()
        request.artifacts = convertArtifacts(publication)
        repositorySystem.install(session, request)
    }

    void deploy(MavenPublication publication, MavenRepository repository) {
        def request = new DeployRequest()
        request.artifacts = convertArtifacts(publication)
        request.repository = convertRepository(repository)
        repositorySystem.deploy(session, request)
    }

    private RepositorySystem createRepositorySystem() {
        def locator = new DefaultServiceLocator()
        locator.addService(RepositoryConnectorFactory, FileRepositoryConnectorFactory)
        locator.addService(RepositoryConnectorFactory, WagonRepositoryConnectorFactory)
        locator.setServices(WagonProvider.class, new ManualWagonProvider());
        locator.getService(RepositorySystem)
    }

    private RepositorySystemSession createRepositorySystemSession(RepositorySystem repositorySystem, LocalRepository localRepository) {
        def session = new MavenRepositorySystemSession()
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(localRepository)
        session
    }

    private List<Artifact> convertArtifacts(MavenPublication publication) {
        def result = []
        def mainArtifact = new DefaultArtifact(publication.groupId, publication.artifactId,
                publication.mainArtifact.classifier, publication.mainArtifact.extension,
                publication.version, [:], publication.mainArtifact.file)
        result << mainArtifact
        result.addAll(publication.subArtifacts.collect { subArtifact ->
            new SubArtifact(mainArtifact, subArtifact.classifier, subArtifact.extension, subArtifact.file)
        })
        result
    }

    private RemoteRepository convertRepository(MavenRepository repository) {
        def result = new RemoteRepository()
        result.url = repository.url
        result.contentType = "default" // FileRepositoryConnectorFactory doesn’t accept any other content type
        def auth = repository.authentication
        if (auth) {
            result.authentication = new Authentication(auth.userName, auth.password)
        }
        result
    }
}
