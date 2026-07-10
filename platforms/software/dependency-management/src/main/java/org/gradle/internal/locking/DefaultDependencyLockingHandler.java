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

package org.gradle.internal.locking;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.LockMode;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.function.Supplier;

public class DefaultDependencyLockingHandler implements DependencyLockingHandler {

    private final Supplier<ConfigurationContainer> configurationContainer;
    private final DependencyLockingProvider dependencyLockingProvider;

    private ConfigurationContainer configurations;

    public DefaultDependencyLockingHandler(Supplier<ConfigurationContainer> configurationContainer, DependencyLockingProvider dependencyLockingProvider) {
        this.configurationContainer = configurationContainer;
        this.dependencyLockingProvider = dependencyLockingProvider;
    }

    private ConfigurationContainer getConfigurations() {
        if (configurations == null) {
            configurations = configurationContainer.get();
        }
        return configurations;
    }

    @Override
    public void lockAllConfigurations() {
        getConfigurations().configureEach(configuration ->
            configuration.getResolutionStrategy().activateDependencyLocking()
        );
    }

    @Override
    public void unlockAllConfigurations() {
        getConfigurations().configureEach(configuration ->
            configuration.getResolutionStrategy().deactivateDependencyLocking()
        );
    }

    @Override
    public Property<LockMode> getLockMode() {
        return dependencyLockingProvider.getLockMode();
    }

    @Override
    public RegularFileProperty getLockFile() {
        return dependencyLockingProvider.getLockFile();
    }

    @Override
    public ListProperty<String> getIgnoredDependencies() {
        return dependencyLockingProvider.getIgnoredDependencies();
    }
}
