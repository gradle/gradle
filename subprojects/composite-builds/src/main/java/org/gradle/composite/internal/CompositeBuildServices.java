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
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ModuleSelectorNotationConverter;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.composite.internal.plugins.CompositeBuildPluginResolverContributor;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildFactory;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.buildtree.GlobalDependencySubstitutionRegistry;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;
import org.gradle.internal.typeconversion.NotationParser;

public class CompositeBuildServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildSessionScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildTreeScopeServices());
        registration.add(DefaultBuildTreeLocalComponentProvider.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(CompositeBuildPluginResolverContributor.class);
    }

    private static class CompositeBuildSessionScopeServices implements ServiceRegistrationProvider {
        @Provides
        public ValueSnapshotterSerializerRegistry createCompositeBuildsValueSnapshotterSerializerRegistry() {
            return new CompositeBuildsValueSnapshotterSerializerRegistry();
        }
    }

    private static class CompositeBuildTreeScopeServices implements ServiceRegistrationProvider {
        @Provides
        public void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(BuildStateFactory.class);
            serviceRegistration.add(DefaultIncludedBuildFactory.class);
            serviceRegistration.add(DefaultIncludedBuildTaskGraph.class);
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
            ModuleSelectorNotationConverter moduleSelectorNotationParser,
            AttributesFactory attributesFactory
        ) {
            NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
            return new IncludedBuildDependencySubstitutionsBuilder(context, instantiator, objectFactory, attributesFactory, moduleSelectorNotationParser, capabilityNotationParser);
        }

        @Provides
        public CompositeBuildContext createCompositeBuildContext() {
            return new DefaultBuildableCompositeBuildContext();
        }
    }
}
