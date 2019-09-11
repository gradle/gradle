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

package org.gradle.vcs;

import org.gradle.api.Action;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;

/**
 * Represents the details about a particular VCS repository that contains a build that produces zero or more components that can be used during dependency resolution.
 *
 * <p>To make a repository definition useful, call the {@link #producesModule(String)} method for each group and module name that is produced by the build in the repository. When resolving a dependency that matches the group and module name, Gradle will look for a matching version in the repository and if present will check out the source and build the binaries from that source.</p>
 *
 * @since 4.10
 */
public interface VersionControlRepository {
    /**
     * Declares that this repository produces (or may produce) component with the given group and module names.
     *
     * @param module The module identity, in "group:module" format.
     */
    void producesModule(String module);

    /**
     * Returns the relative path to the root of the build within the repository.
     *
     * <p>Defaults to an empty relative path, meaning the root of the repository.
     *
     * @return the root directory of the build, relative to the root of this repository.
     */
    String getRootDir();

    /**
     * Sets the relative path to the root of the build within the repository. Use an empty string to refer to the root of the repository.
     *
     * @param rootDir The root directory of the build, relative to the root of this repository.
     */
    void setRootDir(String rootDir);

    /**
     * Defines the plugins to be injected into the build.
     *
     * <p>Currently, plugins must be located in the calling build's `buildSrc` project.</p>
     *
     * @param configuration the configuration action for adding injected plugins
     * @since 4.6
     */
    void plugins(Action<? super InjectedPluginDependencies> configuration);
}
