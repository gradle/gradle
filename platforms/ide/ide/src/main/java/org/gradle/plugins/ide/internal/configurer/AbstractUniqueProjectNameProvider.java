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
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

public abstract class AbstractUniqueProjectNameProvider implements UniqueProjectNameProvider {

    protected final ProjectStateRegistry projectRegistry;

    protected AbstractUniqueProjectNameProvider(ProjectStateRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    /**
     * Finds the "parent" project based on the build-tree path of the current project.
     * <p>
     * This is different from looking up the {@link ProjectState#getParent() parent project} inside a given build,
     * because a root project of a build does not have a parent. In the context of project hiearachy shown in the IDE, however,
     * we are looking for the "parent" project based on the build-tree path.
     * This means that the <b>"parent" project might belong to a different build.</b>
     */
    @Nullable
    protected ProjectIdentity findParentInBuildTree(ProjectIdentity projectIdentity) {
        Path parentInBuildTreePath = projectIdentity.getBuildTreePath().getParent();
        if (parentInBuildTreePath == null) {
            return null;
        }
        ProjectState parentInBuildTree = projectRegistry.findProjectState(parentInBuildTreePath);
        if (parentInBuildTree == null) {
            return null;
        }
        return parentInBuildTree.getIdentity();
    }
}
