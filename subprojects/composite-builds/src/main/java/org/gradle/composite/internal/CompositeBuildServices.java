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

import org.gradle.StartParameter;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactBuilder;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.tasks.TaskReferenceResolver;
import org.gradle.initialization.BuildIdentity;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;

public class CompositeBuildServices extends AbstractPluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildTreeScopeServices());
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildBuildScopeServices());
    }

    private static class CompositeBuildGlobalScopeServices {
    }

    private static class CompositeBuildTreeScopeServices {
        public DefaultIncludedBuilds createIncludedBuilds() {
            return new DefaultIncludedBuilds();
        }

        public CompositeBuildContext createCompositeBuildContext(IncludedBuilds includedBuilds, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            return new DefaultBuildableCompositeBuildContext(includedBuilds, moduleIdentifierFactory);
        }

        public DefaultProjectPathRegistry createProjectPathRegistry() {
            return new DefaultProjectPathRegistry();
        }

        public CompositeContextBuilder createCompositeContextBuilder(DefaultIncludedBuilds includedBuilds, DefaultProjectPathRegistry projectRegistry, CompositeBuildContext context) {
            return new DefaultCompositeContextBuilder(includedBuilds, projectRegistry, context);
        }

        public IncludedBuildControllers createIncludedBuildControllers(ExecutorFactory executorFactory, IncludedBuilds includedBuilds) {
            return new DefaultIncludedBuildControllers(executorFactory, includedBuilds);
        }

        public IncludedBuildTaskGraph createIncludedBuildTaskGraph(IncludedBuildControllers controllers) {
            return new DefaultIncludedBuildTaskGraph(controllers);
        }

        public IncludedBuildArtifactBuilder createIncludedBuildArtifactBuilder(IncludedBuildTaskGraph includedBuildTaskGraph) {
            return new IncludedBuildArtifactBuilder(includedBuildTaskGraph);
        }
    }

    private static class CompositeBuildBuildScopeServices {
        public IncludedBuildFactory createIncludedBuildFactory(Instantiator instantiator, StartParameter startParameter, NestedBuildFactory nestedBuildFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, WorkerLeaseService workerLeaseService) {
            return new DefaultIncludedBuildFactory(instantiator, startParameter, nestedBuildFactory, moduleIdentifierFactory, workerLeaseService);
        }

        public TaskReferenceResolver createResolver(IncludedBuildTaskGraph includedBuilds, BuildIdentity buildIdentity) {
            return new IncludedBuildTaskReferenceResolver(includedBuilds, buildIdentity);
        }

        public ProjectArtifactBuilder createProjectArtifactBuilder(IncludedBuildArtifactBuilder builder, BuildIdentity buildIdentity) {
            return new CompositeProjectArtifactBuilder(builder, buildIdentity);
        }
    }

}
