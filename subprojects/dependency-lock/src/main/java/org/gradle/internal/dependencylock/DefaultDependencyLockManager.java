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

package org.gradle.internal.dependencylock;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.internal.dependencylock.io.writer.DependencyLockWriter;
import org.gradle.internal.dependencylock.io.writer.JsonDependencyLockWriter;
import org.gradle.internal.dependencylock.model.DependencyLock;

import java.io.File;

public class DefaultDependencyLockManager implements DependencyLockManager {

    private final DependencyLockState dependencyLockState;

    public DefaultDependencyLockManager(DependencyLockState dependencyLockState) {
        this.dependencyLockState = dependencyLockState;
    }

    @Override
    public void lockResolvedDependencies(Project project, Configuration configuration) {
        dependencyLockState.resolveAndPersist(project, configuration);
    }

    @Override
    public void writeLock(File lockFile) {
        DependencyLock dependencyLock = dependencyLockState.getDependencyLock();
        DependencyLockWriter dependencyLockWriter = new JsonDependencyLockWriter(lockFile);
        dependencyLockWriter.write(dependencyLock);
    }
}
