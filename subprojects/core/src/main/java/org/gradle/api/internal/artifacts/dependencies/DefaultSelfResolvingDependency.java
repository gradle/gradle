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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Set;

public class DefaultSelfResolvingDependency extends AbstractDependency implements SelfResolvingDependencyInternal, FileCollectionDependency {
    private final ComponentIdentifier targetComponentId;
    private final FileCollectionInternal source;

    public DefaultSelfResolvingDependency(FileCollectionInternal source) {
        this.targetComponentId = null;
        this.source = source;
    }

    public DefaultSelfResolvingDependency(ComponentIdentifier targetComponentId, FileCollectionInternal source) {
        this.targetComponentId = targetComponentId;
        this.source = source;
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        if (!(dependency instanceof DefaultSelfResolvingDependency)) {
            return false;
        }
        DefaultSelfResolvingDependency selfResolvingDependency = (DefaultSelfResolvingDependency) dependency;
        return source.equals(selfResolvingDependency.source);
    }

    @Override
    public DefaultSelfResolvingDependency copy() {
        return new DefaultSelfResolvingDependency(targetComponentId, source);
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
        context.add(source);
    }

    @Override
    public Set<File> resolve() {
        return source.getFiles();
    }

    @Override
    public Set<File> resolve(boolean transitive) {
        return source.getFiles();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return source.getBuildDependencies();
    }

    @Override
    public FileCollection getFiles() {
        return source;
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        source.registerWatchPoints(builder);
    }
}
