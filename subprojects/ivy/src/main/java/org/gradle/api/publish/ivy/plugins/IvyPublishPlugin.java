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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.DefaultIvyPublication;
import org.gradle.api.publish.ivy.internal.IvyModuleDescriptorInternal;
import org.gradle.api.publish.ivy.tasks.internal.IvyPublishDynamicTaskCreator;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Configures the project to publish a “main” IvyPublication to a “main” IvyArtifactRepository.
 *
 * Creates an IvyPublication named "main" in project.publications, configured to publish all of the visible configurations of the project.
 * Creates an IvyArtifactRepository
 *
 * @since 1.3
 */
@Incubating
public class IvyPublishPlugin implements Plugin<Project> {

    private final Instantiator instantiator;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;

    @Inject
    public IvyPublishPlugin(
            Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver
    ) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
    }

    public void apply(Project project) {
        project.getPlugins().apply(PublishingPlugin.class);
        PublishingExtension extension = project.getExtensions().getByType(PublishingExtension.class);

        // Create the default publication
        Set<Configuration> visibleConfigurations = project.getConfigurations().matching(new Spec<Configuration>() {
            public boolean isSatisfiedBy(Configuration configuration) {
                return configuration.isVisible();
            }
        });
        extension.getPublications().add(createPublication("ivy", project, visibleConfigurations));

        // Create publish tasks automatically for any Ivy publication and repository combinations
        Task publishLifecycleTask = project.getTasks().getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        IvyPublishDynamicTaskCreator publishTaskCreator = new IvyPublishDynamicTaskCreator(project.getTasks(), publishLifecycleTask);
        publishTaskCreator.monitor(extension.getPublications(), extension.getRepositories());
    }

    private IvyPublication createPublication(String name, final Project project, Set<? extends Configuration> configurations) {
        final DefaultIvyPublication publication = instantiator.newInstance(
                DefaultIvyPublication.class,
                name, instantiator, configurations, dependencyMetaDataProvider, fileResolver, project.getTasks()
        );

        IvyModuleDescriptorInternal descriptor = publication.getDescriptor();
        DslObject descriptorDslObject = new DslObject(descriptor);
        ConventionMapping descriptorConventionMapping = descriptorDslObject.getConventionMapping();
        descriptorConventionMapping.map("file", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), "publications/" + publication.getName() + "/ivy.xml");
            }
        });

        return publication;
    }
}
