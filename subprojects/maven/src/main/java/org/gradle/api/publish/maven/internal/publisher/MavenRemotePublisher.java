/*
 * Copyright 2012 the original author or authors.
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

import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.wagon.Wagon;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.publication.maven.internal.action.MavenDeployAction;
import org.gradle.api.publication.maven.internal.action.MavenPublishAction;
import org.gradle.api.publication.maven.internal.wagon.RepositoryTransportDeployWagon;
import org.gradle.api.publication.maven.internal.wagon.RepositoryTransportWagonAdapter;
import org.gradle.internal.Factory;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.internal.logging.LoggingManagerInternal;

import java.io.File;
import java.net.URI;

public class MavenRemotePublisher extends AbstractMavenPublisher {
    private final Factory<File> temporaryDirFactory;
    private final RepositoryTransportFactory repositoryTransportFactory;

    public MavenRemotePublisher(Factory<LoggingManagerInternal> loggingManagerFactory, LocalMavenRepositoryLocator mavenRepositoryLocator, Factory<File> temporaryDirFactory, RepositoryTransportFactory repositoryTransportFactory) {
        super(loggingManagerFactory, mavenRepositoryLocator);
        this.temporaryDirFactory = temporaryDirFactory;
        this.repositoryTransportFactory = repositoryTransportFactory;
    }

    protected MavenPublishAction createDeployTask(File pomFile, LocalMavenRepositoryLocator mavenRepositoryLocator, MavenArtifactRepository artifactRepository) {
        GradleWagonMavenDeployAction deployTask = new GradleWagonMavenDeployAction(pomFile, artifactRepository, repositoryTransportFactory);
        deployTask.setLocalMavenRepositoryLocation(temporaryDirFactory.create());
        deployTask.setRepositories(createMavenRemoteRepository(artifactRepository), null);
        return deployTask;
    }

    private RemoteRepository createMavenRemoteRepository(MavenArtifactRepository repository) {
        RemoteRepository remoteRepository = new RemoteRepository();
        remoteRepository.setUrl(repository.getUrl().toString());
        return remoteRepository;
    }

    /**
     * A deploy action that uses a Gradle provided wagon implementation.
     */
    private static class GradleWagonMavenDeployAction extends MavenDeployAction {
        private final MavenArtifactRepository artifactRepository;
        private final RepositoryTransportFactory repositoryTransportFactory;

        public GradleWagonMavenDeployAction(File pomFile, MavenArtifactRepository artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
            super(pomFile, null);
            this.artifactRepository = artifactRepository;
            this.repositoryTransportFactory = repositoryTransportFactory;

            registerWagonProtocols();
        }

        private void registerWagonProtocols() {
            Wagon wagon = new RepositoryTransportDeployWagon();
            for (String protocol : repositoryTransportFactory.getRegisteredProtocols()) {
                getContainer().addComponent(wagon, Wagon.class, protocol);
            }
        }

        @Override
        public void publish() {
            String protocol = artifactRepository.getUrl().getScheme().toLowerCase();
            RepositoryTransportWagonAdapter adapter = createAdapter(protocol, artifactRepository, repositoryTransportFactory);
            RepositoryTransportDeployWagon.contextualize(adapter);
            try {
                super.publish();
            } finally {
                RepositoryTransportDeployWagon.decontextualize();
            }
        }

        private RepositoryTransportWagonAdapter createAdapter(String protocol, MavenArtifactRepository artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
            RepositoryTransport transport = repositoryTransportFactory.createTransport(protocol, artifactRepository.getName(),
                    ((AuthenticationSupportedInternal)artifactRepository).getConfiguredAuthentication());
            URI rootUri = artifactRepository.getUrl();
            return new RepositoryTransportWagonAdapter(transport, rootUri);
        }
    }
}
