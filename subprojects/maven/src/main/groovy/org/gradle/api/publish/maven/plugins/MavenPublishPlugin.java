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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.MavenPublishDynamicTaskCreator;
import org.gradle.api.publish.maven.internal.ModuleBackedMavenProjectIdentity;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.Factory;
import org.gradle.internal.LazyIterable;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.CollectionUtils.InjectionStep;
import static org.gradle.util.CollectionUtils.inject;

/**
 * Adds the ability to publish in the Maven format to Maven repositories.
 *
 * @since 1.4
 */
@Incubating
public class MavenPublishPlugin implements Plugin<Project> {

    public static final String INSTALL_TO_MAVEN_LOCAL_TASK_NAME = "installToMavenLocal";

    private final Instantiator instantiator;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver) {
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
        extension.getPublications().add(createPublication("maven", project, visibleConfigurations));

        TaskContainer tasks = project.getTasks();

        // Create publish tasks automatically for any Maven publication and repository combinations
        Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        Task installToMavenLocalTask = tasks.add(INSTALL_TO_MAVEN_LOCAL_TASK_NAME);
        MavenPublishDynamicTaskCreator publishTaskCreator = new MavenPublishDynamicTaskCreator(tasks, publishLifecycleTask, installToMavenLocalTask);
        publishTaskCreator.monitor(extension.getPublications(), extension.getRepositories());
    }

    private MavenPublication createPublication(final String name, final Project project, final Set<? extends Configuration> configurations) {
        Callable<Object> pomDirCallable = new Callable<Object>() {
            public Object call() {
                return new File(project.getBuildDir(), "publications/" + name);
            }
        };

        ModuleBackedMavenProjectIdentity projectIdentity = new ModuleBackedMavenProjectIdentity(dependencyMetaDataProvider.getModule());

        DefaultMavenPublication publication = instantiator.newInstance(
                DefaultMavenPublication.class,
                name, instantiator, projectIdentity, asPublishArtifacts(configurations), null, fileResolver, project.getTasks()
        );

        ConventionMapping descriptorConventionMapping = new DslObject(publication).getConventionMapping();
        descriptorConventionMapping.map("pomDir", pomDirCallable);

        return publication;
    }

    private Iterable<PublishArtifact> asPublishArtifacts(final Set<? extends Configuration> configurations) {
        return new LazyIterable<PublishArtifact>(new Factory<Iterable<PublishArtifact>>() {
            public Iterable<PublishArtifact> create() {
                return inject(new HashSet<PublishArtifact>(), configurations, new Action<InjectionStep<HashSet<PublishArtifact>, Configuration>>() {
                    public void execute(InjectionStep<HashSet<PublishArtifact>, Configuration> step) {
                        step.getTarget().addAll(step.getItem().getAllArtifacts());
                    }
                });
            }
        });
    }

}
