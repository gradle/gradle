/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Describables;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultPublishArtifactSet extends DelegatingDomainObjectSet<PublishArtifact> implements PublishArtifactSet {
    private final TaskDependencyInternal builtBy = new ArtifactsTaskDependency();
    private final FileCollection files;
    private final Describable displayName;

    public DefaultPublishArtifactSet(String displayName, DomainObjectSet<PublishArtifact> backingSet, FileCollectionFactory fileCollectionFactory) {
        this(Describables.of(displayName), backingSet, fileCollectionFactory);
    }

    public DefaultPublishArtifactSet(Describable displayName, DomainObjectSet<PublishArtifact> backingSet, FileCollectionFactory fileCollectionFactory) {
        super(backingSet);
        this.displayName = displayName;
        this.files = fileCollectionFactory.create(builtBy, new ArtifactsFileCollection());
    }

    @Override
    public String toString() {
        return displayName.getDisplayName();
    }

    @Override
    public FileCollection getFiles() {
        return files;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return builtBy;
    }

    private class ArtifactsFileCollection implements MinimalFileSet {
        @Override
        public String getDisplayName() {
            return displayName.getDisplayName();
        }

        @Override
        public Set<File> getFiles() {
            Set<File> files = new LinkedHashSet<File>();
            for (PublishArtifact artifact : DefaultPublishArtifactSet.this) {
                files.add(artifact.getFile());
            }
            return files;
        }
    }

    private class ArtifactsTaskDependency extends AbstractTaskDependency {
        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (PublishArtifact publishArtifact : DefaultPublishArtifactSet.this) {
                context.add(publishArtifact);
            }
        }
    }
}
