/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.model;

import org.gradle.api.Incubating;

/**
 * Identifies a Gradle project.
 *
 * <p>
 *     A Gradle Project is a project in a multi-project Gradle build or a single "standalone" project.
 * </p>
 *
 * @since 2.13
 */
@Incubating
public interface ProjectIdentifier extends Model {

    /**
     * The path of the project, relative to its build.
     *
     * @return the path, never null
     * @since 3.3
     */
    String getProjectPath();

    /**
     * Identifier of the build this project is a member of.
     *
     * @return build identifier, never null.
     */
    BuildIdentifier getBuildIdentifier();
}
