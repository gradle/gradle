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

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Map;

/**
 * A build scoped service that collects information on the local "publications" of each project within a build. A "publication" here means some buildable thing that the project produces that can be consumed outside of the project.
 *
 * The information is gathered from multiple sources ({@code publishing.publications} container, etc.).
 */
@ThreadSafe
public interface ProjectPublicationRegistry {
    void registerPublication(ProjectInternal project, ProjectPublication publication);

    /**
     * Returns the known publications for the given project.
     */
    <T extends ProjectPublication> Collection<T> getPublications(Class<T> type, String projectPath);

    /**
     * Returns all known publications, grouped by the project that produced it.
     *
     * This is used for resolving plugins in local composite builds.  See {@link org.gradle.composite.internal.plugins.LocalPluginResolution LocalPluginResolution}.
     */
    @SuppressWarnings("JavadocReference")
    <T extends ProjectPublication> Map<ProjectInternal, Collection<T>> getPublicationsByProject(Class<T> type);
}
