/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.gmm;

import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.gmm.internal.GMMProjectIdentity;
import org.gradle.api.publish.gmm.internal.publication.DefaultGMMPublication;
import org.gradle.api.publish.gmm.internal.publication.GMMPublicationInternal;
import org.gradle.api.publish.gmm.internal.publication.MutableGMMProjectIdentity;
import org.gradle.api.publish.gmm.internal.publication.WritableGMMProjectIdentity;
import org.gradle.api.publish.gmm.publication.GMMPublication;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Plugin for publishing Gradle Module Metadata.
 * <p>
 * This plugin exists to provide a way to publish Gradle Module Metadata without involving any other metadata publishing
 * or repository format (i.e., Ivy, Maven).
 *
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html">Gradle Module Metadata documentation reference</a>
 *
 * @since 8.1
 */
@Incubating
public abstract class GMMPublishPlugin implements Plugin<Project> {
    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;
    private final ProviderFactory providerFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final DocumentationRegistry documentationRegistry;

    @Inject
    public GMMPublishPlugin(Instantiator instantiator, ObjectFactory objectFactory, DependencyMetaDataProvider dependencyMetaDataProvider,
                            FileResolver fileResolver, ProviderFactory providerFactory, TaskDependencyFactory taskDependencyFactory, DocumentationRegistry documentationRegistry) {
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.providerFactory = providerFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getPublications().registerFactory(GMMPublication.class, new GMMPublicationFactory());

            @SuppressWarnings("rawtypes")
            final NamedDomainObjectSet<PublicationInternal> publications = extension.getPublications().withType(PublicationInternal.class);
            final TaskContainer tasks = project.getTasks();
            final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

            publications.all(publication -> createGenerateMetadataTask(tasks, publication, buildDirectory));

            realizePublishingTasksLater(project, extension);
        });
    }

    private void realizePublishingTasksLater(final Project project, final PublishingExtension extension) {
        final NamedDomainObjectSet<GMMPublicationInternal> gmmPublications = extension.getPublications().withType(GMMPublicationInternal.class);
        final TaskContainer tasks = project.getTasks();

        final TaskProvider<Task> publishLifecycleTask = tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        final NamedDomainObjectList<FlatDirectoryArtifactRepository> repositories = extension.getRepositories().withType(FlatDirectoryArtifactRepository.class); // TODO: currently only publishing to flat repos

        repositories.all(repository -> tasks.register(publishAllToSingleRepoTaskName(repository), publish -> {
            publish.setDescription("Publishes all GMM publications produced by this project to the " + repository.getName() + " repository.");
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        }));

        gmmPublications.all(publication -> {
            createPublishTasksForEachFlatRepo(tasks, publishLifecycleTask, publication, repositories);
        });
    }

    @SuppressWarnings("unchecked")
    private void createGenerateMetadataTask(TaskContainer tasks, @SuppressWarnings("rawtypes") final PublicationInternal publication, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generateMetadataFileFor" + capitalize(publicationName) + "Publication";
        TaskProvider<GenerateModuleMetadata> generatorTask = tasks.register(descriptorTaskName, GenerateModuleMetadata.class, generateGMMTask -> {
            generateGMMTask.setDescription("Generates the Gradle Module Metadata file for publication '" + publicationName + "'.");
            generateGMMTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            generateGMMTask.getOutputFile().set(buildDir.file("publications/" + publication.getName() + "/module.json"));
            generateGMMTask.getPublication().set(publication);
        });
        publication.setModuleDescriptorGenerator(generatorTask);
    }

    // TODO: Abstract publishing plugin could contain this?
    private String publishAllToSingleRepoTaskName(ArtifactRepository repository) {
        return "publishAllGMMPublicationsTo" + capitalize(repository.getName()) + "Repository";
    }

    private void createPublishTasksForEachFlatRepo(final TaskContainer tasks, final TaskProvider<Task> publishLifecycleTask,
                                                   final GMMPublicationInternal publication, final NamedDomainObjectList<FlatDirectoryArtifactRepository> repositories) {
        final String publicationName = publication.getName();
        repositories.all(repository -> {
            final String repositoryName = repository.getName();
            final String publishTaskName = "publish" + capitalize(publicationName) + "PublicationTo" + capitalize(repositoryName) + "Repository";
            tasks.register(publishTaskName, Copy.class, copyTask -> {
                copyTask.from(publication.getModuleDescriptorGenerator());
                repository.getDirs().forEach(r -> copyTask.into(r));
                copyTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                copyTask.setDescription("Publishes GMM publication '" + publicationName + "' to repository '" + repositoryName + "'.");
            });
            publishLifecycleTask.configure(task -> task.dependsOn(publishTaskName));
            tasks.named(publishAllToSingleRepoTaskName(repository), publish -> publish.dependsOn(publishTaskName));
        });
    }

    private class GMMPublicationFactory implements NamedDomainObjectFactory<GMMPublication> {
        @Override
        public GMMPublication create(final String name) {
            return objectFactory.newInstance(
                    DefaultGMMPublication.class,
                    name,
                    taskDependencyFactory,
                    documentationRegistry,
                    createProjectIdentity()
            );
        }

        private GMMProjectIdentity createProjectIdentity() {
            final Module module = dependencyMetaDataProvider.getModule();
            MutableGMMProjectIdentity projectIdentity = new WritableGMMProjectIdentity(objectFactory);
            projectIdentity.getGroupId().set(providerFactory.provider(module::getGroup));
            projectIdentity.getArtifactId().set(providerFactory.provider(module::getName));
            projectIdentity.getVersion().set(providerFactory.provider(module::getVersion));
            return projectIdentity;
        }
    }
}
