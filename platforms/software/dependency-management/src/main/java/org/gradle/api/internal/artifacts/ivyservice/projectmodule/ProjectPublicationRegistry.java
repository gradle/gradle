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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.util.Path;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;

/**
 * A build tree scoped service that collects information on the local "publications" of each
 * project within a build tree. A "publication" here means some buildable thing that the project
 * produces that can be consumed outside of the project.
 *
 * The information is gathered from multiple sources ({@code publishing.publications} container, etc.).
 */
@ThreadSafe
public interface ProjectPublicationRegistry {

    /**
     * Register the given publication as produced by the project with the given ID.
     */
    void registerPublication(ProjectIdentity projectIdentity, ProjectPublication publication);

    /**
     * Returns the known publications for the given project.
     */
    <T extends ProjectPublication> Collection<T> getPublicationsForProject(Class<T> type, Path projectIdentityPath);

    /**
     * Returns all known publications for the given build.
     */
    <T extends ProjectPublication> Collection<PublicationForProject<T>> getPublicationsForBuild(Class<T> type, BuildIdentifier buildIdentity);

    interface PublicationForProject<T extends ProjectPublication> {

        /**
         * The publication produced by the project.
         */
        T getPublication();

        /**
         * The ID of the project that produced the publication.
         */
        ProjectIdentity getProducingProjectId();
    }
}
