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

import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ComponentSelectorNotationConverter;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.BuildTreeLocalComponentProvider;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.model.ObjectFactory;
import org.gradle.composite.internal.plugins.CompositeBuildPluginResolverContributor;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildFactory;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.buildtree.GlobalDependencySubstitutionRegistry;
import org.gradle.internal.composite.BuildIncludeListener;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.internal.service.scopes.BrokenBuildsCapturingListener;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.plugin.use.resolve.internal.PluginResolverContributor;

import java.util.Map;

public class CompositeBuildServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildTreeScopeServices());
        registration.add(BuildTreeLocalComponentProvider.class, HoldsProjectState.class, DefaultBuildTreeLocalComponentProvider.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(PluginResolverContributor.class, HoldsProjectState.class, CompositeBuildPluginResolverContributor.class);
    }

    private static class CompositeBuildTreeScopeServices implements ServiceRegistrationProvider {
        @Provides
        public void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(BuildStateFactory.class);
            serviceRegistration.add(IncludedBuildFactory.class, DefaultIncludedBuildFactory.class);
            serviceRegistration.add(BuildTreeWorkGraphController.class, DefaultIncludedBuildTaskGraph.class);
        }

        @Provides
        BuildIncludeListener createBuildIncludeListener(BuildModelParameters buildModelParameters, FailureFactory failureFactory) {
            if (buildModelParameters.isResilientModelBuilding()) {
                return new BrokenBuildsCapturingListener(failureFactory);
            }
            //ignored in non-resilient model building
            return new NoOpBuildIncludeListener();
        }

        @Provides
        public BuildStateRegistry createBuildStateRegistry(
            BuildModelParameters buildModelParameters,
            IncludedBuildFactory includedBuildFactory,
            ListenerManager listenerManager,
            BuildStateFactory buildStateFactory
        ) {
            if (buildModelParameters.isIsolatedProjects()) {
                // IP mode prohibits cycles in included plugin builds graph
                return new AcyclicIncludedBuildRegistry(includedBuildFactory, listenerManager, buildStateFactory);
            } else {
                return new DefaultIncludedBuildRegistry(includedBuildFactory, listenerManager, buildStateFactory);
            }
        }

        @Provides
        public GlobalDependencySubstitutionRegistry createGlobalDependencySubstitutionRegistry(
            CompositeBuildContext context,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ComponentSelectorNotationConverter componentSelectorNotationParser,
            AttributesFactory attributesFactory
        ) {
            NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
            return new IncludedBuildDependencySubstitutionsBuilder(context, instantiator, objectFactory, attributesFactory, componentSelectorNotationParser, capabilityNotationParser);
        }

        @Provides
        public CompositeBuildContext createCompositeBuildContext() {
            return new DefaultBuildableCompositeBuildContext();
        }

        private static class NoOpBuildIncludeListener implements BuildIncludeListener {
            @Override
            public void buildInclusionFailed(BuildState buildState, Exception exception) {
                // No-op in non-resilient model building
            }

            @Override
            public Map<BuildState, Failure> getBrokenBuilds() {
                throw new UnsupportedOperationException("getBrokenBuilds() should not be called in non-resilient model building");
            }

            @Override
            public void settingsScriptFailed(SettingsInternal settingsScript, LocationAwareException e) {
                // No-op in non-resilient model building
            }

            @Override
            public Map<SettingsInternal, Failure> getBrokenSettings() {
                throw new UnsupportedOperationException("getBrokenSettings() should not be called in non-resilient model building");
            }
        }
    }
}
