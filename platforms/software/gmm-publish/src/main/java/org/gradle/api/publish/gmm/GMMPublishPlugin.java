/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.gmm.internal.GMMProjectIdentity;
import org.gradle.api.publish.gmm.internal.PublicationWithModuleMetadata;
import org.gradle.api.publish.gmm.internal.publication.DefaultGMMPublication;
import org.gradle.api.publish.gmm.internal.publication.GMMPublicationInternal;
import org.gradle.api.publish.gmm.publication.GMMPublication;
import org.gradle.api.publish.internal.versionmapping.DefaultVersionMappingStrategy;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.apache.commons.lang3.StringUtils.capitalize;

/**
 * Adds the ability to publish Gradle Module Metadata on its own, without Maven or Ivy metadata.
 * <p>
 * Applies the {@link PublishingPlugin}, registers the task that generates Gradle Module Metadata for
 * each publication that supports it, and adds the {@link GMMPublication} type. A {@code GMMPublication}
 * can currently only be published to a flat directory repository.
 * <p>
 * The maven-publish and ivy-publish plugins apply this plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html">Gradle Module Metadata</a>
 * @since 9.10.0
 */
@Incubating
public abstract class GMMPublishPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            DependencyMetaDataProvider dependencyMetaDataProvider = ((ProjectInternal) project).getServices().get(DependencyMetaDataProvider.class);
            extension.getPublications().registerFactory(GMMPublication.class, new GMMPublicationFactory(project.getObjects(), project.getProviders(), dependencyMetaDataProvider));

            @SuppressWarnings("rawtypes")
            NamedDomainObjectSet<PublicationWithModuleMetadata> publications = extension.getPublications().withType(PublicationWithModuleMetadata.class);
            TaskContainer tasks = project.getTasks();
            DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
            publications.all(publication -> createGenerateMetadataTask(tasks, publication, publications, buildDirectory));

            createGMMPublishTasks(tasks, extension);
        });
    }

    private static void createGenerateMetadataTask(
        TaskContainer tasks,
        PublicationWithModuleMetadata<?> publication,
        @SuppressWarnings("rawtypes") Set<PublicationWithModuleMetadata> publications,
        DirectoryProperty buildDir
    ) {
        String publicationName = publication.getName();
        String descriptorTaskName = "generateMetadataFileFor" + capitalize(publicationName) + "Publication";
        TaskProvider<GenerateModuleMetadata> generatorTask = tasks.register(descriptorTaskName, GenerateModuleMetadata.class, generateTask -> {
            generateTask.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.");
            generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            generateTask.getPublication().set(publication);
            generateTask.getPublications().set(publications);
            generateTask.getOutputFile().convention(buildDir.file("publications/" + publicationName + "/module.json"));
        });
        publication.setModuleDescriptorGenerator(generatorTask);
    }

    private static void createGMMPublishTasks(TaskContainer tasks, PublishingExtension extension) {
        NamedDomainObjectSet<GMMPublicationInternal> gmmPublications = extension.getPublications().withType(GMMPublicationInternal.class);
        // Only flat directory repositories are supported for now
        NamedDomainObjectList<FlatDirectoryArtifactRepository> repositories = extension.getRepositories().withType(FlatDirectoryArtifactRepository.class);
        TaskProvider<Task> publishLifecycleTask = tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);

        repositories.all(repository -> tasks.register(publishAllToSingleRepoTaskName(repository), publish -> {
            publish.setDescription("Publishes all Gradle Module Metadata publications produced by this project to the " + repository.getName() + " repository.");
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        }));

        gmmPublications.all(publication -> {
            String publicationName = publication.getName();
            repositories.all(repository -> {
                String repositoryName = repository.getName();
                String publishTaskName = "publish" + capitalize(publicationName) + "PublicationTo" + capitalize(repositoryName) + "Repository";
                tasks.register(publishTaskName, Copy.class, copyTask -> {
                    copyTask.setDescription("Publishes Gradle Module Metadata publication '" + publicationName + "' to repository '" + repositoryName + "'.");
                    copyTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    copyTask.from(Objects.requireNonNull(publication.getModuleDescriptorGenerator()));
                    copyTask.into((Callable<File>) () -> repository.getDirs().iterator().next());
                });
                publishLifecycleTask.configure(task -> task.dependsOn(publishTaskName));
                tasks.named(publishAllToSingleRepoTaskName(repository), publish -> publish.dependsOn(publishTaskName));
            });
        });
    }

    private static String publishAllToSingleRepoTaskName(FlatDirectoryArtifactRepository repository) {
        return "publishAllGMMPublicationsTo" + capitalize(repository.getName()) + "Repository";
    }

    private static class GMMPublicationFactory implements NamedDomainObjectFactory<GMMPublication> {
        private final ObjectFactory objectFactory;
        private final ProviderFactory providerFactory;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;

        private GMMPublicationFactory(ObjectFactory objectFactory, ProviderFactory providerFactory, DependencyMetaDataProvider dependencyMetaDataProvider) {
            this.objectFactory = objectFactory;
            this.providerFactory = providerFactory;
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        }

        @Override
        public GMMPublication create(String name) {
            return objectFactory.newInstance(
                DefaultGMMPublication.class,
                name,
                createProjectIdentity(),
                objectFactory.newInstance(DefaultVersionMappingStrategy.class)
            );
        }

        private GMMProjectIdentity createProjectIdentity() {
            Module module = dependencyMetaDataProvider.getModule();
            GMMProjectIdentity identity = objectFactory.newInstance(GMMProjectIdentity.class);
            identity.getGroupId().convention(providerFactory.provider(module::getGroup));
            identity.getArtifactId().convention(providerFactory.provider(module::getName));
            identity.getVersion().convention(providerFactory.provider(module::getVersion));
            return identity;
        }
    }
}
