/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.model.ModelRule;

import java.io.File;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

public class MavenPublishTaskModelRule extends ModelRule {

    private final Project project;
    private final Task publishLifecycleTask;
    private final Task publishLocalLifecycleTask;

    public MavenPublishTaskModelRule(Project project, Task publishLifecycleTask, Task publishLocalLifecycleTask) {
        this.project = project;
        this.publishLifecycleTask = publishLifecycleTask;
        this.publishLocalLifecycleTask = publishLocalLifecycleTask;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void realizePublishingTasks(TaskContainer tasks, PublishingExtension extension) {
        // Create generatePom tasks for any Maven publication
        PublicationContainer publications = extension.getPublications();

        for (final MavenPublicationInternal publication : publications.withType(MavenPublicationInternal.class)) {
            String publicationName = publication.getName();

            createGeneratePomTask(publication, publicationName);
            createLocalInstallTask(tasks, publication, publicationName);
            createPublishTasksForEachMavenRepo(tasks, extension, publication, publicationName);
        }
    }

    private void createPublishTasksForEachMavenRepo(TaskContainer tasks, PublishingExtension extension, MavenPublicationInternal publication, String publicationName) {
        for (MavenArtifactRepository repository : extension.getRepositories().withType(MavenArtifactRepository.class)) {
            String repositoryName = repository.getName();

            String publishTaskName = String.format("publish%sPublicationTo%sRepository", capitalize(publicationName), capitalize(repositoryName));

            PublishToMavenRepository publishTask = tasks.create(publishTaskName, PublishToMavenRepository.class);
            publishTask.setPublication(publication);
            publishTask.setRepository(repository);
            publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            publishTask.setDescription(String.format("Publishes Maven publication '%s' to Maven repository '%s'.", publicationName, repositoryName));

            publishLifecycleTask.dependsOn(publishTask);
        }
    }

    private void createLocalInstallTask(TaskContainer tasks, MavenPublicationInternal publication, String publicationName) {
        String installTaskName = String.format("publish%sPublicationToMavenLocal", capitalize(publicationName));

        PublishToMavenLocal publishLocalTask = tasks.create(installTaskName, PublishToMavenLocal.class);
        publishLocalTask.setPublication(publication);
        publishLocalTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        publishLocalTask.setDescription(String.format("Publishes Maven publication '%s' to the local Maven repository.", publicationName));

        publishLocalLifecycleTask.dependsOn(installTaskName);
    }

    private void createGeneratePomTask(final MavenPublicationInternal publication, String publicationName) {
        String descriptorTaskName = String.format("generatePomFileFor%sPublication", capitalize(publicationName));
        GenerateMavenPom generatePomTask = project.getTasks().create(descriptorTaskName, GenerateMavenPom.class);
        generatePomTask.setDescription(String.format("Generates the Maven POM file for publication '%s'.", publication.getName()));
        generatePomTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        generatePomTask.setPom(publication.getPom());

        ConventionMapping descriptorTaskConventionMapping = new DslObject(generatePomTask).getConventionMapping();
        descriptorTaskConventionMapping.map("destination", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), "publications/" + publication.getName() + "/pom-default.xml");
            }
        });

        // Wire the generated pom into the publication.
        publication.setPomFile(generatePomTask.getOutputs().getFiles());
    }
}
