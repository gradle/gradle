/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.resources;

import org.gradle.util.Path;

public class ProjectLockRegistry extends AbstractResourceLockRegistry<Path, ProjectLock> {
    private final boolean parallelEnabled;
    private final LockCache<Path, AllProjectsLock> allProjectsLocks;

    public ProjectLockRegistry(ResourceLockCoordinationService coordinationService, boolean parallelEnabled) {
        super(coordinationService);
        this.parallelEnabled = parallelEnabled;
        allProjectsLocks = new LockCache<Path, AllProjectsLock>(coordinationService, this);
    }

    public boolean getAllowsParallelExecution() {
        return parallelEnabled;
    }

    public ResourceLock getAllProjectsLock(final Path buildIdentityPath) {
        return allProjectsLocks.getOrRegisterResourceLock(buildIdentityPath, new ResourceLockProducer<Path, AllProjectsLock>() {
            @Override
            public AllProjectsLock create(Path key, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
                String displayName = "All projects of " + buildIdentityPath;
                return new AllProjectsLock(displayName, coordinationService, owner);
            }
        });
    }

    public ProjectLock getProjectLock(Path buildIdentityPath, Path projectIdentityPath) {
        return doGetResourceLock(buildIdentityPath, parallelEnabled ? projectIdentityPath : buildIdentityPath);
    }

    private ProjectLock doGetResourceLock(final Path buildIdentityPath, final Path lockPath) {
        return getOrRegisterResourceLock(lockPath, new ResourceLockProducer<Path, ProjectLock>() {
            @Override
            public ProjectLock create(Path projectPath, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
                String displayName = parallelEnabled ? "state of project " + lockPath : "state of build " + lockPath;
                return new ProjectLock(displayName, coordinationService, owner, getAllProjectsLock(buildIdentityPath));
            }
        });
    }
}
