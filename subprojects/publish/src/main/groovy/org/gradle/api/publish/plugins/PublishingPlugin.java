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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.DefaultPublicationContainer;
import org.gradle.api.publish.internal.DefaultPublishingExtension;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.ModelPath;
import org.gradle.model.ModelRule;
import org.gradle.model.ModelRules;

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
    private final ModelRules modelRules;
    private final ArtifactPublicationServices publicationServices;

    @Inject
    public PublishingPlugin(ArtifactPublicationServices publicationServices, Instantiator instantiator, ModelRules modelRules) {
        this.publicationServices = publicationServices;
        this.instantiator = instantiator;
        this.modelRules = modelRules;
    }

    public void apply(final Project project) {
        RepositoryHandler repositories = publicationServices.createRepositoryHandler();
        PublicationContainer publications = instantiator.newInstance(DefaultPublicationContainer.class, instantiator);

        // TODO Registering an extension should register it with the model registry as well
        PublishingExtension extension = project.getExtensions().create(PublishingExtension.NAME, DefaultPublishingExtension.class, repositories, publications);

        ModelPath extensionModelPath = ModelPath.path(PublishingExtension.NAME);

        modelRules.register(extensionModelPath.toString(), extension);

        modelRules.rule(new ModelRule() {
            public void triggerDeferredConfigurables(PublishingExtension publishingExtension) {
                project.getExtensions().getByType(DefaultPublishingExtension.class);
            }
        });

        Task publishLifecycleTask = project.getTasks().create(PUBLISH_LIFECYCLE_TASK_NAME);
        publishLifecycleTask.setDescription("Publishes all publications produced by this project.");
        publishLifecycleTask.setGroup(PUBLISH_TASK_GROUP);
    }
}
