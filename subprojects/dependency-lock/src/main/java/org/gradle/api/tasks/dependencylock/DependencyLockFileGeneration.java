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

package org.gradle.api.tasks.dependencylock;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.dependencylock.DefaultDependencyLockCreator;
import org.gradle.internal.dependencylock.DependencyLockCreator;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.writer.DependencyLockWriter;
import org.gradle.internal.dependencylock.writer.JsonDependencyLockWriter;
import org.gradle.internal.dependencylock.writer.StandardOutputDependencyLockWriter;

import java.io.File;

public class DependencyLockFileGeneration extends DefaultTask {

    private File lockFile;

    @TaskAction
    public void generate() {
        DependencyLockCreator dependencyLockCreator = new DefaultDependencyLockCreator();
        DependencyLock dependencyLock = dependencyLockCreator.create(getProject());

        DependencyLockWriter standardOutputDependencyLockWriter = new StandardOutputDependencyLockWriter();
        standardOutputDependencyLockWriter.write(dependencyLock);
        DependencyLockWriter jsonDependencyLockWriter = new JsonDependencyLockWriter(getLockFile());
        jsonDependencyLockWriter.write(dependencyLock);
    }

    @OutputFile
    public File getLockFile() {
        return lockFile;
    }

    public void setLockFile(File lockFile) {
        this.lockFile = lockFile;
    }
}
