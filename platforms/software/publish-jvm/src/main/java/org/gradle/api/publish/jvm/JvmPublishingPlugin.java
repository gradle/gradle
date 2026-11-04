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

package org.gradle.api.publish.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.jvm.internal.JvmPublicationInternal;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.Set;

import static org.apache.commons.lang3.StringUtils.capitalize;

/**
 * Adds publishing support common to all JVM publications, regardless of repository type.
 * <p>
 * Applies the {@link PublishingPlugin} and registers a task that generates Gradle Module Metadata
 * for each {@link JvmPublication}.
 *
 * @since 9.10.0
 */
@Incubating
public abstract class JvmPublishingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        project.getExtensions().configure(PublishingExtension.class, extension -> {
            NamedDomainObjectSet<JvmPublicationInternal> publications = extension.getPublications().withType(JvmPublicationInternal.class);
            TaskContainer tasks = project.getTasks();
            DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

            publications.all(publication -> createGenerateMetadataTask(tasks, publication, publications, buildDirectory));
        });
    }

    private static void createGenerateMetadataTask(TaskContainer tasks, JvmPublicationInternal publication, Set<JvmPublicationInternal> publications, DirectoryProperty buildDir) {
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
}
