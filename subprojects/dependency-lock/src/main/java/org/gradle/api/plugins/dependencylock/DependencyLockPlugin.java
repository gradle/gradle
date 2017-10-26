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

package org.gradle.api.plugins.dependencylock;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.dependencylock.DependencyLockManager;

/**
 * <p>A {@link Plugin} which creates locked versions for resolved dependencies and uses them in subsequent builds.</p>
 *
 * <p>The plugin is meant to be applied to the root project of a build only.</p>
 *
 * @since 4.4
 */
@Incubating
public class DependencyLockPlugin implements Plugin<ProjectInternal> {

    @Override
    public void apply(ProjectInternal project) {
        if (isRootProject(project)) {
            getDependencyLockManager(project).initiate(project);
        }
    }

    private boolean isRootProject(Project project) {
        return project.getParent() == null;
    }

    private DependencyLockManager getDependencyLockManager(ProjectInternal project) {
        return project.getServices().get(DependencyLockManager.class);
    }
}
