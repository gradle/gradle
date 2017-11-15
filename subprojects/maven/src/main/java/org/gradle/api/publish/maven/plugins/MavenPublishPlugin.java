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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.ExperimentalFeatures;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.Publication;
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
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    private final FileCollectionFactory fileCollectionFactory;
    private final ExperimentalFeatures experimentalFeatures;

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver,
                              ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
                              ExperimentalFeatures experimentalFeatures) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.projectDependencyResolver = projectDependencyResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.experimentalFeatures = experimentalFeatures;
    }

    public void apply(final Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

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

    static class Rules extends RuleSource {
        @Mutate
        @SuppressWarnings("UnusedDeclaration")
        public void realizePublishingTasks(ModelMap<Task> tasks, PublishingExtension extension, @Path("buildDir") File buildDir) {
            // Create generatePom tasks for any Maven publication
            PublicationContainer publications = extension.getPublications();
            Task publishLifecycleTask = tasks.get(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
            Task publishLocalLifecycleTask = tasks.get(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);

            NamedDomainObjectSet<MavenPublicationInternal> mavenPublications = publications.withType(MavenPublicationInternal.class);
            List<Publication> asPublication = new ArrayList<Publication>(publications);

            for (final MavenPublicationInternal publication : mavenPublications) {
                String publicationName = publication.getName();

                createGenerateMetadataTask(tasks, publication, asPublication, buildDir);
                createGeneratePomTask(tasks, publication, buildDir);
                createLocalInstallTask(tasks, publishLocalLifecycleTask, publication);
                createPublishTasksForEachMavenRepo(tasks, extension, publishLifecycleTask, publication);
            }
        }

        private void createPublishTasksForEachMavenRepo(ModelMap<Task> tasks, PublishingExtension extension, final Task publishLifecycleTask, final MavenPublicationInternal publication) {
            final String publicationName = publication.getName();
            for (final MavenArtifactRepository repository : extension.getRepositories().withType(MavenArtifactRepository.class)) {
                final String repositoryName = repository.getName();

                String publishTaskName = "publish" + capitalize(publicationName) + "PublicationTo" + capitalize(repositoryName) + "Repository";

                tasks.create(publishTaskName, PublishToMavenRepository.class, new Action<PublishToMavenRepository>() {
                    public void execute(PublishToMavenRepository publishTask) {
                        publishTask.setPublication(publication);
                        publishTask.setRepository(repository);
                        publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                        publishTask.setDescription("Publishes Maven publication '" + publicationName + "' to Maven repository '" + repositoryName + "'.");

                    }
                });
                publishLifecycleTask.dependsOn(publishTaskName);
            }
        }

        private void createLocalInstallTask(ModelMap<Task> tasks, final Task publishLocalLifecycleTask, final MavenPublicationInternal publication) {
            final String publicationName = publication.getName();
            final String installTaskName = "publish" + capitalize(publicationName) + "PublicationToMavenLocal";

            tasks.create(installTaskName, PublishToMavenLocal.class, new Action<PublishToMavenLocal>() {
                public void execute(PublishToMavenLocal publishLocalTask) {
                    publishLocalTask.setPublication(publication);
                    publishLocalTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    publishLocalTask.setDescription("Publishes Maven publication '" + publicationName + "' to the local Maven repository.");
                }
            });
            publishLocalLifecycleTask.dependsOn(installTaskName);
        }

        private void createGeneratePomTask(ModelMap<Task> tasks, final MavenPublicationInternal publication, final File buildDir) {
            final String publicationName = publication.getName();
            String descriptorTaskName = "generatePomFileFor" + capitalize(publicationName) + "Publication";
            tasks.create(descriptorTaskName, GenerateMavenPom.class, new Action<GenerateMavenPom>() {
                public void execute(final GenerateMavenPom generatePomTask) {
                    generatePomTask.setDescription("Generates the Maven POM file for publication '" + publicationName + "'.");
                    generatePomTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    generatePomTask.setPom(publication.getPom());
                    generatePomTask.setDestination(new File(buildDir, "publications/" + publication.getName() + "/pom-default.xml"));
                }
            });
            // Wire the generated pom into the publication.
            publication.setPomFile(tasks.get(descriptorTaskName).getOutputs().getFiles());
        }

        private void createGenerateMetadataTask(ModelMap<Task> tasks, final MavenPublicationInternal publication, final List<Publication> publications, final File buildDir) {
            if (!publication.canPublishModuleMetadata()) {
                return;
            }

            final String publicationName = publication.getName();
            String descriptorTaskName = "generateMetadataFileFor" + capitalize(publicationName) + "Publication";
            tasks.create(descriptorTaskName, GenerateModuleMetadata.class, new Action<GenerateModuleMetadata>() {
                public void execute(final GenerateModuleMetadata generateTask) {
                    generateTask.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.");
                    generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    generateTask.getPublication().set(publication);
                    generateTask.getPublications().set(publications);
                    // TODO - should deal with build dir changes
                    generateTask.getOutputFile().set(new File(buildDir, "publications/" + publication.getName() + "/module.json"));
                }
            });
            GenerateModuleMetadata generatorTask = (GenerateModuleMetadata) tasks.get(descriptorTaskName);
            MavenArtifact metadataFile = publication.artifact(generatorTask.getOutputFile());
            metadataFile.setExtension("module");
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
                    name, projectIdentity, artifactNotationParser, instantiator, projectDependencyResolver, fileCollectionFactory, experimentalFeatures
            );
        }
    }
}
