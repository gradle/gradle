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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Path;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;

/**
 * A build scoped service that collects information on the local "publications" of each project within a build. A "publication" here means some buildable thing that the project produces that can be consumed outside of the project.
 *
 * The information is gathered from multiple sources ({@code publishing.publications} container, {@code uploadArchives} task, etc.).
 */
@ThreadSafe
public interface ProjectPublicationRegistry {
    void registerPublication(ProjectInternal project, ProjectPublication publication);

    /**
     * Returns the known publications for the given project.
     */
    <T extends ProjectPublication> Collection<T> getPublications(Class<T> type, Path projectIdentityPath);

    /**
     * Returns all known publications.
     */
    <T extends ProjectPublication> Collection<Reference<T>> getPublications(Class<T> type);

    interface Reference<T> {
        T get();

        // Should use ProjectState instead
        ProjectInternal getProducingProject();
    }
}
