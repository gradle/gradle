/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;

/**
 * Creates {@link VariantDependencyResolver} and {@link ComponentDependencyResolver} scoped to a particular variant,
 * taking into account whether dependency mapping or version mapping is enabled.
 */
public interface DependencyCoordinateResolverFactory {

    /**
     * Create coordinate resolvers for the given variant.
     */
    DependencyResolvers createCoordinateResolvers(SoftwareComponentVariant variant, VersionMappingStrategyInternal versionMappingStrategy);

    /**
     * Contains the variant and component coordinate resolver for a given variant.
     */
    class DependencyResolvers {

        private final VariantDependencyResolver variantResolver;
        private final ComponentDependencyResolver componentResolver;

        public DependencyResolvers(VariantDependencyResolver variantResolver, ComponentDependencyResolver componentResolver) {
            this.variantResolver = variantResolver;
            this.componentResolver = componentResolver;
        }

        public VariantDependencyResolver getVariantResolver() {
            return variantResolver;
        }

        public ComponentDependencyResolver getComponentResolver() {
            return componentResolver;
        }
    }
}
