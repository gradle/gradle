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
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.MavenPublishDynamicTaskCreator;
import org.gradle.api.publish.maven.internal.MavenPublishLocalDynamicTaskCreator;
import org.gradle.api.publish.maven.internal.ModuleBackedMavenProjectIdentity;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

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

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(PublishingPlugin.class);
        final PublishingExtension extension = project.getExtensions().getByType(PublishingExtension.class);

        // Create the default publication for any components
        project.getComponents().all(new Action<SoftwareComponent>() {
            public void execute(SoftwareComponent softwareComponent) {
                if (!extension.getPublications().withType(MavenPublication.class).isEmpty()) {
                    throw new IllegalStateException("Cannot publish multiple components to Maven : need to fix this before we add another softwareComponent");
                }
                extension.getPublications().add(createPublication("maven", project, softwareComponent));
            }
        });

        TaskContainer tasks = project.getTasks();

        // Create publish tasks automatically for any Maven publication and repository combinations
        Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        MavenPublishDynamicTaskCreator publishTaskCreator = new MavenPublishDynamicTaskCreator(tasks, publishLifecycleTask);
        publishTaskCreator.monitor(extension.getPublications(), extension.getRepositories());

        // Create install tasks automatically for any Maven publication
        Task publishLocalLifecycleTask = tasks.add(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);
        MavenPublishLocalDynamicTaskCreator publishLocalTaskCreator = new MavenPublishLocalDynamicTaskCreator(tasks, publishLocalLifecycleTask);
        publishLocalTaskCreator.monitor(extension.getPublications());
    }

    private MavenPublication createPublication(final String name, final Project project, SoftwareComponent component) {
        Callable<Object> pomDirCallable = new Callable<Object>() {
            public Object call() {
                return new File(project.getBuildDir(), "publications/" + name);
            }
        };

        ModuleBackedMavenProjectIdentity projectIdentity = new ModuleBackedMavenProjectIdentity(dependencyMetaDataProvider.getModule());

        DefaultMavenPublication publication = instantiator.newInstance(
                DefaultMavenPublication.class,
                name, instantiator, projectIdentity, null
        );

        publication.from(component);

        ConventionMapping descriptorConventionMapping = new DslObject(publication).getConventionMapping();
        descriptorConventionMapping.map("pomDir", pomDirCallable);

        return publication;
    }

}
