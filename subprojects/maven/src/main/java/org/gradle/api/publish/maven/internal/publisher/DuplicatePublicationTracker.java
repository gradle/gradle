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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;

import java.net.URI;

public class DuplicatePublicationTracker {
    private final static Logger LOG = Logging.getLogger(DuplicatePublicationTracker.class);
    private final Multimap<String, MavenPublicationInternal> published = LinkedHashMultimap.create();
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public DuplicatePublicationTracker(LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public synchronized void checkCanPublish(MavenPublicationInternal publication, URI repositoryLocation, String repositoryName) {
        String repositoryKey = normalizeLocation(repositoryLocation);

        if (published.get(repositoryKey).contains(publication)) {
            LOG.warn("Publication '" + publication.getCoordinates() + "' is published multiple times to the same location. It is likely that repository '" + repositoryName + "' is duplicated.");
            return;
        }

        ModuleVersionIdentifier projectIdentity = publication.getCoordinates();
        for (MavenPublicationInternal previousPublication : published.get(repositoryKey)) {
            if (previousPublication.getCoordinates().equals(projectIdentity)) {
                throw new GradleException("Cannot publish multiple publications with coordinates '" + projectIdentity + "' to repository '" + repositoryName + "'");
            }
        }

        published.put(repositoryKey, publication);
    }

    public synchronized void checkCanPublishToMavenLocal(MavenPublicationInternal publication) {
        checkCanPublish(publication, mavenRepositoryLocator.getLocalMavenRepository().toURI(), "mavenLocal");
    }

    private String normalizeLocation(URI location) {
        String repoUrl = location.toString();
        if (!repoUrl.endsWith("/")) {
            return repoUrl + "/";
        }
        return repoUrl;
    }
}
