/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.model.gradle;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.model.BuildableElement;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.ProjectModel;

import java.io.File;

/**
 * Represents a Gradle project, isolated from the project hierarchy.
 *
 * @since 8.5
 */
@NonNullApi
public interface IsolatedGradleProject extends BuildableElement, ProjectModel {

    /**
     * Returns the identifier for this Gradle project.
     */
    @Override
    ProjectIdentifier getProjectIdentifier();

    /**
     * Returns the path of this project. This is a unique identifier for this project within the build.
     *
     * @return The path.
     */
    String getPath();

    /**
     * Returns the build script for this project.
     *
     * @return The build script.
     */
    GradleScript getBuildScript();

    /**
     * Returns the build directory for this project.
     *
     * @return The build directory.
     */
    File getBuildDirectory();

    /**
     * Returns the project directory for this project.
     *
     * @return The project directory.
     */
    File getProjectDirectory();

}
