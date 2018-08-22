/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.internal.artifacts.repositories.resolver.AbstractDependencyMetadataAdapter;

abstract class PlatformSupport {
    static void addEnforcedPlatformRule(ComponentMetadataHandler components, final ModuleIdentifier module, final String version) {
        components.withModule(module, new Action<ComponentMetadataDetails>() {
            @Override
            public void execute(ComponentMetadataDetails componentMetadataDetails) {
                if (componentMetadataDetails.getId().getVersion().equals(version)) {
                    componentMetadataDetails.allVariants(new ForceDirectDependenciesAction());
                }
            }
        });
    }

    private static class ForceDirectDependenciesAction implements Action<VariantMetadata> {

        private  <T extends DependencyMetadata> void forceAll(DependenciesMetadata<T> dependencies) {
            for (T dependency : dependencies) {
                if (dependency instanceof AbstractDependencyMetadataAdapter) {
                    ((AbstractDependencyMetadataAdapter) dependency).forced();
                }
            }
        }

        private final Action<DependenciesMetadata<?>> forceDependencies = new Action<DependenciesMetadata<?>>() {
            @Override
            public void execute(DependenciesMetadata<?> dependencies) {
                forceAll(dependencies);
            }
        };

        @Override
        public void execute(VariantMetadata variantMetadata) {
            variantMetadata.withDependencies(forceDependencies);
            variantMetadata.withDependencyConstraints(forceDependencies);
        }
    }
}
