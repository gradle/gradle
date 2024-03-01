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

package org.gradle.api.publish.internal.validation;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;
import java.net.URI;

public class DuplicatePublicationTracker {
    private final static Logger LOG = Logging.getLogger(DuplicatePublicationTracker.class);
    private final Multimap<String, PublicationWithProject> published = LinkedHashMultimap.create();

    public synchronized void checkCanPublish(String projectDisplayName, String publicationName, ModuleVersionIdentifier projectIdentity, @Nullable URI repositoryLocation, String repositoryName) {
        // Don't track publications to repositories configured without a base URL
        if (repositoryLocation == null) {
            return;
        }

        String repositoryKey = normalizeLocation(repositoryLocation);

        PublicationWithProject publicationWithProject = new PublicationWithProject(projectDisplayName, publicationName, projectIdentity);
        if (published.get(repositoryKey).contains(publicationWithProject)) {
            LOG.warn("Publication '" + projectIdentity + "' is published multiple times to the same location. It is likely that repository '" + repositoryName + "' is duplicated.");
            return;
        }

        for (PublicationWithProject previousPublicationWithProject : published.get(repositoryKey)) {
            if (previousPublicationWithProject.getCoordinates().equals(projectIdentity)) {
                String firstPublication = previousPublicationWithProject.toString();
                String secondPublication = publicationWithProject.toString();
                if (secondPublication.compareTo(firstPublication) < 0) {
                    String temp = firstPublication;
                    firstPublication = secondPublication;
                    secondPublication = temp;
                }
                LOG.warn("Multiple publications with coordinates '" + projectIdentity + "' are published to repository '" + repositoryName + "'. The publications " + firstPublication + " and " + secondPublication + " will overwrite each other!");
            }
        }

        published.put(repositoryKey, publicationWithProject);
    }

    private String normalizeLocation(URI location) {
        String repoUrl = location.toString();
        if (!repoUrl.endsWith("/")) {
            return repoUrl + "/";
        }
        return repoUrl;
    }
}
