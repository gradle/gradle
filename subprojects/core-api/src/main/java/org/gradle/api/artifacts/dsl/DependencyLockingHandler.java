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

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;

import java.io.File;

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
    @Incubating
    void unlockAllConfigurations();

    /**
     * Allows to query the lock mode currently configured
     *
     * @since 6.1
     */
    @Incubating
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
    @Incubating
    Property<File> getLockFile();

}
