/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Project;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;

import javax.annotation.Nullable;

public class DomainObjectProjectStateHandler {

    private final ProjectStateRegistry projectStateRegistry;
    private final DomainObjectContext domainObjectContext;
    private final ProjectFinder projectFinder;

    public DomainObjectProjectStateHandler(ProjectStateRegistry projectStateRegistry, DomainObjectContext domainObjectContext, ProjectFinder projectFinder) {
        this.projectStateRegistry = projectStateRegistry;
        this.domainObjectContext = domainObjectContext;
        this.projectFinder = projectFinder;
    }

    @Nullable
    public ProjectInternal maybeGetOwningProject() {
        if (domainObjectContext.getProjectPath() != null) {
            return projectFinder.findProject(domainObjectContext.getProjectPath().getPath());
        } else {
            return null;
        }
    }

    public boolean hasMutableProjectState() {
        Project project = maybeGetOwningProject();
        if (project != null) {
            ProjectState projectState = projectStateRegistry.stateFor(project);
            return projectState.hasMutableState();
        }
        return true;
    }

    public void withLenientState(Runnable runnable) {
        projectStateRegistry.withLenientState(runnable);
    }

    public ProjectStateRegistry.SafeExclusiveLock newExclusiveOperationLock() {
        return projectStateRegistry.newExclusiveOperationLock();
    }
}
