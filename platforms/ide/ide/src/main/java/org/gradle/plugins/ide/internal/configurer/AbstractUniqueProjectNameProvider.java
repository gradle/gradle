/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.configurer;

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateLookup;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

public abstract class AbstractUniqueProjectNameProvider implements UniqueProjectNameProvider {

    protected final ProjectStateLookup projectStateLookup;

    /**
     * Creates a new AbstractUniqueProjectNameProvider that uses the given lookup to resolve projects.
     *
     * @param projectStateLookup lookup used to find project states by build-tree path; stored for use by subclasses
     */
    protected AbstractUniqueProjectNameProvider(ProjectStateLookup projectStateLookup) {
        this.projectStateLookup = projectStateLookup;
    }

    /**
     * Locate the parent project corresponding to this project's build-tree parent path.
     *
     * <p>If this project has no parent path or no project is registered at the parent path, returns {@code null}.
     * The identified parent project may belong to a different build than the given project.
     *
     * @param projectIdentity the project whose build-tree parent path will be looked up
     * @return the parent project's identity if present, {@code null} otherwise
     */
    @Nullable
    protected ProjectIdentity findParentInBuildTree(ProjectIdentity projectIdentity) {
        Path parentInBuildTreePath = projectIdentity.getBuildTreePath().getParent();
        if (parentInBuildTreePath == null) {
            return null;
        }
        ProjectState parentInBuildTree = projectStateLookup.findProject(parentInBuildTreePath);
        if (parentInBuildTree == null) {
            return null;
        }
        return parentInBuildTree.getIdentity();
    }
}
