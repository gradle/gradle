/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

/**
 * A set of parameters controlling the behavior of a dependency management instance.
 */
public class DependencyManagementParameters {

    private final DisplayName displayName;
    private final @Nullable String dependencyLockingPrefix;
    private final boolean isJvmEnvironment;
    private final boolean ignoreGlobalRepositories;
    private final boolean ignoreGlobalComponentMetadataRules;

    public DependencyManagementParameters(
        DisplayName displayName,
        @Nullable String dependencyLockingPrefix,
        boolean isJvmEnvironment,
        boolean ignoreGlobalRepositories,
        boolean ignoreGlobalComponentMetadataRules
    ) {
        this.displayName = displayName;
        this.dependencyLockingPrefix = dependencyLockingPrefix;
        this.isJvmEnvironment = isJvmEnvironment;
        this.ignoreGlobalRepositories = ignoreGlobalRepositories;
        this.ignoreGlobalComponentMetadataRules = ignoreGlobalComponentMetadataRules;
    }

    /**
     * Returns a human-readable description of the owner of the dependency
     * management instance, for use in error messages and logging.
     */
    public DisplayName getDisplayName() {
        return displayName;
    }

    /**
     * True if dependency locking is enabled, false otherwise.
     */
    public boolean isDependencyLockingEnabled() {
        return dependencyLockingPrefix != null;
    }

    /**
     * Returns the dependency locking prefix to prepend to all dependency lock files.
     *
     * @throws IllegalStateException if dependency locking is not enabled.
     */
    public String getDependencyLockingPrefix() {
        if (dependencyLockingPrefix == null) {
            throw new IllegalStateException("Dependency locking is not enabled");
        }
        return dependencyLockingPrefix;
    }

    /**
     * Returns true if the dependency management instance is running in a
     * JVM environment, false otherwise.
     */
    public boolean isJvmEnvironment() {
        return isJvmEnvironment;
    }

    /**
     * Returns true if the dependency management instance should ignore
     * global repositories, false if they should be potentially merged
     * into the instance's repository manager.
     */
    // TODO: This should be richer, and should include actual immutable
    // descriptions of the repositories to include.
    public boolean ignoreGlobalRepositories() {
        return ignoreGlobalRepositories;
    }

    /**
     * Returns true if the dependency management instance should ignore
     * global component metadata rules, false if they should be potentially merged
     * into the instance's component metadata rule manager.
     */
    // TODO: This should be richer, and should include actual immutable
    // descriptions of the rules to include.
    public boolean ignoreGlobalComponentMetadataRules() {
        return ignoreGlobalComponentMetadataRules;
    }

}
