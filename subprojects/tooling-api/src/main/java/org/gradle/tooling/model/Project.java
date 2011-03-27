/*
 * Copyright 2011 the original author or authors.
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

import java.io.File;

/**
 * Represents a project of some kind.
 */
public interface Project {
    /**
     * Returns the fully-qualified path of this project. This is a unique identifier for the project.
     *
     * @return The path.
     */
    String getPath();

    /**
     * Returns the name of this project. Note that the name is not a unique identifier for the project.
     *
     * @return The name.
     */
    String getName();

    /**
     * Returns the description of this project.
     *
     * @return The description. May be null.
     */
    String getDescription();

    /**
     * Returns the project directory for this project.
     *
     * @return The project directory.
     */
    File getProjectDirectory();
}
