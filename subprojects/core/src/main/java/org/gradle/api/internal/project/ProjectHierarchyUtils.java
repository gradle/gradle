/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.Project;

import java.util.Collection;

public class ProjectHierarchyUtils {
    /**
     * Gets the collection of the direct child projects from {@code thisProject}.
     * This method is safe to use in the Gradle internal code, as it does not impose the contracts
     * specific to the project public API.
     *
     * @param thisProject the project to get the children from. Expected to be {@link ProjectInternal}.
     * @return the collection of the child projects
     *
     * @see Project#getChildProjects()
     * @see ProjectInternal#getChildProjectsUnchecked()
     */
    public static Collection<Project> getChildProjectsForInternalUse(Project thisProject) {
        return ((ProjectInternal) thisProject).getChildProjectsUnchecked().values();
    }
}
