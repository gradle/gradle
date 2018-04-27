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

package org.gradle.api.publish.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.DefaultPublicationContainer;
import org.gradle.api.publish.internal.DefaultPublishingExtension;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;

import javax.inject.Inject;

/**
 * Installs a {@link org.gradle.api.publish.PublishingExtension} with name {@value org.gradle.api.publish.PublishingExtension#NAME}.
 *
 * @since 1.3
 */
@Incubating
public class PublishingPlugin implements Plugin<Project> {

    public static final String PUBLISH_TASK_GROUP = "publishing";
    public static final String PUBLISH_LIFECYCLE_TASK_NAME = "publish";

    private final Instantiator instantiator;
    private final ArtifactPublicationServices publicationServices;
    private final ProjectPublicationRegistry projectPublicationRegistry;

    @Inject
    public PublishingPlugin(ArtifactPublicationServices publicationServices, Instantiator instantiator, ProjectPublicationRegistry projectPublicationRegistry) {
        this.publicationServices = publicationServices;
        this.instantiator = instantiator;
        this.projectPublicationRegistry = projectPublicationRegistry;
    }

    public void apply(final Project project) {
        RepositoryHandler repositories = publicationServices.createRepositoryHandler();
        PublicationContainer publications = instantiator.newInstance(DefaultPublicationContainer.class, instantiator);
        PublishingExtension extension = project.getExtensions().create(PublishingExtension.class, PublishingExtension.NAME, DefaultPublishingExtension.class, repositories, publications);

        Task publishLifecycleTask = project.getTasks().create(PUBLISH_LIFECYCLE_TASK_NAME);
        publishLifecycleTask.setDescription("Publishes all publications produced by this project.");
        publishLifecycleTask.setGroup(PUBLISH_TASK_GROUP);
        extension.getPublications().all(new Action<Publication>() {
            @Override
            public void execute(Publication publication) {
                PublicationInternal internalPublication = (PublicationInternal) publication;
                projectPublicationRegistry.registerPublication(project.getPath(), internalPublication);
            }
        });
    }

    /**
     * These bindings are only here for backwards compatibility, as some users might depend on the extensions being available in the model.
     */
    static class Rules extends RuleSource {
        @Model
        PublishingExtension publishing(ExtensionContainer extensions) {
            return extensions.getByType(PublishingExtension.class);
        }

        @Model
        ProjectPublicationRegistry projectPublicationRegistry(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ProjectPublicationRegistry.class);
        }

        @Mutate
        void addConfiguredPublicationsToProjectPublicationRegistry(ProjectPublicationRegistry projectPublicationRegistry, PublishingExtension extension) {
            //this rule is just here to ensure backwards compatibility for builds that create publications with model rules
        }

        @Mutate
        void tasksDependOnProjectPublicationRegistry(ModelMap<Task> tasks, PublishingExtension extension) {
            //this rule is just here to ensure backwards compatibility for builds that create publications with model rules
        }
    }

}
