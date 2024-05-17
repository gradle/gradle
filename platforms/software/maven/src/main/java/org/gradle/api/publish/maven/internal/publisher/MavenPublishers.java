/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher;

import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.internal.Factory;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.File;

public class MavenPublishers {
    private final BuildCommencedTimeProvider timeProvider;
    private RepositoryTransportFactory repositoryTransportFactory;
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public MavenPublishers(BuildCommencedTimeProvider timeProvider, RepositoryTransportFactory repositoryTransportFactory, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.timeProvider = timeProvider;
        this.repositoryTransportFactory = repositoryTransportFactory;
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public MavenPublisher getRemotePublisher(Factory<File> temporaryDirFactory) {
        return new MavenRemotePublisher(temporaryDirFactory, timeProvider);
    }

    public MavenPublisher getLocalPublisher(Factory<File> temporaryDirFactory) {
        return new MavenLocalPublisher(temporaryDirFactory, repositoryTransportFactory, mavenRepositoryLocator);
    }
}
