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
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.Publication;
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
        publications.all(new Action<Publication>() {
            public void execute(Publication publication) {
                for (ArtifactRepository repository : repositories) {
                    maybeCreate(publication, repository);
                }
            }
        });

        repositories.whenObjectAdded(new Action<ArtifactRepository>() {
            public void execute(ArtifactRepository repository) {
                for (Publication publication : publications) {
                    maybeCreate(publication, repository);
                }
            }
        });

        // Note: we aren't supporting removal of repositories or publications
        // Note: we also aren't considering that repos have a setName, so their name can change
        //       (though this is a violation of the Named contract)
    }

    private void maybeCreate(Publication publication, ArtifactRepository repository) {
        if (!(publication instanceof MavenPublicationInternal)) {
            return;
        }
        if (!(repository instanceof MavenArtifactRepository)) {
            return;
        }

        final MavenPublicationInternal publicationInternal = (MavenPublicationInternal) publication;
        final MavenArtifactRepository mavenRepository = (MavenArtifactRepository) repository;

        String publicationName = publication.getName();
        String repositoryName = repository.getName();

        String publishTaskName = calculatePublishTaskName(publicationName, repositoryName);
        PublishToMavenRepository publishTask = tasks.add(publishTaskName, PublishToMavenRepository.class);
        publishTask.setPublication(publicationInternal);
        publishTask.setRepository(mavenRepository);
        publishTask.setGroup("publishing");
        publishTask.setDescription(String.format("Publishes Maven publication '%s' to Maven repository '%s'", publicationName, repositoryName));

        publishLifecycleTask.dependsOn(publishTask);
    }

    private String calculatePublishTaskName(String publicationName, String repositoryName) {
        return String.format("publish%sPublicationTo%sRepository", capitalize(publicationName), capitalize(repositoryName));
    }

}
