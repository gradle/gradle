/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.tasks.TaskContainer;

import static org.apache.commons.lang.StringUtils.capitalize;

public class MavenPublishDynamicTaskCreator {

    final private TaskContainer tasks;
    private final Task publishLifecycleTask;

    public MavenPublishDynamicTaskCreator(TaskContainer tasks, Task publishLifecycleTask) {
        this.tasks = tasks;
        this.publishLifecycleTask = publishLifecycleTask;
    }

    public void monitor(final PublicationContainer publications, final ArtifactRepositoryContainer repositories) {
        final NamedDomainObjectSet<MavenPublicationInternal> mavenPublications = publications.withType(MavenPublicationInternal.class);
        final NamedDomainObjectList<MavenArtifactRepository> mavenRepositories = repositories.withType(MavenArtifactRepository.class);

        mavenPublications.all(new Action<MavenPublicationInternal>() {
            public void execute(MavenPublicationInternal publication) {
                for (MavenArtifactRepository repository : mavenRepositories) {
                    maybeCreate(publication, repository);
                }
            }
        });

        mavenRepositories.all(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository repository) {
                for (MavenPublicationInternal publication : mavenPublications) {
                    maybeCreate(publication, repository);
                }
            }
        });

        // Note: we aren't supporting removal of repositories or publications
        // Note: we also aren't considering that repos have a setName, so their name can change
        //       (though this is a violation of the Named contract)
    }

    private void maybeCreate(MavenPublicationInternal publication, MavenArtifactRepository repository) {
        String publicationName = publication.getName();
        String repositoryName = repository.getName();

        String publishTaskName = calculatePublishTaskName(publicationName, repositoryName);
        if (tasks.findByName(publishTaskName) == null) {
            PublishToMavenRepository publishTask = tasks.add(publishTaskName, PublishToMavenRepository.class);
            publishTask.setPublication(publication);
            publishTask.setRepository(repository);
            publishTask.setGroup("publishing");
            publishTask.setDescription(String.format("Publishes Maven publication '%s' to Maven repository '%s'", publicationName, repositoryName));

            publishLifecycleTask.dependsOn(publishTask);
        }
    }

    private String calculatePublishTaskName(String publicationName, String repositoryName) {
        return String.format("publish%sPublicationTo%sRepository", capitalize(publicationName), capitalize(repositoryName));
    }

}
