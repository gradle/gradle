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
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.file.DefaultFileSystemLocation;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;

/**
 * Implements {@link org.gradle.api.Buildable} and {@link #builtBy(Object...)} for {@link ConfigurablePublishArtifact}.
 * <p>
 * In future versions, We should drop {@link org.gradle.api.Buildable} from
 * {@link org.gradle.api.artifacts.PublishArtifact}, and therefore this class will no longer be needed.
 */
public abstract class AbstractConfigurablePublishArtifact implements ConfigurablePublishArtifact, PublishArtifactInternal, TaskDependencyContainer {

    private final DefaultTaskDependency taskDependency;

    public AbstractConfigurablePublishArtifact(
        TaskDependencyFactory taskDependencyFactory,
        Object... dependencies
    ) {
        taskDependency = taskDependencyFactory.configurableDependency(ImmutableSet.copyOf(dependencies));
    }

    @Override
    public ConfigurablePublishArtifact builtBy(Object... tasks) {
        taskDependency.add(tasks);
        return this;
    }

    @Override
    public Provider<? extends FileSystemLocation> getFileProvider() {
        // Ideally, subclasses would implement this method directly, but since we need to
        // include dependencies from `builtBy`, we implement this here and delegate to getFile().
        return new BuildableBackedProvider<>(this, FileSystemLocation.class, SerializableLambdas.factory(() -> new DefaultFileSystemLocation(getFile())));
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(taskDependency);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + getName() + ":" + getType() + ":" + getExtension()  + ":" + getClassifier();
    }
}
