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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.model.ModelRule;

import java.io.File;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

public class IvyPublicationTasksModelRule extends ModelRule {
    private final Project project;

    public IvyPublicationTasksModelRule(Project project) {
        this.project = project;
    }

    public void createTasks(TaskContainer tasks, PublishingExtension publishingExtension) {
        PublicationContainer publications = publishingExtension.getPublications();
        RepositoryHandler repositories = publishingExtension.getRepositories();

        for (final IvyPublicationInternal publication : publications.withType(IvyPublicationInternal.class)) {

            final String publicationName = publication.getName();
            final String descriptorTaskName = String.format("generateDescriptorFileFor%sPublication", capitalize(publicationName));

            GenerateIvyDescriptor descriptorTask = tasks.create(descriptorTaskName, GenerateIvyDescriptor.class);
            descriptorTask.setDescription(String.format("Generates the Ivy Module Descriptor XML file for publication '%s'.", publication.getName()));
            descriptorTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            descriptorTask.setDescriptor(publication.getDescriptor());

            ConventionMapping descriptorTaskConventionMapping = new DslObject(descriptorTask).getConventionMapping();
            descriptorTaskConventionMapping.map("destination", new Callable<Object>() {
                public Object call() throws Exception {
                    return new File(project.getBuildDir(), "publications/" + publication.getName() + "/ivy.xml");
                }
            });

            publication.setDescriptorFile(descriptorTask.getOutputs().getFiles());

            for (IvyArtifactRepository repository : repositories.withType(IvyArtifactRepository.class)) {
                final String repositoryName = repository.getName();
                final String publishTaskName = String.format("publish%sPublicationTo%sRepository", capitalize(publicationName), capitalize(repositoryName));

                PublishToIvyRepository publishTask = tasks.create(publishTaskName, PublishToIvyRepository.class);
                publishTask.setPublication(publication);
                publishTask.setRepository(repository);
                publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                publishTask.setDescription(String.format("Publishes Ivy publication '%s' to Ivy repository '%s'.", publicationName, repositoryName));

                tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME).dependsOn(publishTask);
            }
        }
    }
}