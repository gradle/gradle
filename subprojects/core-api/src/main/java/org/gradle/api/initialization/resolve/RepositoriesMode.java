/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.initialization.resolve;

import org.gradle.api.Incubating;

/**
 * The repository mode configures how repositories are setup in the build.
 *
 * @since 6.8
 */
@Incubating
public enum RepositoriesMode {
    /**
     * If this mode is set, any repository declared on a project will cause
     * the project to use the repositories declared by the project, ignoring
     * those declared in settings.
     *
     * This is the default behavior.
     */
    PREFER_PROJECT,

    /**
     * If this mode is set, any repository declared directly in a project,
     * either directly or via a plugin, will be ignored.
     */
    PREFER_SETTINGS,

    /**
     * If this mode is set, any repository declared directly in a project,
     * either directly or via a plugin, will trigger a build error.
     */
    FAIL_ON_PROJECT_REPOS;
}
