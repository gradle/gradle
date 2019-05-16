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

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.internal.resource.ExternalResourceRepository;

import java.io.File;
import java.net.URI;

public class MavenRemotePublisher extends AbstractMavenPublisher {

    public MavenRemotePublisher(Factory<File> temporaryDirFactory, RepositoryTransportFactory repositoryTransportFactory) {
        super(temporaryDirFactory, repositoryTransportFactory);
    }

    @Override
    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        String protocol = artifactRepository.getUrl().getScheme().toLowerCase();
        RepositoryTransport transport = repositoryTransportFactory.createTransport(protocol, artifactRepository.getName(),
                ((AuthenticationSupportedInternal) artifactRepository).getConfiguredAuthentication());
        ExternalResourceRepository repository = transport.getRepository();

        URI rootUri = artifactRepository.getUrl();

        publish(publication, repository, rootUri, false);
    }
}
