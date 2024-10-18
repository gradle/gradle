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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactNotationParserFactory;
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates;
import org.gradle.api.publish.ivy.internal.versionmapping.DefaultVersionMappingStrategy;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.model.Path;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Adds the ability to publish in the Ivy format to Ivy repositories.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_ivy.html">Ivy Publishing reference</a>
 * @since 1.3
 */
public abstract class IvyPublishPlugin implements Plugin<Project> {
    private final static Logger LOGGER = Logging.getLogger(IvyPublishPlugin.class);

    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;
    private final ProviderFactory providerFactory;

    @Inject
    public IvyPublishPlugin(Instantiator instantiator, ObjectFactory objectFactory, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver, ProviderFactory providerFactory) {
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.providerFactory = providerFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getPublications().registerFactory(IvyPublication.class,
                new IvyPublicationFactory(dependencyMetaDataProvider,
                    instantiator,
                    objectFactory,
                    fileResolver,
                    ((ProjectInternal) project).getTaskDependencyFactory(),
                    providerFactory
                )
            );
            createTasksLater(project, extension, project.getLayout().getBuildDirectory());
        });
    }

    private void createTasksLater(final Project project, final PublishingExtension publishingExtension, final DirectoryProperty buildDir) {
        final TaskContainer tasks = project.getTasks();
        final NamedDomainObjectSet<IvyPublicationInternal> publications = publishingExtension.getPublications().withType(IvyPublicationInternal.class);
        final NamedDomainObjectList<IvyArtifactRepository> repositories = publishingExtension.getRepositories().withType(IvyArtifactRepository.class);
        repositories.all(repository -> tasks.register(publishAllToSingleRepoTaskName(repository), publish -> {
            publish.setDescription("Publishes all Ivy publications produced by this project to the " + repository.getName() + " repository.");
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        }));

        publications.all(publication -> {
            final String publicationName = publication.getName();
            createGenerateIvyDescriptorTask(tasks, publicationName, publication, buildDir);
            createGenerateMetadataTask(tasks, publication, publications, buildDir, repositories);
            createPublishTaskForEachRepository(tasks, publication, publicationName, repositories);
        });
    }

    private String publishAllToSingleRepoTaskName(IvyArtifactRepository repository) {
        return "publishAllPublicationsTo" + capitalize(repository.getName()) + "Repository";
    }

    private void createPublishTaskForEachRepository(final TaskContainer tasks, final IvyPublicationInternal publication, final String publicationName, NamedDomainObjectList<IvyArtifactRepository> repositories) {
        repositories.all(repository -> {
            final String repositoryName = repository.getName();
            final String publishTaskName = "publish" + capitalize(publicationName) + "PublicationTo" + capitalize(repositoryName) + "Repository";
            createPublishToRepositoryTask(tasks, publication, publicationName, repository, repositoryName, publishTaskName);
        });
    }

    private void createPublishToRepositoryTask(TaskContainer tasks, final IvyPublicationInternal publication, final String publicationName, final IvyArtifactRepository repository, final String repositoryName, final String publishTaskName) {
        tasks.register(publishTaskName, PublishToIvyRepository.class, publishTask -> {
            publishTask.setPublication(publication);
            publishTask.setRepository(repository);
            publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            publishTask.setDescription("Publishes Ivy publication '" + publicationName + "' to Ivy repository '" + repositoryName + "'.");
        });
        tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME, task -> task.dependsOn(publishTaskName));
        tasks.named(publishAllToSingleRepoTaskName(repository), publish -> publish.dependsOn(publishTaskName));
    }

    private void createGenerateIvyDescriptorTask(TaskContainer tasks, final String publicationName, final IvyPublicationInternal publication, @Path("buildDir") final DirectoryProperty buildDir) {
        final String descriptorTaskName = "generateDescriptorFileFor" + capitalize(publicationName) + "Publication";

        TaskProvider<GenerateIvyDescriptor> generatorTask = tasks.register(descriptorTaskName, GenerateIvyDescriptor.class, descriptorTask -> {
            descriptorTask.setDescription("Generates the Ivy Module Descriptor XML file for publication '" + publicationName + "'.");
            descriptorTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            descriptorTask.setDescriptor(publication.getDescriptor());
            descriptorTask.getDestination().convention(
                buildDir.file("publications/" + publicationName + "/ivy.xml")
            );
        });
        publication.setIvyDescriptorGenerator(generatorTask);
    }

    private void createGenerateMetadataTask(final TaskContainer tasks, final IvyPublicationInternal publication, final Set<IvyPublicationInternal> publications, final DirectoryProperty buildDir, NamedDomainObjectList<IvyArtifactRepository> repositories) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generateMetadataFileFor" + capitalize(publicationName) + "Publication";
        TaskProvider<GenerateModuleMetadata> generatorTask = tasks.register(descriptorTaskName, GenerateModuleMetadata.class, generateTask -> {
            generateTask.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.");
            generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            generateTask.getPublication().set(publication);
            generateTask.getPublications().set(publications);
            generateTask.getOutputFile().convention(buildDir.file("publications/" + publicationName + "/module.json"));
            disableGradleMetadataGenerationIfCustomLayout(repositories, generateTask);
        });
        publication.setModuleDescriptorGenerator(generatorTask);
    }

    private static void disableGradleMetadataGenerationIfCustomLayout(NamedDomainObjectList<IvyArtifactRepository> repositories, GenerateModuleMetadata generateTask) {
        Provider<Boolean> standard = new DefaultProvider<>(() -> repositories.stream().allMatch(IvyPublishPlugin::hasStandardPattern));
        generateTask.onlyIf("The Ivy repositories follow the standard layout", Cast.uncheckedCast(new CheckStandardLayoutSpec(standard)));
    }

    private static class CheckStandardLayoutSpec implements Spec<GenerateModuleMetadata> {
        private final Provider<Boolean> standard;
        private final AtomicBoolean didWarn = new AtomicBoolean();

        CheckStandardLayoutSpec(Provider<Boolean> standard) {
            this.standard = standard;
        }

        @Override
        public boolean isSatisfiedBy(GenerateModuleMetadata element) {
            if (!standard.get() && !didWarn.getAndSet(true)) {
                LOGGER.warn("Publication of Gradle Module Metadata is disabled because you have configured an Ivy repository with a non-standard layout");
            }
            return standard.get();
        }
    }

    private static boolean hasStandardPattern(ArtifactRepository ivyArtifactRepository) {
        DefaultIvyArtifactRepository repo = (DefaultIvyArtifactRepository) ivyArtifactRepository;
        return repo.hasStandardPattern();
    }

    private static class IvyPublicationFactory implements NamedDomainObjectFactory<IvyPublication> {
        private final Instantiator instantiator;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final ObjectFactory objectFactory;
        private final FileResolver fileResolver;
        private final TaskDependencyFactory taskDependencyFactory;
        private final ProviderFactory providerFactory;

        private IvyPublicationFactory(
            DependencyMetaDataProvider dependencyMetaDataProvider, Instantiator instantiator, ObjectFactory objectFactory, FileResolver fileResolver,
            TaskDependencyFactory taskDependencyFactory, ProviderFactory providerFactory
        ) {
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.instantiator = instantiator;
            this.objectFactory = objectFactory;
            this.fileResolver = fileResolver;
            this.taskDependencyFactory = taskDependencyFactory;
            this.providerFactory = providerFactory;
        }

        @Override
        public IvyPublication create(String name) {
            Module module = dependencyMetaDataProvider.getModule();
            IvyPublicationCoordinates publicationIdentity = objectFactory.newInstance(IvyPublicationCoordinates.class);
            publicationIdentity.getOrganisation().set(providerFactory.provider(module::getGroup));
            publicationIdentity.getModule().set(providerFactory.provider(module::getName));
            publicationIdentity.getRevision().set(providerFactory.provider(module::getVersion));

            NotationParser<Object, IvyArtifact> notationParser = new IvyArtifactNotationParserFactory(instantiator, fileResolver, publicationIdentity, taskDependencyFactory, providerFactory, objectFactory).create();
            VersionMappingStrategyInternal versionMappingStrategy = objectFactory.newInstance(DefaultVersionMappingStrategy.class);

            return objectFactory.newInstance(DefaultIvyPublication.class, name, publicationIdentity, notationParser, versionMappingStrategy);
        }
    }

}
