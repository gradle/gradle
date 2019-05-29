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
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker;

import javax.annotation.Nullable;
import java.net.URI;

public class MavenDuplicatePublicationTracker {
    private final DuplicatePublicationTracker duplicatePublicationTracker;
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public MavenDuplicatePublicationTracker(DuplicatePublicationTracker duplicatePublicationTracker, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.duplicatePublicationTracker = duplicatePublicationTracker;
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public void checkCanPublish(PublicationInternal publication, @Nullable URI repositoryLocation, String repositoryName) {
        duplicatePublicationTracker.checkCanPublish(publication, repositoryLocation, repositoryName);
    }

    public void checkCanPublishToMavenLocal(PublicationInternal publication) {
        checkCanPublish(publication, mavenRepositoryLocator.getLocalMavenRepository().toURI(), "mavenLocal");
    }
}
