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
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.language.ComponentDependencies;

import javax.inject.Inject;

public class DefaultComponentDependencies implements ComponentDependencies {
    private final Configuration implementation;

    @Inject
    public DefaultComponentDependencies(RoleBasedConfigurationContainerInternal configurations, String implementationName) {
        implementation = configurations.dependencyScopeUnlocked(implementationName).get();
    }

    public Configuration getImplementationDependencies() {
        return implementation;
    }

    @Inject
    protected DependencyHandler getDependencyHandler() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void implementation(Object notation) {
        implementation.getDependencies().add(getDependencyHandler().create(notation));
    }

    @Override
    public void implementation(Object notation, Action<? super ExternalModuleDependency> action) {
        ExternalModuleDependency dependency = (ExternalModuleDependency) getDependencyHandler().create(notation);
        action.execute(dependency);
        implementation.getDependencies().add(dependency);
    }
}
