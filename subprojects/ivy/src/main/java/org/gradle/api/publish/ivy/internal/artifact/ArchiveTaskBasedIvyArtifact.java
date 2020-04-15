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

package org.gradle.api.publish.ivy.internal.artifact;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;

public class ArchiveTaskBasedIvyArtifact extends AbstractIvyArtifact {
    private final AbstractArchiveTask archiveTask;
    private final IvyPublicationIdentity identity;
    private final TaskDependencyInternal buildDependencies;

    public ArchiveTaskBasedIvyArtifact(AbstractArchiveTask archiveTask, IvyPublicationIdentity identity) {
        this.archiveTask = archiveTask;
        this.identity = identity;
        this.buildDependencies = new DefaultTaskDependency(null, ImmutableSet.<Object>of(archiveTask));
    }

    @Override
    protected String getDefaultName() {
        return identity.getModule();
    }

    @Override
    protected String getDefaultType() {
        return archiveTask.getArchiveExtension().getOrNull();
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
    protected String getDefaultConf() {
        return null;
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return buildDependencies;
    }

    @Override
    public File getFile() {
        return archiveTask.getArchiveFile().get().getAsFile();
    }

    @Override
    public boolean shouldBePublished() {
        return archiveTask.isEnabled();
    }
}
