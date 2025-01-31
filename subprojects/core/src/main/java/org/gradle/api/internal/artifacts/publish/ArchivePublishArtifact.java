/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Date;

/**
 * Wraps an {@link AbstractArchiveTask} as a {@link PublishArtifactInternal}.
 */
public class ArchivePublishArtifact implements PublishArtifactInternal {

    private final TaskDependency taskDependency;
    private final AbstractArchiveTask archiveTask;

    public ArchivePublishArtifact(TaskDependencyFactory taskDependencyFactory, AbstractArchiveTask archiveTask) {
        this.archiveTask = archiveTask;
        this.taskDependency = taskDependencyFactory.configurableDependency(ImmutableSet.of(archiveTask));
    }

    @Override
    public Provider<? extends FileSystemLocation> getFileProvider() {
        return archiveTask.getArchiveFile();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }

    @Override
    public File getFile() {
        return archiveTask.getArchiveFile().get().getAsFile();
    }

    @Override
    public String getName() {
        String baseName = archiveTask.getArchiveBaseName().getOrNull();
        if (baseName != null) {
            return withAppendix(baseName);
        }
        return archiveTask.getArchiveAppendix().getOrNull();
    }

    private String withAppendix(String baseName) {
        String appendix = archiveTask.getArchiveAppendix().getOrNull();
        return baseName + (GUtil.isTrue(appendix)? "-" + appendix : "");
    }

    @Override
    public String getExtension() {
        return archiveTask.getArchiveExtension().getOrNull();
    }

    @Override
    public String getType() {
        return archiveTask.getArchiveExtension().getOrNull();
    }

    @Override
    public String getClassifier() {
        return archiveTask.getArchiveClassifier().getOrNull();
    }

    @Override
    public Date getDate() {
        return new Date(archiveTask.getArchiveFile().get().getAsFile().lastModified());
    }

    @Override
    public boolean shouldBePublished() {
        return archiveTask.isEnabled();
    }
}
