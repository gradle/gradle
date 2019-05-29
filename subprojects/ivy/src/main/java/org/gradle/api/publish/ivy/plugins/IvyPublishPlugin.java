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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactNotationParserFactory;
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication;
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.publish.ivy.internal.versionmapping.DefaultVersionMappingStrategy;
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor;
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.model.Path;

import javax.inject.Inject;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Adds the ability to publish in the Ivy format to Ivy repositories.
 *
 * @since 1.3
 */
public class IvyPublishPlugin implements Plugin<Project> {

    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final FileCollectionFactory fileCollectionFactory;
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final FeaturePreviews featurePreviews;
    private CollectionCallbackActionDecorator collectionCallbackActionDecorator;

    @Inject
    public IvyPublishPlugin(Instantiator instantiator, ObjectFactory objectFactory, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver,
                            ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
                            ImmutableAttributesFactory immutableAttributesFactory, FeaturePreviews featurePreviews, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.projectDependencyResolver = projectDependencyResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.immutableAttributesFactory = immutableAttributesFactory;
        this.featurePreviews = featurePreviews;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getPublications().registerFactory(IvyPublication.class, new IvyPublicationFactory(dependencyMetaDataProvider,
                instantiator,
                objectFactory,
                fileResolver,
                collectionCallbackActionDecorator,
                project.getConfigurations(),
                project.getPluginManager(),
                project.getExtensions(),
                (AttributesSchemaInternal) project.getDependencies().getAttributesSchema()));
            createTasksLater(project, extension, project.getLayout().getBuildDirectory());
        });
    }

    private void createTasksLater(final Project project, final PublishingExtension publishingExtension, final DirectoryProperty buildDir) {
        final TaskContainer tasks = project.getTasks();
        final NamedDomainObjectSet<IvyPublicationInternal> publications = publishingExtension.getPublications().withType(IvyPublicationInternal.class);
        final NamedDomainObjectList<IvyArtifactRepository> repositories = publishingExtension.getRepositories().withType(IvyArtifactRepository.class);
        repositories.all(repository -> tasks.register(publishAllToSingleRepoTaskName(repository), publish -> {
            publish.setDescription("Publishes all Maven publications produced by this project to the " + repository.getName() + " repository.");
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        }));

        publications.all(publication -> {
            final String publicationName = publication.getName();
            createGenerateIvyDescriptorTask(tasks, publicationName, publication, buildDir);
            createGenerateMetadataTask(tasks, publication, publications, buildDir);
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
            if (descriptorTask.getDestination() == null) {
                descriptorTask.setDestination(buildDir.file("publications/" + publicationName + "/ivy.xml"));
            }
        });
        publication.setIvyDescriptorGenerator(generatorTask);
    }

    private void createGenerateMetadataTask(final TaskContainer tasks, final IvyPublicationInternal publication, final Set<IvyPublicationInternal> publications, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
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

    private class IvyPublicationFactory implements NamedDomainObjectFactory<IvyPublication> {
        private final Instantiator instantiator;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final ObjectFactory objectFactory;
        private final FileResolver fileResolver;
        private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
        private final ConfigurationContainer configurations;
        private final PluginManager plugins;
        private final ExtensionContainer extensionContainer;
        private final AttributesSchemaInternal attributesSchema;

        private IvyPublicationFactory(DependencyMetaDataProvider dependencyMetaDataProvider, Instantiator instantiator, ObjectFactory objectFactory, FileResolver fileResolver,
                                      CollectionCallbackActionDecorator collectionCallbackActionDecorator, ConfigurationContainer configurations,
                                      PluginManager plugins, ExtensionContainer extensionContainer,
                                      AttributesSchemaInternal attributesSchema) {
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.instantiator = instantiator;
            this.objectFactory = objectFactory;
            this.fileResolver = fileResolver;
            this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
            this.configurations = configurations;
            this.plugins = plugins;
            this.extensionContainer = extensionContainer;
            this.attributesSchema = attributesSchema;
        }

        @Override
        public IvyPublication create(String name) {
            Module module = dependencyMetaDataProvider.getModule();
            IvyPublicationIdentity publicationIdentity = new DefaultIvyPublicationIdentity(module);
            NotationParser<Object, IvyArtifact> notationParser = new IvyArtifactNotationParserFactory(instantiator, fileResolver, publicationIdentity).create();
            VersionMappingStrategyInternal versionMappingStrategy = instantiator.newInstance(DefaultVersionMappingStrategy.class,
                objectFactory,
                configurations,
                attributesSchema,
                immutableAttributesFactory);
            configureDefaultConfigurationsUsedWhenMappingToResolvedVersions(versionMappingStrategy);

            return instantiator.newInstance(
                DefaultIvyPublication.class,
                name, instantiator, objectFactory, publicationIdentity, notationParser, projectDependencyResolver, fileCollectionFactory, immutableAttributesFactory, featurePreviews,
                collectionCallbackActionDecorator, versionMappingStrategy
            );
        }

        private void configureDefaultConfigurationsUsedWhenMappingToResolvedVersions(VersionMappingStrategyInternal versionMappingStrategy) {
            plugins.withPlugin("org.gradle.java", plugin -> {
                SourceSet mainSourceSet = extensionContainer.getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                // setup the default configurations used when mapping to resolved versions
                versionMappingStrategy.defaultResolutionConfiguration(Usage.JAVA_API, mainSourceSet.getCompileClasspathConfigurationName());
                versionMappingStrategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, mainSourceSet.getRuntimeClasspathConfigurationName());
            });
            plugins.withPlugin("org.gradle.java-platform", plugin -> {
                versionMappingStrategy.defaultResolutionConfiguration(Usage.JAVA_API, JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME);
                versionMappingStrategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME);
            });
        }
    }

}
