/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.Cast;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.internal.Factory;

import java.util.Collections;

public class IvyPublisher {

    private final ArtifactPublisher artifactPublisher;
    private final Factory<Configuration> configurationFactory;

    public IvyPublisher(ArtifactPublisher artifactPublisher, Factory<Configuration> configurationFactory) {
        this.artifactPublisher = artifactPublisher;
        this.configurationFactory = configurationFactory;
    }

    public void publish(IvyNormalizedPublication publication, IvyArtifactRepository repository) {
        Configuration publishConfiguration = createPopulatedConfiguration(publication.getArtifacts());
        DependencyResolver dependencyResolver = Cast.cast(ArtifactRepositoryInternal.class, repository).createResolver();
        artifactPublisher.publish(Collections.singleton(dependencyResolver), publication.getModule(), Collections.singleton(publishConfiguration), publication.getDescriptorFile());
    }

    private Configuration createPopulatedConfiguration(Iterable<PublishArtifact> artifacts) {
        Configuration configuration = configurationFactory.create();
        for (PublishArtifact artifact : artifacts) {
            configuration.getArtifacts().add(artifact);
        }
        return configuration;
    }


}
