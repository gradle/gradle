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

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.MergeProvider;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Describables;

public class DefaultPublishArtifactSet extends DelegatingDomainObjectSet<PublishArtifact> implements PublishArtifactSet {
    private final TaskDependencyInternal builtBy;
    private final FileCollection files;
    private final Describable displayName;

    public DefaultPublishArtifactSet(
        String displayName,
        DomainObjectSet<PublishArtifact> backingSet,
        FileCollectionFactory fileCollectionFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this(Describables.of(displayName), backingSet, fileCollectionFactory, taskDependencyFactory);
    }

    public DefaultPublishArtifactSet(
        Describable displayName,
        DomainObjectSet<PublishArtifact> backingSet,
        FileCollectionFactory fileCollectionFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        super(backingSet);
        this.displayName = displayName;
        this.files = fileCollectionFactory.fromProvider(MergeProvider.of(
            Collections2.transform(this, artifact -> ((PublishArtifactInternal) artifact).getFileProvider())
        ));
        this.builtBy = taskDependencyFactory.configurableDependency(ImmutableSet.of(files));
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

}
