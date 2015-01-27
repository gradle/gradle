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

package org.gradle.api.publication.maven.internal;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.internal.resource.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class RepositoryTransportDeployDelegate {

    private final RepositoryTransport transport;
    private final MavenArtifactRepository artifactRepository;

    public RepositoryTransportDeployDelegate(String protocol, MavenArtifactRepository artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
        this.artifactRepository = artifactRepository;
        transport = repositoryTransportFactory.createTransport(protocol, artifactRepository.getName(), artifactRepository.getAlternativeCredentials());
    }

    public boolean getAndWriteFile(File destination, String resourceName) throws IOException, ResourceDoesNotExistException {
        URI uriForResource = getUriForResource(resourceName);
        try {
            ExternalResource resource = transport.getRepository().getResource(uriForResource);
            if (null != resource) {
                resource.writeTo(destination);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public void putFile(File file, String resourceName) throws IOException {
        transport.getRepository().putWithoutChecksum(file, getUriForResource(resourceName));
    }

    private URI getUriForResource(String resource) {
        String base = this.artifactRepository.getUrl().toString();
        StringBuilder sb = new StringBuilder();
        sb.append(base);
        if (!base.endsWith("/") && !resource.startsWith("/")) {
            sb.append("/");
        }
        sb.append(resource);
        try {
            return new URI(sb.toString());
        } catch (URISyntaxException e) {
            throw new GradleException("Could not create URL for resource", e);
        }
    }
}
