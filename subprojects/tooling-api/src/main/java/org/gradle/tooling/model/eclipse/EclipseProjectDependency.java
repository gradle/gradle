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
package org.gradle.tooling.model.eclipse;

import org.gradle.tooling.model.ProjectDependency;

/**
 * Represents a dependency on another Eclipse project.
 */
public interface EclipseProjectDependency extends ProjectDependency, EclipseClasspathEntry {
    /**
    * Returns the target of this dependency.
    *
    * @return The target project, or null for a dependency on a different build within a composite.
    */
    @Deprecated
    HierarchicalEclipseProject getTargetProject();

    /**
     * Returns the path to use for this project dependency.
     */
    String getPath();

    /**
     * Marks this dependency as exported.
     *
     * @return whether this dependency needs to be exported.
     * @since 2.5
     */
    boolean isExported();
}
