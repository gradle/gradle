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

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.composite.internal.plugins.CompositeBuildPluginResolverContributor;
import org.gradle.internal.buildtree.GlobalDependencySubstitutionRegistry;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;
import org.gradle.internal.typeconversion.NotationParser;

public class CompositeBuildServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildSessionScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new CompositeBuildTreeScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(CompositeBuildClassPathInitializer.class);
        registration.add(CompositeBuildPluginResolverContributor.class);
    }

    private static class CompositeBuildSessionScopeServices {
        public ValueSnapshotterSerializerRegistry createCompositeBuildsValueSnapshotterSerializerRegistry() {
            return new CompositeBuildsValueSnapshotterSerializerRegistry();
        }
    }

    private static class CompositeBuildTreeScopeServices {
        public void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(BuildStateFactory.class);
            serviceRegistration.add(DefaultIncludedBuildFactory.class);
            serviceRegistration.add(DefaultIncludedBuildTaskGraph.class);
            serviceRegistration.add(DefaultIncludedBuildRegistry.class);
        }

        public GlobalDependencySubstitutionRegistry createGlobalDependencySubstitutionRegistry(CompositeBuildContext context,
                                                                                               Instantiator instantiator,
                                                                                               ObjectFactory objectFactory,
                                                                                               NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                                                                               ImmutableAttributesFactory attributesFactory) {
            NotationParser<Object, Capability> capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
            return new IncludedBuildDependencySubstitutionsBuilder(context, instantiator, objectFactory, attributesFactory, moduleSelectorNotationParser, capabilityNotationParser);
        }

        public CompositeBuildContext createCompositeBuildContext() {
            return new DefaultBuildableCompositeBuildContext();
        }

        public DefaultLocalComponentInAnotherBuildProvider createLocalComponentProvider(CalculatedValueContainerFactory calculatedValueContainerFactory) {
            return new DefaultLocalComponentInAnotherBuildProvider(new IncludedBuildDependencyMetadataBuilder(), calculatedValueContainerFactory);
        }
    }
}
