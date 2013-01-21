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

package org.gradle.api.publish.ivy.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.PublicationContainerInternal;
import org.gradle.api.publish.internal.PublicationFactory;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.DefaultIvyPublication;
import org.gradle.api.publish.ivy.tasks.internal.IvyPublicationDynamicDescriptorGenerationTaskCreator;
import org.gradle.api.publish.ivy.tasks.internal.IvyPublishDynamicTaskCreator;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

/**
 * Configures the project to publish a “main” IvyPublication to a “main” IvyArtifactRepository.
 *
 * Creates an IvyPublication named "main" in project.publications, configured to publish all of the visible configurations of the project.
 * Creates an IvyArtifactRepository
 *
 * @since 1.3
 */
@Incubating
public class IvyPublishPlugin implements Plugin<Project> {

    private final Instantiator instantiator;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;

    @Inject
    public IvyPublishPlugin(
            Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider
    ) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(PublishingPlugin.class);

        // Create the default publication
        final PublishingExtension extension = project.getExtensions().getByType(PublishingExtension.class);

        final PublicationContainerInternal publicationContainer = (PublicationContainerInternal) extension.getPublications();
        publicationContainer.registerFactory(IvyPublication.class, new IvyPublicationFactory(dependencyMetaDataProvider, instantiator));

        TaskContainer tasks = project.getTasks();

        // Create generate descriptor tasks
        IvyPublicationDynamicDescriptorGenerationTaskCreator descriptorGenerationTaskCreator = new IvyPublicationDynamicDescriptorGenerationTaskCreator(project);
        descriptorGenerationTaskCreator.monitor(extension.getPublications());

        // Create publish tasks automatically for any Ivy publication and repository combinations
        Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        IvyPublishDynamicTaskCreator publishTaskCreator = new IvyPublishDynamicTaskCreator(tasks, publishLifecycleTask);
        publishTaskCreator.monitor(extension.getPublications(), extension.getRepositories());
    }


    private class IvyPublicationFactory implements PublicationFactory {
        private final Instantiator instantiator;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;

        private IvyPublicationFactory(DependencyMetaDataProvider dependencyMetaDataProvider, Instantiator instantiator) {
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.instantiator = instantiator;
        }

        public Publication create(String name) {
            Module module = dependencyMetaDataProvider.getModule();
            return instantiator.newInstance(
                    DefaultIvyPublication.class,
                    name, instantiator, module
            );

        }
    }
}
