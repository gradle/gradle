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
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.plugins.ide.internal.configurer.DefaultUniqueProjectNameProvider;
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.internal.BuildScopeToolingModelBuilderRegistryAction;
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider;
import org.gradle.tooling.provider.model.internal.PluginApplyingBuilder;

public class ToolingModelServices extends AbstractGradleModuleServices {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeToolingServices());
    }

    private static class BuildScopeToolingServices implements ServiceRegistrationProvider {

        @Provides
        protected UniqueProjectNameProvider createBuildProjectRegistry(ProjectStateRegistry projectRegistry) {
            return new DefaultUniqueProjectNameProvider(projectRegistry);
        }

        @Provides
        protected BuildScopeToolingModelBuilderRegistryAction createIdeBuildScopeToolingModelBuilderRegistryAction(
            final ProjectTaskLister taskLister,
            final ProjectPublicationRegistry projectPublicationRegistry,
            final FileCollectionFactory fileCollectionFactory,
            final BuildStateRegistry buildStateRegistry,
            final ProjectStateRegistry projectStateRegistry,
            BuildModelParameters buildModelParameters,
            IntermediateToolingModelProvider intermediateToolingModelProvider
        ) {

            return new BuildScopeToolingModelBuilderRegistryAction() {
                @Override
                public void execute(ToolingModelBuilderRegistry registry) {
                    boolean isolatedProjects = buildModelParameters.isIsolatedProjects();
                    GradleProjectBuilderInternal gradleProjectBuilder = createGradleProjectBuilder(isolatedProjects);
                    IdeaModelBuilderInternal ideaModelBuilder = createIdeaModelBuilder(isolatedProjects, gradleProjectBuilder);
                    registry.register(new RunBuildDependenciesTaskBuilder());
                    registry.register(new RunEclipseTasksBuilder());
                    registry.register(new EclipseModelBuilder(gradleProjectBuilder, projectStateRegistry));
                    registry.register(ideaModelBuilder);
                    registry.register(gradleProjectBuilder);
                    registry.register(new GradleBuildBuilder(buildStateRegistry));
                    registry.register(new BasicIdeaModelBuilder(ideaModelBuilder));
                    registry.register(new BuildInvocationsBuilder(taskLister));
                    registry.register(new PublicationsBuilder(projectPublicationRegistry));
                    registry.register(new BuildEnvironmentBuilder(fileCollectionFactory));
                    registry.register(new IsolatedGradleProjectInternalBuilder());
                    registry.register(new IsolatedIdeaModuleInternalBuilder());
                    registry.register(new PluginApplyingBuilder());
                }

                private IdeaModelBuilderInternal createIdeaModelBuilder(boolean isolatedProjects, GradleProjectBuilderInternal gradleProjectBuilder) {
                    return isolatedProjects ? new IsolatedProjectsSafeIdeaModelBuilder(intermediateToolingModelProvider, gradleProjectBuilder) : new IdeaModelBuilder(gradleProjectBuilder);
                }

                private GradleProjectBuilderInternal createGradleProjectBuilder(boolean isolatedProjects) {
                    return isolatedProjects ? new IsolatedProjectsSafeGradleProjectBuilder(intermediateToolingModelProvider) : new GradleProjectBuilder();
                }
            };
        }
    }
}
