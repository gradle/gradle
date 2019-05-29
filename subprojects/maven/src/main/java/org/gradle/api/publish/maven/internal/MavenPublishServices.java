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

package org.gradle.api.publish.maven.internal;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.publication.maven.internal.MavenFactory;
import org.gradle.api.publication.maven.internal.MavenVersionRangeMapper;
import org.gradle.api.publication.maven.internal.VersionRangeMapper;
import org.gradle.api.publication.maven.internal.pom.DefaultMavenFactory;
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker;
import org.gradle.api.publish.maven.internal.publisher.MavenDuplicatePublicationTracker;
import org.gradle.api.publish.maven.internal.publisher.MavenPublishers;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.gradle.util.BuildCommencedTimeProvider;

public class MavenPublishServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ComponentRegistrationAction());
    }

    private static class ComponentRegistrationAction {
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            // TODO There should be a more explicit way to execute an action against existing services
            componentTypeRegistry.maybeRegisterComponentType(MavenModule.class)
                    .registerArtifactType(MavenPomArtifact.class, ArtifactType.MAVEN_POM);
        }

        public MavenFactory createMavenFactory(VersionRangeMapper versionRangeMapper) {
            return new DefaultMavenFactory(versionRangeMapper);
        }

        public VersionRangeMapper createVersionRangeMapper(VersionSelectorScheme versionSelectorScheme) {
            return new MavenVersionRangeMapper(versionSelectorScheme);
        }

        public MavenPublishers createMavenPublishers(RepositoryTransportFactory repositoryTransportFactory, BuildCommencedTimeProvider timeProvider, LocalMavenRepositoryLocator mavenRepositoryLocator) {
            return new MavenPublishers(repositoryTransportFactory, timeProvider, mavenRepositoryLocator);
        }

        public MavenDuplicatePublicationTracker createDuplicatePublicationTracker(DuplicatePublicationTracker duplicatePublicationTracker, LocalMavenRepositoryLocator mavenRepositoryLocator) {
            return new MavenDuplicatePublicationTracker(duplicatePublicationTracker, mavenRepositoryLocator);
        }
    }
}
