/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.gmm.internal.artifact;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.publish.gmm.GMMArtifact;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

/**
 * GMM artifact that is built by the {@link org.gradle.api.publish.tasks.GenerateModuleMetadata} task.
 * <p>
 * This task is currently the only way to publish a GMM artifact.
 *
 * @since 8.1
 */
@Incubating
public abstract class DefaultGMMArtifact implements GMMArtifact {
    private final GenerateModuleMetadata gmmTask;
    private final TaskDependency allBuildDependencies;
    private final DefaultTaskDependency additionalBuildDependencies;

    public DefaultGMMArtifact(GenerateModuleMetadata gmmTask, TaskDependencyFactory taskDependencyFactory) {
        this.gmmTask = gmmTask;
        this.additionalBuildDependencies = new DefaultTaskDependency();
        this.allBuildDependencies = taskDependencyFactory.visitingDependencies(context -> {
            context.add(taskDependencyFactory.configurableDependency(ImmutableSet.of(gmmTask)));
            additionalBuildDependencies.visitDependencies(context);
        });
    }

    @Override
    public File getFile() {
        return gmmTask.getOutputFile().get().getAsFile();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return allBuildDependencies;
    }

    @Override
    public void builtBy(Object... tasks) {
        additionalBuildDependencies.add(tasks);
    }
}
