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

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.gradle.api.GradleException;

public class WagonRegistry {
    private static final String FAILED_TO_REGISTER_WAGON = "Failed to register wagon";
    private PlexusContainer plexusContainer;

    public WagonRegistry(PlexusContainer plexusContainer) {
        this.plexusContainer = plexusContainer;
    }

    public void registerProtocol(String protocol) {
        try {
            ComponentDescriptor componentDescriptor = new ComponentDescriptor();
            componentDescriptor.setRole(Wagon.ROLE);
            componentDescriptor.setRoleHint(protocol);
            componentDescriptor.setImplementation(RepositoryTransportDeployWagon.class.getCanonicalName());

            plexusContainer.addComponentDescriptor(componentDescriptor);
        } catch (ComponentRepositoryException e) {
            throw new GradleException(FAILED_TO_REGISTER_WAGON, e);
        }
    }
}
