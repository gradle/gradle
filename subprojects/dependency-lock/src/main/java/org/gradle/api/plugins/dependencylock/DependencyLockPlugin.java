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

import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.dependencylock.DefaultDependencyLockCreator;
import org.gradle.internal.dependencylock.DependencyLockCreator;
import org.gradle.internal.dependencylock.io.writer.DependencyLockWriter;
import org.gradle.internal.dependencylock.io.writer.JsonDependencyLockWriter;
import org.gradle.internal.dependencylock.model.DependencyLock;

import java.io.File;

public class DependencyLockPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (isRootProject(project)) {
            final File lockFile = getLockFile(project);

            DependencyLockCreator dependencyLockCreator = new DefaultDependencyLockCreator();
            final DependencyLock dependencyLock = dependencyLockCreator.create(project.getAllprojects());

            project.getGradle().buildFinished(new Action<BuildResult>() {
                @Override
                public void execute(BuildResult buildResult) {
                    DependencyLockWriter dependencyLockWriter = new JsonDependencyLockWriter(lockFile);
                    dependencyLockWriter.write(dependencyLock);
                }
            });
        }
    }

    private boolean isRootProject(Project project) {
        return project.getParent() == null;
    }

    private File getLockFile(Project project) {
        File lockDir = project.file("gradle");
        return new File(lockDir, "dependencies.lock");
    }
}
