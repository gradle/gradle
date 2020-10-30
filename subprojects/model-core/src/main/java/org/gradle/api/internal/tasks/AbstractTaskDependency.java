/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.artifacts.transform.TransformationDependency;

import javax.annotation.Nullable;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.gradle.api.internal.tasks.WorkDependencyResolver.TASK_AS_TASK;

@NonNullApi
public abstract class AbstractTaskDependency implements TaskDependencyInternal {
    private static final WorkDependencyResolver<Task> IGNORE_ARTIFACT_TRANSFORM_RESOLVER = new WorkDependencyResolver<Task>() {
        @Override
        public boolean resolve(Task task, Object node, Action<? super Task> resolveAction) {
            // Ignore artifact transforms
            return node instanceof TransformationDependency;
        }

        @Override
        public boolean attachActionTo(Task task, Action<? super Task> action) {
            return false;
        }
    };

    @Override
    public Set<? extends Task> getDependencies(@Nullable Task task) {
        CachingTaskDependencyResolveContext<Task> context = new CachingTaskDependencyResolveContext<Task>(
            asList(TASK_AS_TASK, IGNORE_ARTIFACT_TRANSFORM_RESOLVER)
        );
        return context.getDependencies(task, this);
    }
}
