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

package org.gradle.language.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.language.LibraryDependencies;

import javax.inject.Inject;

public class DefaultLibraryDependencies extends DefaultComponentDependencies implements LibraryDependencies {
    private final Configuration apiDependencies;

    @Inject
    public DefaultLibraryDependencies(RoleBasedConfigurationContainerInternal configurations, String implementationName, String apiName) {
        super(configurations, implementationName);
        apiDependencies = configurations.dependenciesUnlocked(apiName).get();
        getImplementationDependencies().extendsFrom(apiDependencies);
    }

    public Configuration getApiDependencies() {
        return apiDependencies;
    }

    @Override
    public void api(Object notation) {
        apiDependencies.getDependencies().add(getDependencyHandler().create(notation));
    }

    @Override
    public void api(Object notation, Action<? super ExternalModuleDependency> action) {
        ExternalModuleDependency dependency = (ExternalModuleDependency) getDependencyHandler().create(notation);
        action.execute(dependency);
        apiDependencies.getDependencies().add(dependency);
    }
}
