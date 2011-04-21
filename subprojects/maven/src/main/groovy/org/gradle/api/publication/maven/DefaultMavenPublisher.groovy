package org.gradle.api.publication.maven

import org.apache.maven.repository.internal.MavenRepositorySystemSession
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.repository.LocalRepository
import org.sonatype.aether.installation.InstallRequest
import org.sonatype.aether.artifact.Artifact
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.util.artifact.SubArtifact
import org.sonatype.aether.deployment.DeployRequest
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory
import org.apache.maven.repository.internal.DefaultServiceLocator

class DefaultMavenPublisher implements MavenPublisher {
    private final RepositorySystem repositorySystem
    private final RepositorySystemSession session

    DefaultMavenPublisher() {
        repositorySystem = createRepositorySystem()
        session = createRepositorySystemSession(repositorySystem)
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
        locator.getService(RepositorySystem)
    }

    private RepositorySystemSession createRepositorySystemSession(RepositorySystem repositorySystem) {
        def session = new MavenRepositorySystemSession()
        def localRepo = new LocalRepository(new File("/swd/tmp/m2repo"))
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(localRepo)
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
        result
    }
}
