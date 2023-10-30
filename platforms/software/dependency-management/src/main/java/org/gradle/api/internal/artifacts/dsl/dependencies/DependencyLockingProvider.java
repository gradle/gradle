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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.LockMode;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.Set;

/**
 * Provides dependency locking support for dependency configuration resolution.
 */
public interface DependencyLockingProvider {

    /**
     * Loads the lock state associated to the given configuration.
     *
     * @param configurationName the configuration to load lock state for
     *
     * @return the lock state of the configuration
     * @throws org.gradle.internal.locking.MissingLockStateException If the {@code LockMode} is {@link LockMode#STRICT} but no lock state can be found.
     */
    DependencyLockingState loadLockState(String configurationName);

    /**
     * Records the resolution result for a locked configuration.
     *
     * @param configurationName the configuration that was resolved
     * @param resolutionResult the resolution result information necessary for locking
     * @param changingResolvedModules any modules that are resolved and marked as changing which defeats locking purpose
     */
    void persistResolvedDependencies(String configurationName, Set<ModuleComponentIdentifier> resolutionResult, Set<ModuleComponentIdentifier> changingResolvedModules);

    /**
     * The current locking mode, exposed in the {@link org.gradle.api.artifacts.dsl.DependencyLockingHandler}.
     *
     * @return the locking mode
     */
    Property<LockMode> getLockMode();

    /**
     * Build finished hook for persisting per project lockfile
     */
    void buildFinished();

    /**
     * The file to be used as the per project lock file, exposed in the {@link DefaultDependencyHandler}.
     *
     * @return the lock file
     */
    RegularFileProperty getLockFile();

    /**
     * A list of module identifiers that are to be ignored in the lock state, exposed in the {@link org.gradle.api.artifacts.dsl.DependencyLockingHandler}.
     * @return
     */
    ListProperty<String> getIgnoredDependencies();

    /**
     * Confirms that a configuration is not locked.
     * This allows the lock state for said configuration to be dropped if it existed before.
     *
     * @param configurationName the unlocked configuration
     */
    void confirmConfigurationNotLocked(String configurationName);
}
