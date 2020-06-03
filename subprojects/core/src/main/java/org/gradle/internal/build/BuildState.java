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

package org.gradle.internal.build;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.util.Path;

import java.io.File;

/**
 * Encapsulates the identity and state of a particular build in a build tree.
 *
 * Implementations are not yet entirely thread-safe, but should be.
 */
public interface BuildState {
    /**
     * Returns the identifier for this build. The identifier is fixed for the lifetime of the build.
     */
    BuildIdentifier getBuildIdentifier();

    /**
     * Returns an identifying path for this build in the build tree. This path is fixed for the lifetime of the build.
     */
    Path getIdentityPath();

    /**
     * Is this an implicit build? An implicit build is one that is managed by Gradle and which is not addressable by user build logic.
     */
    boolean isImplicitBuild();

    /**
     * The configured settings object for this build, if available.
     *
     * This should not be exposed directly, but should be behind some method that coordinates access from multiple threads.
     *
     * @throws IllegalStateException When the settings are not available for this build.
     */
    SettingsInternal getLoadedSettings() throws IllegalStateException;

    /**
     * Returns the factory to use to create children of this build.
     */
    NestedBuildFactory getNestedBuildFactory();

    /**
     * Note: may change value over the lifetime of this build, as this is often a function of the name of the root project in the build and this is not known until the settings have been configured. A temporary value will be returned when child builds need to create projects for some reason.
     */
    Path getCurrentPrefixForProjectsInChildBuilds();

    /**
     * Calculates the identity path for a project in this build.
     */
    Path getIdentityPathForProject(Path projectPath) throws IllegalStateException;

    /**
     * Calculates the identifier for a project in this build.
     */
    ProjectComponentIdentifier getIdentifierForProject(Path projectPath) throws IllegalStateException;

    /**
     * Asserts that the given build can be included by this build.
     */
    void assertCanAdd(IncludedBuildSpec includedBuildSpec);

    /**
     * The root directory of the build.
     */
    File getBuildRootDir();
}
