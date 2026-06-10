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
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public class ArchiveTaskBasedMavenArtifact extends AbstractMavenArtifact {
    private final AbstractArchiveTask archiveTask;
    private final TaskDependencyInternal buildDependencies;

    public ArchiveTaskBasedMavenArtifact(
        AbstractArchiveTask archiveTask,
        TaskDependencyFactory taskDependencyFactory,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory
    ) {
        super(taskDependencyFactory, objectFactory, providerFactory);
        this.archiveTask = archiveTask;
        this.buildDependencies = taskDependencyFactory.configurableDependency(ImmutableSet.of(archiveTask));
    }

    @Override
    public Provider<RegularFile> getFile() {
        return archiveTask.getArchiveFile();
    }

    @Override
    protected Provider<String> getDefaultExtension() {
        return archiveTask.getArchiveExtension();
    }

    @Override
    protected Provider<String> getDefaultClassifier() {
        return archiveTask.getArchiveClassifier().filter(it -> !it.isEmpty());
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
