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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Project;

import java.io.File;

public class DependencyLockFileGenerationListener extends BuildAdapter {

    private final DependencyLockManager dependencyLockManager;

    public DependencyLockFileGenerationListener(DependencyLockManager dependencyLockManager) {
        this.dependencyLockManager = dependencyLockManager;
    }

    @Override
    public void buildFinished(BuildResult result) {
        if (successfulBuild(result)) {
            Project rootProject = result.getGradle().getRootProject();
            File lockFile = getLockFile(rootProject);
            dependencyLockManager.writeLock(lockFile);
        }
    }

    private boolean successfulBuild(BuildResult result) {
        return result.getFailure() == null;
    }

    private File getLockFile(Project rootProject) {
        File lockDir = rootProject.file("gradle");
        return new File(lockDir, "dependencies.lock");
    }
}
