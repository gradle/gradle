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
import org.gradle.api.publish.maven.internal.dependencies.MavenVersionRangeMapper;
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper;
import org.gradle.api.publish.maven.internal.publisher.MavenDuplicatePublicationTracker;
import org.gradle.api.publish.maven.internal.publisher.MavenPublishers;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.gradle.util.internal.BuildCommencedTimeProvider;

public class MavenPublishServices extends AbstractGradleModuleServices {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ComponentRegistrationAction());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.add(MavenDuplicatePublicationTracker.class);
    }

    private static class ComponentRegistrationAction implements ServiceRegistrationProvider {
        @SuppressWarnings("UnusedVariable")
        @Provides
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            // TODO There should be a more explicit way to execute an action against existing services
            componentTypeRegistry.maybeRegisterComponentType(MavenModule.class)
                .registerArtifactType(MavenPomArtifact.class, ArtifactType.MAVEN_POM);
        }

        @Provides
        public VersionRangeMapper createVersionRangeMapper(VersionSelectorScheme versionSelectorScheme) {
            return new MavenVersionRangeMapper(versionSelectorScheme);
        }

        @Provides
        public MavenPublishers createMavenPublishers(BuildCommencedTimeProvider timeProvider, RepositoryTransportFactory repositoryTransportFactory, LocalMavenRepositoryLocator mavenRepositoryLocator) {
            return new MavenPublishers(timeProvider, repositoryTransportFactory, mavenRepositoryLocator);
        }
    }
}
