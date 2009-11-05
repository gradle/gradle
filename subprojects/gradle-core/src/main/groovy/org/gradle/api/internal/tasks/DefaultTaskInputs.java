/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.PathResolvingFileCollection;
import org.gradle.api.tasks.TaskInputs;

public class DefaultTaskInputs implements TaskInputs {
    private final PathResolvingFileCollection inputFiles;
    private final FileResolver resolver;

    public DefaultTaskInputs(FileResolver resolver) {
        this.resolver = resolver;
        inputFiles = new PathResolvingFileCollection("task input files", resolver, null);
    }

    public boolean getHasInputFiles() {
        return !inputFiles.getSources().isEmpty();
    }

    public FileCollection getFiles() {
        return inputFiles;
    }

    public TaskInputs files(Object... paths) {
        inputFiles.from(paths);
        return this;
    }

    public TaskInputs dir(Object dirPath) {
        inputFiles.from(resolver.resolveFilesAsTree(dirPath));
        return this;
    }
}
