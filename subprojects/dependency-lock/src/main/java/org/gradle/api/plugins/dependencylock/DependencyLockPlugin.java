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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.dependencylock.DependencyLockFileGeneration;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.reader.DependencyLockReader;
import org.gradle.internal.dependencylock.reader.JsonDependencyLockReader;

import java.io.File;

public class DependencyLockPlugin implements Plugin<Project> {

    public static final String GENERATE_LOCK_FILE_TASK_NAME = "generateDependencyLock";

    @Override
    public void apply(Project project) {
        File lockFile = new File(project.getProjectDir(), "dependencies.lock");
        createLockFileGenerationTask(project, lockFile);

        DependencyLockReader dependencyLockReader = new JsonDependencyLockReader(lockFile);
        DependencyLock dependencyLock = dependencyLockReader.read();
    }

    private void createLockFileGenerationTask(Project project, File lockFile) {
        DependencyLockFileGeneration task = project.getTasks().create(GENERATE_LOCK_FILE_TASK_NAME, DependencyLockFileGeneration.class);
        task.setLockFile(lockFile);
    }
}
