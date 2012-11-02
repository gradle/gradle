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

package org.gradle.api.publish;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;

/**
 * Provides access to the publications and repositories to publish to.
 *
 * The {@code “publish”} plugin installs an instance of this extension to the {@link org.gradle.api.Project} with the name {@value #NAME}.

 * See the {@code “ivy-publish”} plugin documentation for example usage.
 *
 * @since 1.3
 */
@Incubating
public interface PublishingExtension {

    /**
     * The name of this extension when installed by the {@link org.gradle.api.publish.plugins.PublishingPlugin} ({@value}).
     */
    String NAME = "publishing";

    /**
     * The container of possible repositories to publish to.
     *
     * @return The container of possible repositories to publish to.
     */
    NamedDomainObjectContainer<ArtifactRepository> getRepositories();

    /**
     * Configures the container of possible repositories to publish to.
     *
     * @param configure The action to configure the container of repositories with.
     */
    void repositories(Action<? super NamedDomainObjectContainer<ArtifactRepository>> configure);

    /**
     * The publications of this project.
     *
     * @return The publications of this project.
     * @see PublicationContainer
     */
    PublicationContainer getPublications();

    /**
     * Configures the publications of this project.
     *
     * @param configure The action to configure the publications with.
     */
    void publications(Action<? super PublicationContainer> configure);

}
