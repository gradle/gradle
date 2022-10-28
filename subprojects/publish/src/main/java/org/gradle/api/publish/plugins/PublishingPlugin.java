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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.DefaultPublicationContainer;
import org.gradle.api.publish.internal.DefaultPublishingExtension;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

/**
 * Installs a {@link org.gradle.api.publish.PublishingExtension} with name {@value org.gradle.api.publish.PublishingExtension#NAME}.
 *
 * @since 1.3
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_setup.html#publishing_overview">Publishing reference</a>
 */
public abstract class PublishingPlugin implements Plugin<Project> {

    public static final String PUBLISH_TASK_GROUP = "publishing";
    public static final String PUBLISH_LIFECYCLE_TASK_NAME = "publish";
    private static final String VALID_NAME_REGEX = "[A-Za-z0-9_\\-.]+";

    private final Instantiator instantiator;
    private final ArtifactPublicationServices publicationServices;
    private final ProjectPublicationRegistry projectPublicationRegistry;
    private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;

    @Inject
    public PublishingPlugin(ArtifactPublicationServices publicationServices,
                            Instantiator instantiator,
                            ProjectPublicationRegistry projectPublicationRegistry,
                            CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.publicationServices = publicationServices;
        this.instantiator = instantiator;
        this.projectPublicationRegistry = projectPublicationRegistry;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
    }

    @Override
    public void apply(final Project project) {
        RepositoryHandler repositories = publicationServices.createRepositoryHandler();
        PublicationContainer publications = instantiator.newInstance(DefaultPublicationContainer.class, instantiator, collectionCallbackActionDecorator);
        PublishingExtension extension = project.getExtensions().create(PublishingExtension.class, PublishingExtension.NAME, DefaultPublishingExtension.class, repositories, publications);
        project.getTasks().register(PUBLISH_LIFECYCLE_TASK_NAME, task -> {
            task.setDescription("Publishes all publications produced by this project.");
            task.setGroup(PUBLISH_TASK_GROUP);
        });
        extension.getPublications().all(publication -> {
            PublicationInternal<?> internalPublication = Cast.uncheckedNonnullCast(publication);
            ProjectInternal projectInternal = (ProjectInternal) project;
            projectPublicationRegistry.registerPublication(projectInternal, internalPublication);
        });
        bridgeToSoftwareModelIfNeeded((ProjectInternal) project);
        validatePublishingModelWhenComplete(project, extension);
    }

    private void validatePublishingModelWhenComplete(Project project, PublishingExtension extension) {
        project.afterEvaluate(projectAfterEvaluate -> {
            for (ArtifactRepository repository : extension.getRepositories()) {
                String repositoryName = repository.getName();
                if (!repositoryName.matches(VALID_NAME_REGEX)) {
                    throw new InvalidUserDataException("Repository name '" + repositoryName + "' is not valid for publication. Must match regex " + VALID_NAME_REGEX + ".");
                }
            }
            for (Publication publication : extension.getPublications()) {
                String publicationName = publication.getName();
                if (!publicationName.matches(VALID_NAME_REGEX)) {
                    throw new InvalidUserDataException("Publication name '" + publicationName + "' is not valid for publication. Must match regex " + VALID_NAME_REGEX + ".");
                }
            }
        });
    }

    private void bridgeToSoftwareModelIfNeeded(ProjectInternal project) {
        project.addRuleBasedPluginListener(new RuleBasedPluginListener() {
            @Override
            public void prepareForRuleBasedPlugins(Project project) {
                project.getPluginManager().apply(PublishingPluginRules.class);
            }
        });
    }

}
