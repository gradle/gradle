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
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.internal.Pair;

import java.net.URI;
import java.util.Set;

public class DuplicatePublicationTracker {
    private final Multimap<String, MavenPublicationInternal> published = LinkedHashMultimap.create();

    public synchronized void checkCanPublish(MavenPublicationInternal publication, MavenArtifactRepository repository) {
        String repositoryKey = getRepositoryLocation(repository);

        if (published.get(repositoryKey).contains(publication)) {
            // TODO:DAZ Should warn about publishing twice to the same repo
            return;
        }

        ModuleVersionIdentifier projectIdentity = publication.getCoordinates();
        for (MavenPublicationInternal previousPublication : published.get(repositoryKey)) {
            if (previousPublication.getCoordinates().equals(projectIdentity)) {
                throw new GradleException("Cannot publish multiple publications with coordinates '" + projectIdentity + "' to repository '" + repository.getName() + "'");
            }
        }

        published.put(repositoryKey, publication);
    }

    private String getRepositoryLocation(MavenArtifactRepository repository) {
        URI location = repository.getUrl();
        String repoUrl = location.toString();
        if (!repoUrl.endsWith("/")) {
            return repoUrl + "/";
        }
        return repoUrl;
    }
}
