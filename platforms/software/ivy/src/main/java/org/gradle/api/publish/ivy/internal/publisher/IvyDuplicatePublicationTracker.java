/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.Project;
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.net.URI;

@ServiceScope(Scope.Project.class)
public class IvyDuplicatePublicationTracker {
    private final Project project;
    private final DuplicatePublicationTracker duplicatePublicationTracker;

    public IvyDuplicatePublicationTracker(Project project, DuplicatePublicationTracker duplicatePublicationTracker) {
        this.project = project;
        this.duplicatePublicationTracker = duplicatePublicationTracker;
    }

    public void checkCanPublish(IvyNormalizedPublication publication, @Nullable URI repositoryLocation, String repositoryName) {
        duplicatePublicationTracker.checkCanPublish(project.getDisplayName(), publication.getName(), publication.getCoordinates(), repositoryLocation, repositoryName);
    }
}
