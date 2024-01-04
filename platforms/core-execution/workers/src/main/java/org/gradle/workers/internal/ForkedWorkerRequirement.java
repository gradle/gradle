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

public class ForkedWorkerRequirement extends IsolatedClassLoaderWorkerRequirement {
    private final DaemonForkOptions forkOptions;

    public ForkedWorkerRequirement(File projectDirectory, File rootDirectory, DaemonForkOptions forkOptions) {
        super(projectDirectory, rootDirectory, forkOptions.getClassLoaderStructure());
        this.forkOptions = forkOptions;
    }

    public ForkedWorkerRequirement(File projectDirectory, DaemonForkOptions forkOptions) {
        // TODO-RC what to do about the root directory?
        this(projectDirectory, projectDirectory, forkOptions);
    }

    public DaemonForkOptions getForkOptions() {
        return forkOptions;
    }
}
