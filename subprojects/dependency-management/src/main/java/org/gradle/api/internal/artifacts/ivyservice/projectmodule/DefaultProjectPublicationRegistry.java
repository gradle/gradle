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
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultProjectPublicationRegistry implements ProjectPublicationRegistry, HoldsProjectState {
    private final SetMultimap<Path, ProjectPublication> publicationsByProject = LinkedHashMultimap.create();
    ProjectRegistry<ProjectInternal> projectRegistry;

    @Inject
    public DefaultProjectPublicationRegistry(ProjectRegistry<ProjectInternal> projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public <T extends ProjectPublication> Collection<T> getPublications(Class<T> type, Path projectIdentityPath) {
        synchronized (publicationsByProject) {
            Collection<ProjectPublication> projectPublications = publicationsByProject.get(projectIdentityPath);
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
    public <T extends ProjectPublication> Map<ProjectInternal, Collection<T>> getPublicationsByProject(Class<T> type) {
        synchronized (publicationsByProject) {
            Collection<ProjectPublication> allPublications = publicationsByProject.values();
            if (allPublications.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<ProjectInternal, Collection<T>> result = new HashMap<>(allPublications.size());
            publicationsByProject.entries().forEach(entry -> {
                ProjectPublication publication = entry.getValue();
                if (type.isInstance(publication)) {
                    ProjectInternal project = projectRegistry.getProject(entry.getKey().getPath());
                    Collection<T> publications = result.computeIfAbsent(project, p -> new ArrayList<>());
                    publications.add(type.cast(publication));
                }
            });

            return result;
        }
    }

    @Override
    public void registerPublication(ProjectInternal project, ProjectPublication publication) {
        synchronized (publicationsByProject) {
            publicationsByProject.put(project.getIdentityPath(), publication);
        }
    }

    @Override
    public void discardAll() {
        publicationsByProject.clear();
    }
}
