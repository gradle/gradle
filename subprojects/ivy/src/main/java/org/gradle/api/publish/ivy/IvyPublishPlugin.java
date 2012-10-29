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

package org.gradle.api.publish.ivy;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.PublishingPlugin;
import org.gradle.api.publish.ivy.internal.DefaultIvyPublication;
import org.gradle.api.publish.ivy.internal.IvyDependencyDescriptorInternal;
import org.gradle.api.publish.ivy.internal.ProjectBackedModuleFactory;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Creates an IvyPublication named "ivy" in project.publications, configured to publish the archives configuration.
 */
@Incubating
public class IvyPublishPlugin implements Plugin<Project> {

    private final Instantiator instantiator;

    @Inject
    public IvyPublishPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        Configuration archivesConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);

        project.getPlugins().apply(PublishingPlugin.class);
        PublishingExtension extension = project.getExtensions().getByType(PublishingExtension.class);
        extension.getPublications().add(createPublication(archivesConfiguration, project));
    }

    private IvyPublication createPublication(Configuration configuration, final Project project) {
        final DefaultIvyPublication publication = instantiator.newInstance(
                DefaultIvyPublication.class,
                "ivy", instantiator, configuration, new ProjectBackedModuleFactory(project)
        );

        IvyDependencyDescriptorInternal descriptor = publication.getIvy();
        DslObject descriptorDslObject = new DslObject(descriptor);
        ConventionMapping descriptorConventionMapping = descriptorDslObject.getConventionMapping();
        descriptorConventionMapping.map("descriptorFile", new Callable<Object>() {
            public Object call() throws Exception {
                return new File(project.getBuildDir(), "publications/" + publication.getName() + "/ivy.xml");
            }
        });

        return publication;
    }
}
