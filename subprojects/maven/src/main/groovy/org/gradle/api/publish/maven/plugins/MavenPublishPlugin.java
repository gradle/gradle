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

import org.gradle.api.*;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.MavenPublishTaskModelRule;
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenProjectIdentity;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.ModelRules;

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
    private final ModelRules modelRules;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver, ModelRules modelRules,
                              ProjectDependencyPublicationResolver projectDependencyResolver) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.modelRules = modelRules;
        this.projectDependencyResolver = projectDependencyResolver;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(PublishingPlugin.class);

        final TaskContainer tasks = project.getTasks();
        final Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        final Task publishLocalLifecycleTask = tasks.create(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);
        publishLocalLifecycleTask.setDescription("Publishes all Maven publications produced by this project to the local Maven cache.");
        publishLocalLifecycleTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);

        // Can't move this to rules yet, because it has to happen before user deferred configurable actions
        project.getExtensions().configure(PublishingExtension.class, new Action<PublishingExtension>() {
            public void execute(PublishingExtension extension) {
                // Register factory for MavenPublication
                extension.getPublications().registerFactory(MavenPublication.class, new MavenPublicationFactory(dependencyMetaDataProvider, instantiator, fileResolver));
            }
        });

        modelRules.rule(new MavenPublishTaskModelRule(project, publishLifecycleTask, publishLocalLifecycleTask));
    }

    private class MavenPublicationFactory implements NamedDomainObjectFactory<MavenPublication> {
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
            NotationParser<Object, MavenArtifact> artifactNotationParser = new MavenArtifactNotationParserFactory(instantiator, fileResolver).create();

            return instantiator.newInstance(
                    DefaultMavenPublication.class,
                    name, projectIdentity, artifactNotationParser, instantiator, projectDependencyResolver
            );
        }
    }
}
