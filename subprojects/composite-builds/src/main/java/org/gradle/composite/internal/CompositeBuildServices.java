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

package org.gradle.composite.internal;

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.composite.internal.plugins.CompositeBuildPluginResolverContributor;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.service.scopes.BuildTreeScopeServices;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.plugin.use.resolve.internal.PluginResolverContributor;

public class CompositeBuildServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildTreeScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildBuildScopeServices());
    }

    private static class CompositeBuildTreeScopeServices {
        public BuildStateRegistry createIncludedBuildRegistry(CompositeBuildContext context, Instantiator instantiator, WorkerLeaseService workerLeaseService, ImmutableModuleIdentifierFactory moduleIdentifierFactory, GradleLauncherFactory gradleLauncherFactory, ListenerManager listenerManager, ServiceRegistry rootServices) {
            IncludedBuildFactory includedBuildFactory = new DefaultIncludedBuildFactory(instantiator, workerLeaseService);
            IncludedBuildDependencySubstitutionsBuilder dependencySubstitutionsBuilder = new IncludedBuildDependencySubstitutionsBuilder(context, moduleIdentifierFactory);
            return new DefaultIncludedBuildRegistry(includedBuildFactory, dependencySubstitutionsBuilder, gradleLauncherFactory, listenerManager, (BuildTreeScopeServices) rootServices);
        }

        public CompositeBuildContext createCompositeBuildContext() {
            return new DefaultBuildableCompositeBuildContext();
        }

        public LocalComponentProvider createLocalComponentProvider(ProjectStateRegistry projectRegistry) {
            return new LocalComponentInAnotherBuildProvider(projectRegistry, new IncludedBuildDependencyMetadataBuilder());
        }

        public IncludedBuildControllers createIncludedBuildControllers(ExecutorFactory executorFactory, BuildStateRegistry buildRegistry, ResourceLockCoordinationService coordinationService) {
            return new DefaultIncludedBuildControllers(executorFactory, buildRegistry, coordinationService);
        }

        public IncludedBuildTaskGraph createIncludedBuildTaskGraph(IncludedBuildControllers controllers) {
            return new DefaultIncludedBuildTaskGraph(controllers);
        }
    }

    private static class CompositeBuildBuildScopeServices {
        public ScriptClassPathInitializer createCompositeBuildClasspathResolver(IncludedBuildTaskGraph includedBuildTaskGraph, BuildState currentBuild) {
            return new CompositeBuildClassPathInitializer(includedBuildTaskGraph, currentBuild);
        }

        public PluginResolverContributor createPluginResolver(BuildStateRegistry buildRegistry, BuildState consumingBuild) {
            return new CompositeBuildPluginResolverContributor(buildRegistry, consumingBuild);
        }
    }

}
