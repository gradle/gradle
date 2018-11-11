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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultProjectPublicationRegistry implements ProjectPublicationRegistry {
    private final SetMultimap<Path, ProjectPublication> publicationsByProject = LinkedHashMultimap.create();
    private final List<Reference> allPublications = new ArrayList<Reference>();

    @Override
    public Set<ProjectPublication> getPublications(Path projectIdentityPath) {
        synchronized (publicationsByProject) {
            return ImmutableSet.copyOf(publicationsByProject.get(projectIdentityPath));
        }
    }

    @Override
    public Collection<Reference> getPublications() {
        return ImmutableList.copyOf(allPublications);
    }

    @Override
    public void registerPublication(ProjectInternal project, ProjectPublication publication) {
        synchronized (publicationsByProject) {
            publicationsByProject.put(project.getIdentityPath(), publication);
            allPublications.add(new Reference() {
                @Override
                public ProjectPublication get() {
                    return publication;
                }

                @Override
                public Project getOwningProject() {
                    return project;
                }
            });
        }
    }
}
