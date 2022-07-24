/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.Describable;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;

/**
 * Captures user-provided information about a version control repository.
 *
 * @since 4.4
 */
public interface VersionControlSpec extends Describable {
    /**
     * Returns a {@link String} identifier which will be unique to this version
     * control specification among other version control specifications.
     */
    String getUniqueId();

    /**
     * Returns the name of the repository.
     */
    String getRepoName();

    /**
     * Returns the relative path to the root of the build within the repository.
     *
     * <p>Defaults to an empty relative path, meaning the root of the repository.
     *
     * @return the root directory of the build, relative to the root of this repository.
     * @since 4.5
     */
    String getRootDir();

    /**
     * Sets the relative path to the root of the build within the repository. Use an empty string to refer to the root of the repository.
     *
     * @param rootDir The root directory of the build, relative to the root of this repository.
     * @since 4.5
     */
    void setRootDir(String rootDir);

    /**
     * Configure injected plugins into this build.
     *
     * @param configuration the configuration action for adding injected plugins
     * @since 4.6
     */
    void plugins(Action<? super InjectedPluginDependencies> configuration);
}
