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

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public class ArchiveTaskBasedMavenArtifact extends AbstractMavenArtifact {
    private final AbstractArchiveTask archiveTask;

    public ArchiveTaskBasedMavenArtifact(AbstractArchiveTask archiveTask, TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory, archiveTask);
        this.archiveTask = archiveTask;
    }

    @Override
    public Provider<? extends FileSystemLocation> getFileProvider() {
        return archiveTask.getArchiveFile();
    }

    @Override
    protected String getDefaultExtension() {
        return archiveTask.getArchiveExtension().getOrNull();
    }

    @Override
    protected String getDefaultClassifier() {
        return archiveTask.getArchiveClassifier().getOrNull();
    }

    @Override
    public boolean shouldBePublished() {
        return archiveTask.isEnabled();
    }
}
