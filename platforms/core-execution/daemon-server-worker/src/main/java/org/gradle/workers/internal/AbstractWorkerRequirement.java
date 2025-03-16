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

package org.gradle.workers.internal;

import java.io.File;

public abstract class AbstractWorkerRequirement implements WorkerRequirement {
    private final File workerDirectory;
    private final File projectCacheDir;

    public AbstractWorkerRequirement(File workerDirectory, File projectCacheDir) {
        this.workerDirectory = workerDirectory;
        this.projectCacheDir = projectCacheDir;
    }

    @Override
    public File getWorkerDirectory() {
        return workerDirectory;
    }

    @Override
    public File getProjectCacheDir() {
        return projectCacheDir;
    }
}
