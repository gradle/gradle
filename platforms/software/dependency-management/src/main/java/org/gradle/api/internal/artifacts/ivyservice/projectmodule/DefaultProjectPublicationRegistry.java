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
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Cast;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultProjectPublicationRegistry implements ProjectPublicationRegistry, HoldsProjectState {
    private final SetMultimap<Path, Reference<?>> publicationsByProject = LinkedHashMultimap.create();

    @Override
    public <T extends ProjectPublication> Collection<T> getPublications(Class<T> type, Path projectIdentityPath) {
        synchronized (publicationsByProject) {
            Collection<Reference<?>> projectPublications = publicationsByProject.get(projectIdentityPath);
            if (projectPublications.isEmpty()) {
                return Collections.emptyList();
            }
            List<T> result = new ArrayList<>(projectPublications.size());
            for (Reference<?> reference : projectPublications) {
                if (type.isInstance(reference.get())) {
                    result.add(type.cast(reference.get()));
                }
            }
            return result;
        }
    }

    @Override
    public <T extends ProjectPublication> Collection<Reference<T>> getPublications(Class<T> type) {
        synchronized (publicationsByProject) {
            Collection<Reference<?>> allPublications = publicationsByProject.values();
            if (allPublications.isEmpty()) {
                return Collections.emptyList();
            }
            List<Reference<T>> result = new ArrayList<>(allPublications.size());
            for (Reference<?> reference : allPublications) {
                if (type.isInstance(reference.get())) {
                    result.add(Cast.uncheckedCast(reference));
                }
            }
            return result;
        }
    }

    @Override
    public void registerPublication(ProjectInternal project, ProjectPublication publication) {
        synchronized (publicationsByProject) {
            publicationsByProject.put(project.getIdentityPath(), new ReferenceImpl(publication, project));
        }
    }

    @Override
    public void discardAll() {
        publicationsByProject.clear();
    }

    private static class ReferenceImpl implements Reference<ProjectPublication> {
        private final ProjectPublication publication;
        private final ProjectInternal project;

        ReferenceImpl(ProjectPublication publication, ProjectInternal project) {
            this.publication = publication;
            this.project = project;
        }

        @Override
        public ProjectPublication get() {
            return publication;
        }

        @Override
        public ProjectInternal getProducingProject() {
            return project;
        }
    }
}
