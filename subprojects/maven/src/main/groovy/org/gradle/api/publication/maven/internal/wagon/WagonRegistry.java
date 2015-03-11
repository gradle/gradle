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

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;

import java.util.HashSet;
import java.util.Set;

public class WagonRegistry {
    private static final String SINGLETON_INSTANTIATION_STRATEGY = "singleton";
    private static final String FAILED_TO_REGISTER_WAGON = "Failed to register wagon";
    private final Set<String> protocols = new HashSet<String>();
    private PlexusContainer plexusContainer;

    public WagonRegistry(PlexusContainer plexusContainer) {
        this.plexusContainer = plexusContainer;
        add("s3");
        add("sftp");
        //Add other transports here
//        add("http");
//        add("https");
    }

    private void add(String protocol) {
        this.protocols.add(protocol.toLowerCase());
    }

    // TODO:DAZ Only need to register a single wagon, since it's backed by a thread context state
    public void registerAll() {
        try {
            for (String protocol : protocols) {
                ComponentDescriptor componentDescriptor = new ComponentDescriptor();
                componentDescriptor.setRole(Wagon.ROLE);
                componentDescriptor.setRoleHint(protocol);
                componentDescriptor.setImplementation(RepositoryTransportDeployWagon.class.getCanonicalName());

                //Must be a singleton so we can configure the wagon - otherwise plexus creates a new instance on every lookup
                componentDescriptor.setInstantiationStrategy(SINGLETON_INSTANTIATION_STRATEGY);
                plexusContainer.addComponentDescriptor(componentDescriptor);

                //Get the wagon early
                RepositoryTransportDeployWagon wagon = (RepositoryTransportDeployWagon) wagonManager().getWagon(protocol);
            }
        } catch (UnsupportedProtocolException e) {
            throwWagonException(FAILED_TO_REGISTER_WAGON, e);
        } catch (ComponentRepositoryException e) {
            throwWagonException(FAILED_TO_REGISTER_WAGON, e);
        }
    }

    private void throwWagonException(String message, Exception e) {
        throw new GradleException(message, e);
    }

    public void prepareForPublish(MavenArtifactRepository artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
        String protocol = artifactRepository.getUrl().getScheme().toLowerCase();
        if (protocols.contains(protocol)) {
            try {
                RepositoryTransportDeployWagon wagon = (RepositoryTransportDeployWagon) wagonManager().getWagon(protocol);
                wagon.createDelegate(protocol, artifactRepository, repositoryTransportFactory);
            } catch (UnsupportedProtocolException e) {
                throwWagonException("Failed to configure wagon for the protocol:" + protocol, e);
            }
        }

    }

    private WagonManager wagonManager() {
        try {
            return (WagonManager) plexusContainer.lookup(WagonManager.ROLE);
        } catch (ComponentLookupException e) {
            throwWagonException("Failed to lookup wagon manager", e);
        }
        return null;
    }
}
