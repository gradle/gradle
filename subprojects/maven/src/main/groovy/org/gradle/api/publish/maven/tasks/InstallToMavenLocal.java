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

package org.gradle.api.publish.maven.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;

import javax.inject.Inject;

/**
 * Installs a {@link org.gradle.api.publish.maven.MavenPublication} to the Maven Local repository.
 *
 * @since 1.4
 */
@Incubating
public class InstallToMavenLocal extends PublishToMavenRepository {
    private final BaseRepositoryFactory baseRepositoryFactory;

    @Inject
    public InstallToMavenLocal(ArtifactPublicationServices publicationServices, Factory<LoggingManagerInternal> loggingManagerFactory,
                               FileResolver fileResolver, DependencyResolutionServices dependencyResolutionServices) {
        super(publicationServices, loggingManagerFactory, fileResolver);
        this.baseRepositoryFactory = dependencyResolutionServices.getBaseRepositoryFactory();
    }

    @Override
    public MavenArtifactRepository getRepository() {
        MavenArtifactRepository mavenLocalRepository = baseRepositoryFactory.createMavenLocalRepository();
        mavenLocalRepository.setName("mavenLocalInstall");
        return mavenLocalRepository;
    }

    @Override
    public void setRepository(MavenArtifactRepository repository) {
        throw new UnsupportedOperationException("Cannot override repository for installing to mavenLocal");
    }
}
