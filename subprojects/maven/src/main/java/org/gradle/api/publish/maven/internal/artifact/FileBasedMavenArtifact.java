/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.maven.internal.artifact;

import com.google.common.io.Files;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;

import java.io.File;

public class FileBasedMavenArtifact extends AbstractMavenArtifact {
    private final File file;
    private final String extension;

    public FileBasedMavenArtifact(File file, TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory);
        this.file = file;
        extension = Files.getFileExtension(file.getName());
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    protected String getDefaultExtension() {
        return extension;
    }

    @Override
    protected String getDefaultClassifier() {
        return null;
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public boolean shouldBePublished() {
        return true;
    }
}
