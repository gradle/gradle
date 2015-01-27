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

package org.gradle.api.publication.maven.internal.ant;

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
import org.gradle.api.publication.maven.internal.RepositoryTransportDeployWagon;

import java.util.HashSet;
import java.util.Set;

public class WagonRegistry {
    private static final String SINGLETON_INSTANTIATION_STRATEGY = "singleton";
    private static final String FAILED_TO_REGISTER_WAGON = "Failed to register wagon";
    private final Set<String> wagonDeployments = new HashSet<String>();

    public WagonRegistry() {
        add("s3");
        //Add other transports here
//        add("http");
//        add("https");
//        add("sftp");
    }

    private void add(String protocol) {
        this.wagonDeployments.add(protocol.toLowerCase());
    }

    public void register(MavenDeploy mavenDeploy, MavenArtifactRepository artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
        PlexusContainer container = mavenDeploy.getContainer();
        String protocol = artifactRepository.getUrl().getScheme().toLowerCase();
        if (wagonDeployments.contains(protocol)) {
            try {
                WagonManager wagonManager = (WagonManager) container.lookup(WagonManager.ROLE);
                ComponentDescriptor componentDescriptor = new ComponentDescriptor();
                componentDescriptor.setRole(Wagon.ROLE);
                componentDescriptor.setRoleHint(protocol);
                componentDescriptor.setImplementation(RepositoryTransportDeployWagon.class.getCanonicalName());

                //Must be a singleton so we can configure the wagon - otherwise plexus creates a new instance on every lookup
                componentDescriptor.setInstantiationStrategy(SINGLETON_INSTANTIATION_STRATEGY);
                container.addComponentDescriptor(componentDescriptor);

                //Need to get the wagon so that plexus/maven creates it at this point - under the hood maven is using a reflective newInstance()
                RepositoryTransportDeployWagon wagon = (RepositoryTransportDeployWagon) wagonManager.getWagon(protocol);
                wagon.createDelegate(protocol, artifactRepository, repositoryTransportFactory);

            } catch (ComponentLookupException e) {
                throwWagonException(FAILED_TO_REGISTER_WAGON, e);
            } catch (UnsupportedProtocolException e) {
                throwWagonException(FAILED_TO_REGISTER_WAGON, e);
            } catch (ComponentRepositoryException e) {
                throwWagonException(FAILED_TO_REGISTER_WAGON, e);
            }
        }
    }

    private void throwWagonException(String message, Exception e) {
        throw new GradleException(message, e);
    }
}
