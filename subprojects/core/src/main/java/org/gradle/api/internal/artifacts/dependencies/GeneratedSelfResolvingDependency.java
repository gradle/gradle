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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Set;

public class GeneratedSelfResolvingDependency extends AbstractDependency implements SelfResolvingDependencyInternal, FileCollectionDependency {
    private static final TaskDependency EMPTY_TASK_DEPENDENCY = new TaskDependency() {
        @Override
        public Set<? extends Task> getDependencies(Task task) {
            return Collections.emptySet();
        }
    };

    private final ComponentIdentifier targetComponentId;
    private final Factory<FileCollectionInternal> sourceFactory;
    private FileCollectionInternal files;

    public GeneratedSelfResolvingDependency(Factory<FileCollectionInternal> sourceFactory) {
        this.targetComponentId = null;
        this.sourceFactory = sourceFactory;
    }

    public GeneratedSelfResolvingDependency(ComponentIdentifier targetComponentId, Factory<FileCollectionInternal> sourceFactory) {
        this.targetComponentId = targetComponentId;
        this.sourceFactory = sourceFactory;
    }

    @Override
    public FileCollection getFiles() {
        maybeResolveFileCollectionInternal();
        return files;
    }

    private void maybeResolveFileCollectionInternal() {
        if(files == null){
            files = sourceFactory.create();
        }
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        if (!(dependency instanceof GeneratedSelfResolvingDependency)) {
            return false;
        }
        maybeResolveFileCollectionInternal();
        GeneratedSelfResolvingDependency selfResolvingDependency = (GeneratedSelfResolvingDependency) dependency;
        return files.equals(selfResolvingDependency.getFiles());
    }

    @Override
    public GeneratedSelfResolvingDependency copy() {
        return new GeneratedSelfResolvingDependency(targetComponentId, sourceFactory);
    }

    @Nullable
    @Override
    public ComponentIdentifier getTargetComponentId() {
        return targetComponentId;
    }

    @Override
    public String getGroup() {
        return null;
    }

    @Override
    public String getName() {
        return "unspecified";
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public void resolve(DependencyResolveContext context) {
        maybeResolveFileCollectionInternal();
        context.add(files);
    }

    @Override
    public Set<File> resolve() {
        maybeResolveFileCollectionInternal();
        return files.getFiles();
    }

    @Override
    public Set<File> resolve(boolean transitive) {
        return resolve();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return EMPTY_TASK_DEPENDENCY;
    }

}
