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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;

public class ArchiveTaskBasedMavenArtifact extends AbstractMavenArtifact {
    private final AbstractArchiveTask archiveTask;
    private final TaskDependencyInternal buildDependencies;

    public ArchiveTaskBasedMavenArtifact(AbstractArchiveTask archiveTask) {
        this.archiveTask = archiveTask;
        this.buildDependencies = new DefaultTaskDependency(null, ImmutableSet.<Object>of(archiveTask));
    }

    @Override
    public File getFile() {
        return archiveTask.getArchiveFile().get().getAsFile();
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
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public boolean shouldBePublished() {
        return archiveTask.isEnabled();
    }
}
