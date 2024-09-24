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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.MissingValueException;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.versionmapping.DefaultVersionMappingStrategy;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Adds the ability to publish in the Maven format to Maven repositories.
 *
 * @since 1.4
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_maven.html">Maven Publishing reference</a>
 */
public abstract class MavenPublishPlugin implements Plugin<Project> {

    public static final String PUBLISH_LOCAL_LIFECYCLE_TASK_NAME = "publishToMavenLocal";

    private final InstantiatorFactory instantiatorFactory;
    private final ObjectFactory objectFactory;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;
    private final ProviderFactory providerFactory;
    private final TaskDependencyFactory taskDependencyFactory;

    @Inject
    public MavenPublishPlugin(InstantiatorFactory instantiatorFactory, ObjectFactory objectFactory, DependencyMetaDataProvider dependencyMetaDataProvider,
                              FileResolver fileResolver,
                              ProviderFactory providerFactory,
                              TaskDependencyFactory taskDependencyFactory) {
        this.instantiatorFactory = instantiatorFactory;
        this.objectFactory = objectFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.providerFactory = providerFactory;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        final TaskContainer tasks = project.getTasks();
        tasks.register(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME, publish -> {
            publish.setDescription("Publishes all Maven publications produced by this project to the local Maven cache.");
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        });

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            extension.getPublications().registerFactory(MavenPublication.class, new MavenPublicationFactory(
                    dependencyMetaDataProvider,
                    instantiatorFactory.decorateLenient(),
                    fileResolver));
            realizePublishingTasksLater(project, extension);
        });
    }

    private void realizePublishingTasksLater(final Project project, final PublishingExtension extension) {
        final NamedDomainObjectSet<MavenPublicationInternal> mavenPublications = extension.getPublications().withType(MavenPublicationInternal.class);
        final TaskContainer tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        final TaskProvider<Task> publishLifecycleTask = tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        final TaskProvider<Task> publishLocalLifecycleTask = tasks.named(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);
        final NamedDomainObjectList<MavenArtifactRepository> repositories = extension.getRepositories().withType(MavenArtifactRepository.class);

        repositories.all(repository -> tasks.register(publishAllToSingleRepoTaskName(repository), publish -> {
            publish.setDescription("Publishes all Maven publications produced by this project to the " + repository.getName() + " repository.");
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
        }));

        mavenPublications.all(publication -> {
            createGenerateMetadataTask(tasks, publication, mavenPublications, buildDirectory);
            createGeneratePomTask(tasks, publication, buildDirectory);
            createLocalInstallTask(tasks, publishLocalLifecycleTask, publication);
            createPublishTasksForEachMavenRepo(project, tasks, publishLifecycleTask, publication, repositories);
        });
    }

    private String publishAllToSingleRepoTaskName(MavenArtifactRepository repository) {
        return "publishAllPublicationsTo" + capitalize(repository.getName()) + "Repository";
    }

    private void createPublishTasksForEachMavenRepo(final Project project, final TaskContainer tasks, final TaskProvider<Task> publishLifecycleTask, final MavenPublicationInternal publication, final NamedDomainObjectList<MavenArtifactRepository> repositories) {
        final String publicationName = publication.getName();
        repositories.all(repository -> {
            final String repositoryName = repository.getName();
            final String publishTaskName = "publish" + capitalize(publicationName) + "PublicationTo" + capitalize(repositoryName) + "Repository";
            tasks.register(publishTaskName, PublishToMavenRepository.class, publishTask -> {
                publishTask.setPublication(publication);
                publishTask.setRepository(repository);
                publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                publishTask.setDescription("Publishes Maven publication '" + publicationName + "' to Maven repository '" + repositoryName + "'.");
                project.getGradle().getTaskGraph().whenReady(graph -> {
                    if (graph.hasTask(publishTask)) {
                        validateCredentialsSetup(project, publishTask);
                    }
                });
            });

            publishLifecycleTask.configure(task -> task.dependsOn(publishTaskName));
            tasks.named(publishAllToSingleRepoTaskName(repository), publish -> publish.dependsOn(publishTaskName));
        });
    }

    private static void validateCredentialsSetup(Project project, PublishToMavenRepository publishToMavenRepository) {
        DefaultMavenArtifactRepository repository = (DefaultMavenArtifactRepository) publishToMavenRepository.getRepository();
        Credentials creds;
        try {
            creds = repository.getConfiguredCredentials().getOrNull();
        } catch (Exception e) {
            // In case of exception, we assume compatibility as this will fail later as well
            creds = null;
        }
        if (creds != null && !isUsingCredentialsProvider((ProjectInternal) project, repository.getName(), creds)) {
            publishToMavenRepository.notCompatibleWithConfigurationCache("Publishing to a repository without a credentials provider is not yet supported for the configuration cache");
        }
    }

    private static boolean isUsingCredentialsProvider(ProjectInternal project, String identity, Credentials toCheck) {
        ProviderFactory providerFactory = project.getServices().get(ProviderFactory.class);
        Credentials referenceCredentials;
        try {
            Provider<? extends Credentials> credentialsProvider;
            try {
                credentialsProvider = providerFactory.credentials(toCheck.getClass(), identity);
            } catch (IllegalArgumentException e) {
                // some possibilities are invalid repository names and invalid credential types
                // either way, this is not the place to validate that
                return false;
            }
            referenceCredentials = credentialsProvider.get();
        } catch (MissingValueException e) {
            return false;
        }
        return EqualsBuilder.reflectionEquals(toCheck, referenceCredentials);
    }

    private void createLocalInstallTask(TaskContainer tasks, final TaskProvider<Task> publishLocalLifecycleTask, final MavenPublicationInternal publication) {
        final String publicationName = publication.getName();
        final String installTaskName = "publish" + capitalize(publicationName) + "PublicationToMavenLocal";

        tasks.register(installTaskName, PublishToMavenLocal.class, publishLocalTask -> {
            publishLocalTask.setPublication(publication);
            publishLocalTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            publishLocalTask.setDescription("Publishes Maven publication '" + publicationName + "' to the local Maven repository.");
        });
        publishLocalLifecycleTask.configure(task -> task.dependsOn(installTaskName));
    }

    private void createGeneratePomTask(TaskContainer tasks, final MavenPublicationInternal publication, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generatePomFileFor" + capitalize(publicationName) + "Publication";
        TaskProvider<GenerateMavenPom> generatorTask = tasks.register(descriptorTaskName, GenerateMavenPom.class, generatePomTask -> {
            generatePomTask.setDescription("Generates the Maven POM file for publication '" + publicationName + "'.");
            generatePomTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            generatePomTask.setPom(publication.getPom());
            generatePomTask.getDestination().convention(
                buildDir.file("publications/" + publication.getName() + "/pom-default.xml")
            );
        });
        publication.setPomGenerator(generatorTask);
    }

    private void createGenerateMetadataTask(final TaskContainer tasks, final MavenPublicationInternal publication, final Set<? extends MavenPublicationInternal> publications, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generateMetadataFileFor" + capitalize(publicationName) + "Publication";
        TaskProvider<GenerateModuleMetadata> generatorTask = tasks.register(descriptorTaskName, GenerateModuleMetadata.class, generateTask -> {
            generateTask.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.");
            generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            generateTask.getPublication().set(publication);
            generateTask.getPublications().set(publications);
            generateTask.getOutputFile().convention(buildDir.file("publications/" + publication.getName() + "/module.json"));
        });
        publication.setModuleDescriptorGenerator(generatorTask);
    }

    private class MavenPublicationFactory implements NamedDomainObjectFactory<MavenPublication> {
        private final Instantiator instantiator;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final FileResolver fileResolver;

        private MavenPublicationFactory(DependencyMetaDataProvider dependencyMetaDataProvider,
                                        Instantiator instantiator,
                                        FileResolver fileResolver) {
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.instantiator = instantiator;
            this.fileResolver = fileResolver;
        }

        @Override
        public MavenPublication create(final String name) {
            NotationParser<Object, MavenArtifact> artifactNotationParser = new MavenArtifactNotationParserFactory(instantiator, fileResolver, taskDependencyFactory, objectFactory, providerFactory).create();
            VersionMappingStrategyInternal versionMappingStrategy = objectFactory.newInstance(DefaultVersionMappingStrategy.class);
            return objectFactory.newInstance(
                    DefaultMavenPublication.class,
                    name,
                    dependencyMetaDataProvider,
                    artifactNotationParser,
                    versionMappingStrategy
            );
        }
    }

}
