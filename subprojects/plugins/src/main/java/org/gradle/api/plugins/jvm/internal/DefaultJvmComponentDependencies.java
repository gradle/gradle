/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.jvm.JvmComponentDependencies;

import javax.inject.Inject;

public class DefaultJvmComponentDependencies implements JvmComponentDependencies {
    private final Configuration implementation;
    private final Configuration compileOnly;
    private final Configuration runtimeOnly;

    @Inject
    public DefaultJvmComponentDependencies(Configuration implementation, Configuration compileOnly, Configuration runtimeOnly) {
        this.implementation = implementation;
        this.compileOnly = compileOnly;
        this.runtimeOnly = runtimeOnly;
    }

    @Inject
    protected DependencyHandler getDependencyHandler() {
        throw new UnsupportedOperationException();
    }

    private void addToConfiguration(Configuration bucket, Object dependencyNotation) {
        bucket.getDependencies().add(getDependencyHandler().create(dependencyNotation));
    }

    private void addToConfiguration(Configuration bucket, Object dependencyNotation, Action<? super ExternalModuleDependency> configuration) {
        ExternalModuleDependency dependency = (ExternalModuleDependency) getDependencyHandler().create(dependencyNotation);
        configuration.execute(dependency);
        bucket.getDependencies().add(dependency);
    }

    @Override
    public void implementation(Object dependencyNotation) {
        addToConfiguration(implementation, dependencyNotation);
    }

    @Override
    public void implementation(Object dependencyNotation, Action<? super ExternalModuleDependency> configuration) {
        addToConfiguration(implementation, dependencyNotation, configuration);
    }

    @Override
    public void runtimeOnly(Object dependencyNotation) {
        addToConfiguration(runtimeOnly, dependencyNotation);
    }

    @Override
    public void runtimeOnly(Object dependencyNotation, Action<? super ExternalModuleDependency> configuration) {
        addToConfiguration(runtimeOnly, dependencyNotation, configuration);
    }

    @Override
    public void compileOnly(Object dependencyNotation) {
        addToConfiguration(compileOnly, dependencyNotation);
    }

    @Override
    public void compileOnly(Object dependencyNotation, Action<? super ExternalModuleDependency> configuration) {
        addToConfiguration(compileOnly, dependencyNotation, configuration);
    }
}
