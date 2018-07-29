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
import org.gradle.api.Incubating;

import java.net.URI;

/**
 * Configuration related to source dependencies.
 *
 * @since 4.4
 */
@Incubating
public interface SourceControl {
    /**
     * Configures VCS mappings.
     */
    void vcsMappings(Action<? super VcsMappings> configuration);

    /**
     * Returns the VCS mappings configuration.
     */
    VcsMappings getVcsMappings();

    /**
     * Registers a Git repository that contains some components that should be used as dependencies.
     * A Git repository can safely be registered multiple times.
     *
     * @param url The URL of the Git repository.
     * @return An object that can be used to configure the details of the repository.
     * @since 4.10
     */
    VersionControlRepository gitRepository(URI url);

    /**
     * Registers a Git repository that contains some components that should be used as dependencies.
     * A Git repository can safely be registered multiple times.
     *
     * @param url The URL of the Git repository.
     * @param configureAction An action to use to configure the repository.
     * @since 4.10
     */
    void gitRepository(URI url, Action<? super VersionControlRepository> configureAction);
}
