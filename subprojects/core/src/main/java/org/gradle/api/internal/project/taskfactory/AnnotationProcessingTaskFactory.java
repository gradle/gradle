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
package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Specs;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;

/**
 * A {@link ITaskFactory} which determines task actions, inputs and outputs based on annotation attached to the task properties. Also provides some validation based on these annotations.
 */
public class AnnotationProcessingTaskFactory implements ITaskFactory {
    private final Instantiator instantiator;
    private final TaskClassInfoStore taskClassInfoStore;
    private final ITaskFactory taskFactory;

    public AnnotationProcessingTaskFactory(Instantiator instantiator, TaskClassInfoStore taskClassInfoStore, ITaskFactory taskFactory) {
        this.instantiator = instantiator;
        this.taskClassInfoStore = taskClassInfoStore;
        this.taskFactory = taskFactory;
    }

    @Override
    public ITaskFactory createChild(ProjectInternal project, InstantiationScheme instantiationScheme) {
        return new AnnotationProcessingTaskFactory(instantiator, taskClassInfoStore, taskFactory.createChild(project, instantiationScheme));
    }

    @Override
    public <S extends Task> S create(TaskIdentity<S> taskIdentity, @Nullable Object[] constructorArgs) {
        return process(taskFactory.create(taskIdentity, constructorArgs));
    }

    private <S extends Task> S process(S task) {
        TaskClassInfo taskClassInfo = taskClassInfoStore.getTaskClassInfo(task.getClass());

        for (TaskActionFactory actionFactory : taskClassInfo.getTaskActionFactories()) {
            ((TaskInternal) task).prependParallelSafeAction(actionFactory.create(instantiator));
        }

        // Enabled caching if task type is annotated with @CacheableTask
        if (taskClassInfo.isCacheable()) {
            task.getOutputs().cacheIf("Annotated with @CacheableTask", Specs.SATISFIES_ALL);
        }

        return task;
    }
}
