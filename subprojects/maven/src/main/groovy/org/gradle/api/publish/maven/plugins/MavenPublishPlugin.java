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

package org.gradle.api.publish.maven.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.PublicationContainerInternal;
import org.gradle.api.publish.internal.PublicationFactory;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenProjectIdentity;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory;
import org.gradle.api.publish.maven.internal.plugins.GeneratePomTaskCreator;
import org.gradle.api.publish.maven.internal.plugins.MavenPublishDynamicTaskCreator;
import org.gradle.api.publish.maven.internal.plugins.MavenPublishLocalDynamicTaskCreator;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

/**
 * Adds the ability to publish in the Maven format to Maven repositories.
 *
 * @since 1.4
 */
@Incubating
public class MavenPublishPlugin implements Plugin<Project> {

    public static final String PUBLISH_LOCAL_LIFECYCLE_TASK_NAME = "publishToMavenLocal";

    private final Instantiator instantiator;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(PublishingPlugin.class);
        final PublishingExtension extension = project.getExtensions().getByType(PublishingExtension.class);

        final PublicationContainerInternal publicationContainer = (PublicationContainerInternal) extension.getPublications();
        publicationContainer.registerFactory(MavenPublication.class, new MavenPublicationFactory(dependencyMetaDataProvider, instantiator, fileResolver));

        TaskContainer tasks = project.getTasks();

        // Create generatePom tasks for any Maven publication
        GeneratePomTaskCreator descriptorGenerationTaskCreator = new GeneratePomTaskCreator(project);
        descriptorGenerationTaskCreator.monitor(extension.getPublications());

        // Create publish tasks automatically for any Maven publication and repository combinations
        Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        MavenPublishDynamicTaskCreator publishTaskCreator = new MavenPublishDynamicTaskCreator(tasks, publishLifecycleTask);
        publishTaskCreator.monitor(extension.getPublications(), extension.getRepositories());

        // Create install tasks automatically for any Maven publication
        Task publishLocalLifecycleTask = tasks.add(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);
        publishLocalLifecycleTask.setDescription("Publishes all Maven publications produced by this project to the local Maven cache.");
        publishLocalLifecycleTask.setGroup("publishing");
        MavenPublishLocalDynamicTaskCreator publishLocalTaskCreator = new MavenPublishLocalDynamicTaskCreator(tasks, publishLocalLifecycleTask);
        publishLocalTaskCreator.monitor(extension.getPublications());
    }

    private class MavenPublicationFactory implements PublicationFactory {
        private final Instantiator instantiator;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final FileResolver fileResolver;

        private MavenPublicationFactory(DependencyMetaDataProvider dependencyMetaDataProvider, Instantiator instantiator, FileResolver fileResolver) {
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.instantiator = instantiator;
            this.fileResolver = fileResolver;
        }

        public MavenPublication create(final String name) {

            Module module = dependencyMetaDataProvider.getModule();
            MavenProjectIdentity projectIdentity = new DefaultMavenProjectIdentity(module.getGroup(), module.getName(), module.getVersion());
            NotationParser<MavenArtifact> artifactNotationParser = new MavenArtifactNotationParserFactory(instantiator, module.getVersion(), fileResolver).create();

            return instantiator.newInstance(
                    DefaultMavenPublication.class,
                    name, projectIdentity, artifactNotationParser, instantiator
            );
        }
    }
}
