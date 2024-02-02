/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

/**
 * Represents the root component identity of a detached configuration.
 * <p>
 * The root component of a detached configuration is adhoc and contains only that configuration.
 * For this reason, the root component of the detached configuration cannot declare the same
 * module coordinates as the project it was derived from, otherwise the resolution engine will
 * consider the detached root component an instance of that module.
 * <p>
 * The detached root component created from a given project does not advertise the variants
 * of that project and thus must have different coordinates.
 */
public class DetachedDependencyMetadataProvider implements DependencyMetaDataProvider {

    private final DependencyMetaDataProvider delegate;
    private final String suffix;

    public DetachedDependencyMetadataProvider(
        DependencyMetaDataProvider delegate,
        String suffix
    ) {
        this.delegate = delegate;
        this.suffix = suffix;
    }

    @Override
    public Module getModule() {
        Module module = delegate.getModule();

        return new DetachedModule(suffix, module);
    }

    private static class DetachedModule implements Module {
        private final Module module;
        private final String suffix;

        public DetachedModule(String suffix, Module module) {
            this.module = module;
            this.suffix = suffix;
        }

        @Override
        public ComponentIdentifier getComponentId() {
            return new DefaultModuleComponentIdentifier(
                DefaultModuleIdentifier.newId(getGroup(), getName()),
                getVersion()
            );
        }

        @Override
        public String getGroup() {
            return module.getGroup();
        }

        @Override
        public String getName() {
            return module.getName() + "-" + suffix;
        }

        @Override
        public String getVersion() {
            return module.getVersion();
        }

        @Override
        public String getStatus() {
            return module.getStatus();
        }
    }
}
