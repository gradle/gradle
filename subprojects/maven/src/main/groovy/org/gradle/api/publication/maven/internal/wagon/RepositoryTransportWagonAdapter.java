/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.publication.maven.internal.wagon;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.internal.artifacts.repositories.MavenArtifactRepositoryInternal;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceException;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class RepositoryTransportWagonAdapter {
    private final RepositoryTransport transport;
    private final URI rootUri;

    public RepositoryTransportWagonAdapter(String protocol, MavenArtifactRepositoryInternal artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
        transport = repositoryTransportFactory.createTransport(protocol, artifactRepository.getName(), artifactRepository.getAlternativeCredentials());
        rootUri = artifactRepository.getUrl();
    }

    public boolean getRemoteFile(File destination, String resourceName) throws ResourceException, ResourceDoesNotExistException {
        URI uriForResource = getUriForResource(resourceName);
        ExternalResource resource = transport.getRepository().getResource(uriForResource);
        if (resource == null) {
            return false;
        }
        try {
            resource.writeTo(destination);
        } finally {
            resource.close();
        }
        return true;
    }

    public void putRemoteFile(File file, String resourceName) throws IOException {
        transport.getRepository().withProgressLogging().put(file, getUriForResource(resourceName));
    }

    private URI getUriForResource(String resource) {
        ExternalResourceName resourceName = new ExternalResourceName(rootUri, resource);
        return resourceName.getUri();
    }
}
