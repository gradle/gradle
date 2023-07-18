/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.Task;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Date;

public class LazyPublishArtifact implements PublishArtifactInternal {
    private final ProviderInternal<?> provider;
    @Nullable
    private final String version;
    private final FileResolver fileResolver;
    private final TaskDependencyFactory taskDependencyFactory;
    private PublishArtifactInternal delegate;

    public LazyPublishArtifact(Provider<?> archiveTask, FileResolver fileResolver, TaskDependencyFactory taskDependencyFactory) {
        this(archiveTask, null, fileResolver, taskDependencyFactory);
    }

    public LazyPublishArtifact(Provider<?> provider, @Nullable String version, FileResolver fileResolver, TaskDependencyFactory taskDependencyFactory) {
        this.provider = Providers.internal(provider);
        this.version = version;
        this.fileResolver = fileResolver;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public String getName() {
        return getDelegate().getName();
    }

    @Override
    public String getExtension() {
        return getDelegate().getExtension();
    }

    @Override
    public String getType() {
        return getDelegate().getType();
    }

    @Override
    public String getClassifier() {
        return getDelegate().getClassifier();
    }

    @Override
    public File getFile() {
        return getDelegate().getFile();
    }

    @Override
    public Date getDate() {
        return new Date();
    }

    private PublishArtifactInternal getDelegate() {
        if (delegate == null) {
            Object value = provider.get();
            if (value instanceof FileSystemLocation) {
                FileSystemLocation location = (FileSystemLocation) value;
                delegate = fromFile(location.getAsFile());
            } else if (value instanceof File) {
                delegate = fromFile((File) value);
            } else if (value instanceof AbstractArchiveTask) {
                delegate = new ArchivePublishArtifact(taskDependencyFactory, (AbstractArchiveTask) value);
            } else if (value instanceof Task) {
                delegate = fromFile(((Task) value).getOutputs().getFiles().getSingleFile());
            } else {
                delegate = fromFile(fileResolver.resolve(value));
            }
        }
        return delegate;
    }

    private PublishArtifactInternal fromFile(File file) {
        ArtifactFile artifactFile = new ArtifactFile(file, version);
        return new DefaultPublishArtifact(taskDependencyFactory, artifactFile.getName(), artifactFile.getExtension(), artifactFile.getExtension(), artifactFile.getClassifier(), null, file);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependencyFactory.visitingDependencies(context -> context.add(provider));
    }

    @Override
    public boolean shouldBePublished() {
        return getDelegate().shouldBePublished();
    }
}
