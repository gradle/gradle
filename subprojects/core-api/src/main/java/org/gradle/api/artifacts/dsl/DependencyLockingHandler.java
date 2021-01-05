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

package org.gradle.api.artifacts.dsl;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * A {@code DependencyLockingHandler} manages the behaviour and configuration of dependency locking.
 *
 * @since 4.8
 */
public interface DependencyLockingHandler {

    /**
     * Convenience method for doing:
     *
     * configurations.all {
     *     resolutionStrategy.activateDependencyLocking()
     * }
     *
     */
    void lockAllConfigurations();

    /**
     * Convenience method for doing:
     *
     * configurations.all {
     *     resolutionStrategy.deactivateDependencyLocking()
     * }
     *
     * @since 6.0
     */
    void unlockAllConfigurations();

    /**
     * Allows to query the lock mode currently configured
     *
     * @since 6.1
     */
    Property<LockMode> getLockMode();

    /**
     * Allows to configure the file used for saving lock state
     * <p>
     * Make sure the lock file is unique per project and separate between the buildscript and project itself.
     * <p>
     * This requires opting in the support for per project single lock file.
     *
     * @since 6.4
     */
    RegularFileProperty getLockFile();

    /**
     * Allows to configure dependencies that will be ignored in the lock state.
     * <p>
     * The format of the entry is {@code <group>:<artifact>} where both can end with a {@code *} as a wildcard character.
     * The value {@code *:*} is not considered a valid value as it is equivalent to disabling locking.
     * <p>
     * These dependencies will not be written to the lock state and any references to them in lock state will be ignored at runtime.
     * It is thus not possible to set this property but still lock a matching entry by manually adding it to the lock state.
     *
     * @since 6.7
     */
    ListProperty<String> getIgnoredDependencies();

}
