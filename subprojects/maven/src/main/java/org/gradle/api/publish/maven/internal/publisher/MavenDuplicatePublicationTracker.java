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

import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.net.URI;

@ServiceScope(Scopes.Project.class)
public class MavenDuplicatePublicationTracker {
    private final String projectDisplayName;
    private final DuplicatePublicationTracker duplicatePublicationTracker;
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public MavenDuplicatePublicationTracker(Project project, DuplicatePublicationTracker duplicatePublicationTracker, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.projectDisplayName = project.getDisplayName();
        this.duplicatePublicationTracker = duplicatePublicationTracker;
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public void checkCanPublish(MavenNormalizedPublication publication, @Nullable URI repositoryLocation, String repositoryName) {
        duplicatePublicationTracker.checkCanPublish(
            projectDisplayName, publication.getName(), DefaultModuleVersionIdentifier.newId(publication.getProjectIdentity()), repositoryLocation, repositoryName);
    }

    public void checkCanPublishToMavenLocal(MavenNormalizedPublication publication) {
        checkCanPublish(publication, mavenRepositoryLocator.getLocalMavenRepository().toURI(), "mavenLocal");
    }
}
