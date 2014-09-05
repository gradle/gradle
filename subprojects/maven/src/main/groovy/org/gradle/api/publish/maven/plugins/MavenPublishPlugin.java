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
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenProjectIdentity;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.capitalize;

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
    private final ProjectDependencyPublicationResolver projectDependencyResolver;

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver,
                              ProjectDependencyPublicationResolver projectDependencyResolver) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.projectDependencyResolver = projectDependencyResolver;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(PublishingPlugin.class);

        final TaskContainer tasks = project.getTasks();
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
    }

    /**
     * Model rules.
     */
    @RuleSource
    static class Rules {
        @Mutate
        @SuppressWarnings("UnusedDeclaration")
        public void realizePublishingTasks(CollectionBuilder<Task> tasks, @Path("tasks.publish") Task publishLifecycleTask, @Path("tasks.publishToMavenLocal") Task publishLocalLifecycleTask,
                                           PublishingExtension extension) {
            // Create generatePom tasks for any Maven publication
            PublicationContainer publications = extension.getPublications();

            for (final MavenPublicationInternal publication : publications.withType(MavenPublicationInternal.class)) {
                String publicationName = publication.getName();

                createGeneratePomTask(tasks, publication, publicationName);
                createLocalInstallTask(tasks, publishLocalLifecycleTask, publication, publicationName);
                createPublishTasksForEachMavenRepo(tasks, extension, publishLifecycleTask, publication, publicationName);
            }
        }

        private void createPublishTasksForEachMavenRepo(CollectionBuilder<Task> tasks, PublishingExtension extension, final Task publishLifecycleTask, final MavenPublicationInternal publication,
                                                        final String publicationName) {
            for (final MavenArtifactRepository repository : extension.getRepositories().withType(MavenArtifactRepository.class)) {
                final String repositoryName = repository.getName();

                String publishTaskName = String.format("publish%sPublicationTo%sRepository", capitalize(publicationName), capitalize(repositoryName));

                tasks.create(publishTaskName, PublishToMavenRepository.class, new Action<PublishToMavenRepository>() {
                    public void execute(PublishToMavenRepository publishTask) {
                        publishTask.setPublication(publication);
                        publishTask.setRepository(repository);
                        publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                        publishTask.setDescription(String.format("Publishes Maven publication '%s' to Maven repository '%s'.", publicationName, repositoryName));

                        //Because dynamic rules are not yet implemented we have to violate input immutability here as an interim step
                        publishLifecycleTask.dependsOn(publishTask);
                    }
                });
            }
        }

        private void createLocalInstallTask(CollectionBuilder<Task> tasks, final Task publishLocalLifecycleTask, final MavenPublicationInternal publication, final String publicationName) {
            final String installTaskName = String.format("publish%sPublicationToMavenLocal", capitalize(publicationName));

            tasks.create(installTaskName, PublishToMavenLocal.class, new Action<PublishToMavenLocal>() {
                public void execute(PublishToMavenLocal publishLocalTask) {
                    publishLocalTask.setPublication(publication);
                    publishLocalTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    publishLocalTask.setDescription(String.format("Publishes Maven publication '%s' to the local Maven repository.", publicationName));

                    //Because dynamic rules are not yet implemented we have to violate input immutability here as an interim step
                    publishLocalLifecycleTask.dependsOn(installTaskName);
                }
            });
        }

        private void createGeneratePomTask(CollectionBuilder<Task> tasks, final MavenPublicationInternal publication, String publicationName) {
            String descriptorTaskName = String.format("generatePomFileFor%sPublication", capitalize(publicationName));
            tasks.create(descriptorTaskName, GenerateMavenPom.class, new Action<GenerateMavenPom>() {
                public void execute(final GenerateMavenPom generatePomTask) {
                    generatePomTask.setDescription(String.format("Generates the Maven POM file for publication '%s'.", publication.getName()));
                    generatePomTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    generatePomTask.setPom(publication.getPom());

                    ConventionMapping descriptorTaskConventionMapping = new DslObject(generatePomTask).getConventionMapping();
                    descriptorTaskConventionMapping.map("destination", new Callable<Object>() {
                        public Object call() throws Exception {
                            return new File(generatePomTask.getProject().getBuildDir(), "publications/" + publication.getName() + "/pom-default.xml");
                        }
                    });

                    // Wire the generated pom into the publication.
                    publication.setPomFile(generatePomTask.getOutputs().getFiles());
                }
            });
        }
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
