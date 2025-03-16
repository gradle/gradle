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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.Cast;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultProjectPublicationRegistry implements ProjectPublicationRegistry, HoldsProjectState {
    private final SetMultimap<Path, ProjectPublication> publicationsByProjectId = LinkedHashMultimap.create();
    private final SetMultimap<BuildIdentifier, PublicationForProject<?>> publicationsByBuildId = LinkedHashMultimap.create();

    @Override
    public <T extends ProjectPublication> Collection<T> getPublicationsForProject(Class<T> type, Path projectIdentityPath) {
        synchronized (publicationsByProjectId) {
            Collection<ProjectPublication> projectPublications = publicationsByProjectId.get(projectIdentityPath);
            if (projectPublications.isEmpty()) {
                return Collections.emptyList();
            }
            List<T> result = new ArrayList<>(projectPublications.size());
            for (ProjectPublication publication : projectPublications) {
                if (type.isInstance(publication)) {
                    result.add(type.cast(publication));
                }
            }
            return result;
        }
    }

    @Override
    public <T extends ProjectPublication> Collection<PublicationForProject<T>> getPublicationsForBuild(Class<T> type, BuildIdentifier buildIdentity) {
        synchronized (publicationsByBuildId) {
            Collection<PublicationForProject<?>> buildPublications = publicationsByBuildId.get(buildIdentity);
            if (buildPublications.isEmpty()) {
                return Collections.emptyList();
            }
            List<PublicationForProject<T>> result = new ArrayList<>(buildPublications.size());
            for (PublicationForProject<?> reference : buildPublications) {
                if (type.isInstance(reference.getPublication())) {
                    result.add(Cast.uncheckedCast(reference));
                }
            }
            return result;
        }
    }

    @Override
    public void registerPublication(ProjectIdentity projectIdentity, ProjectPublication publication) {
        synchronized (publicationsByProjectId) {
            publicationsByProjectId.put(projectIdentity.getBuildTreePath(), publication);
        }
        synchronized (publicationsByBuildId) {
            DefaultPublicationForProject publicationReference = new DefaultPublicationForProject(publication, projectIdentity);
            publicationsByBuildId.put(projectIdentity.getBuildIdentifier(), publicationReference);
        }
    }

    @Override
    public void discardAll() {
        publicationsByProjectId.clear();
        publicationsByBuildId.clear();
    }

    private static class DefaultPublicationForProject implements PublicationForProject<ProjectPublication> {
        private final ProjectPublication publication;
        private final ProjectIdentity projectId;

        DefaultPublicationForProject(ProjectPublication publication, ProjectIdentity projectId) {
            this.publication = publication;
            this.projectId = projectId;
        }

        @Override
        public ProjectPublication getPublication() {
            return publication;
        }

        @Override
        public ProjectIdentity getProducingProjectId() {
            return projectId;
        }
    }
}
