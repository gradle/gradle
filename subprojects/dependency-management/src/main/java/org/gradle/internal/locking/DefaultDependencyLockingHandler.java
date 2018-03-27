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

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.LockHandling;
import org.gradle.api.internal.file.FileOperations;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultDependencyLockingHandler implements DependencyLockingHandlerInternal {

    public static final DependencyLockingProvider NO_OP_LOCKING_PROVIDER = new NoOpDependencyLockingProvider();
    private final ConfigurationContainer configurationContainer;

    private DependencyLockingProvider dependencyLockingProvider;

    public DefaultDependencyLockingHandler(ConfigurationContainer configurationContainer, DependencyFactory dependencyFactory, FileOperations fileOperations) {
        this.configurationContainer = configurationContainer;
        this.dependencyLockingProvider = new DefaultDependencyLockingProvider(dependencyFactory, fileOperations);
    }

    @Override
    public void lockAllConfigurations() {
        configurationContainer.all(new Action<Configuration>() {
            @Override
            public void execute(Configuration configuration) {
                configuration.getResolutionStrategy().activateDependencyLocking();
            }
        });
    }

    @Override
    public DependencyLockingProvider getDependencyLockingProvider() {
        return dependencyLockingProvider;
    }

    private static class NoOpDependencyLockingProvider implements DependencyLockingProvider {

        @Override
        public Set<DependencyConstraint> getLockedDependencies(String configurationName) {
            return Collections.emptySet();
        }

        @Override
        public void updateLockHandling(LockHandling lockHandling) {
            // No-op
        }

        @Override
        public void validateLockAligned(String configurationName, Map<String, ModuleComponentIdentifier> modules) {
            // No-op
        }

        @Override
        public void setUpgradeModules(List<String> strings) {
            // No-op
        }
    }
}
