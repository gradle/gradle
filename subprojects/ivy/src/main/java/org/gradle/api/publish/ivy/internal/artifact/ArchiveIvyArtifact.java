/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Buildable;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;

public class ArchiveIvyArtifact extends ConfigurableIvyArtifact implements Buildable {
    private final AbstractArchiveTask delegate;

    public ArchiveIvyArtifact(AbstractArchiveTask delegate) {
        this.delegate = delegate;
    }

    @Override
    protected String getBaseName() {
        return delegate.getBaseName();
    }

    @Override
    protected String getBaseType() {
        return delegate.getExtension();
    }

    @Override
    protected String getBaseExtension() {
        return delegate.getExtension();
    }

    public File getFile() {
        return delegate.getArchivePath();
    }

    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            public void resolve(TaskDependencyResolveContext context) {
                context.add(delegate);
            }
        };
    }
}
