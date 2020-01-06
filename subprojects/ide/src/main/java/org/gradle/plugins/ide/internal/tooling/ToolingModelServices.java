/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.plugins.ide.internal.configurer.DefaultUniqueProjectNameProvider;
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.internal.BuildScopeToolingModelBuilderRegistryAction;

public class ToolingModelServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeToolingServices());
    }

    private static class BuildScopeToolingServices {

        protected UniqueProjectNameProvider createBuildProjectRegistry(ProjectStateRegistry projectRegistry) {
            return new DefaultUniqueProjectNameProvider(projectRegistry);
        }

        protected BuildScopeToolingModelBuilderRegistryAction createIdeBuildScopeToolingModelBuilderRegistryAction(
            final ProjectTaskLister taskLister,
            final ProjectPublicationRegistry projectPublicationRegistry,
            final FileCollectionFactory fileCollectionFactory,
            final ServiceRegistry services) {

            return new BuildScopeToolingModelBuilderRegistryAction() {
                @Override
                public void execute(ToolingModelBuilderRegistry registry) {
                    GradleProjectBuilder gradleProjectBuilder = new GradleProjectBuilder();
                    IdeaModelBuilder ideaModelBuilder = new IdeaModelBuilder(gradleProjectBuilder, services);
                    registry.register(new RunBuildDependenciesTaskBuilder());
                    registry.register(new RunEclipseTasksBuilder());
                    registry.register(new EclipseModelBuilder(gradleProjectBuilder, services));
                    registry.register(ideaModelBuilder);
                    registry.register(gradleProjectBuilder);
                    registry.register(new GradleBuildBuilder());
                    registry.register(new BasicIdeaModelBuilder(ideaModelBuilder));
                    registry.register(new BuildInvocationsBuilder(taskLister));
                    registry.register(new PublicationsBuilder(projectPublicationRegistry));
                    registry.register(new BuildEnvironmentBuilder(fileCollectionFactory));
                }
            };
        }
    }
}
