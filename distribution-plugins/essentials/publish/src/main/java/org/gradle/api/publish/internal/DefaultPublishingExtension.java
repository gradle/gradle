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

package org.gradle.api.publish.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;

public class DefaultPublishingExtension implements PublishingExtension {
    private final RepositoryHandler repositories;
    private final PublicationContainer publications;

    public DefaultPublishingExtension(RepositoryHandler repositories, PublicationContainer publications) {
        this.repositories = repositories;
        this.publications = publications;
    }

    @Override
    public RepositoryHandler getRepositories() {
        return repositories;
    }

    @Override
    public void repositories(Action<? super RepositoryHandler> configure) {
        configure.execute(repositories);
    }

    @Override
    public PublicationContainer getPublications() {
        return publications;
    }

    @Override
    public void publications(Action<? super PublicationContainer> configure) {
        configure.execute(publications);
    }
}
