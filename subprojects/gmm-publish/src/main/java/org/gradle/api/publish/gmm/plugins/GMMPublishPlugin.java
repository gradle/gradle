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

package org.gradle.api.publish.gmm.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Incubating;

import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.Set;

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

    @Inject
    public GMMPublishPlugin(Instantiator instantiator, ObjectFactory objectFactory, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            @SuppressWarnings("rawtypes")
            final NamedDomainObjectSet<PublicationInternal> publications = extension.getPublications().withType(PublicationInternal.class);
            final TaskContainer tasks = project.getTasks();
            final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

            publications.all(publication -> createGenerateMetadataTask(tasks, publication, publications, buildDirectory));
        });
    }

    @SuppressWarnings("unchecked")
    private void createGenerateMetadataTask(final TaskContainer tasks, @SuppressWarnings("rawtypes") final PublicationInternal publication, @SuppressWarnings("rawtypes") final Set<PublicationInternal> publications, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generateMetadataFileFor" + StringUtils.capitalize(publicationName) + "Publication";
        TaskProvider<? extends Task> generatorTask = tasks.register(descriptorTaskName, GenerateModuleMetadata.class, generateTask -> {
            generateTask.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.");
            generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
            generateTask.getPublication().set(publication);
            generateTask.getPublications().set(publications);
            generateTask.getOutputFile().convention(buildDir.file("publications/" + publication.getName() + "/module.json"));
        });
        publication.setModuleDescriptorGenerator(generatorTask);
    }
}
