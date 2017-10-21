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

package org.gradle.nativeplatform.test.googletest.internal;

import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;

import java.io.File;

public class GoogleTestExecutionSpec implements TestExecutionSpec {
    private final File workingDir;
    private final RegularFile executable;

    // TODO: Make TestExecutionSpec Describable/Named?
    private final String path;

    public GoogleTestExecutionSpec(File workingDir, RegularFile executable, String path) {
        this.workingDir = workingDir;
        this.executable = executable;
        this.path = path;
    }

    public File getWorkingDirectory() {
        return workingDir;
    }

    public RegularFile getExecutable() {
        return executable;
    }

    public String getPath() {
        return path;
    }
}
