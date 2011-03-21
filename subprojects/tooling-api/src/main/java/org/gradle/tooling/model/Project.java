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
 * Represents a Gradle project.
 */
public interface Project {
    /**
     * Returns the name of this project.
     *
     * @return The name.
     */
    String getName();

    /**
     * Returns the project directory for this project.
     *
     * @return The project directory. Does not return null.
     */
    File getProjectDirectory();

    /**
     * Returns the parent project of this project, if any.
     *
     * @return The parent, or null if this project has no parent.
     */
    Project getParent();

    /**
     * Returns the child projects of this project.
     *
     * @return The child projects. Returns an empty set if this project has no children.
     */
    DomainObjectSet<? extends Project> getChildren();
}
