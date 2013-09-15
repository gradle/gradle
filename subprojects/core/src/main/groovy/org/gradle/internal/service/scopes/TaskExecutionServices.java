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
package org.gradle.internal.service.scopes;

import org.gradle.StartParameter;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.changes.ShortCircuitTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.*;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.*;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutorFactory;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ListenerManager;

public class TaskExecutionServices {
    TaskExecuter createTaskExecuter(TaskArtifactStateRepository repository, ListenerManager listenerManager) {
        return new ExecuteAtMostOnceTaskExecuter(
                new SkipOnlyIfTaskExecuter(
                        new SkipTaskWithNoActionsExecuter(
                                new SkipEmptySourceFilesTaskExecuter(
                                        new ValidatingTaskExecuter(
                                                new SkipUpToDateTaskExecuter(repository,
                                                        new PostExecutionAnalysisTaskExecuter(
                                                                new ExecuteActionsTaskExecuter(
                                                                        listenerManager.getBroadcaster(TaskActionListener.class)
                                                                ))))))));
    }

    TaskArtifactStateCacheAccess createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryTaskArtifactCache inMemoryTaskArtifactCache, StartParameter startParameter) {
        InMemoryPersistentCacheDecoratorFactory decoratorFactory = new InMemoryPersistentCacheDecoratorFactory(inMemoryTaskArtifactCache, startParameter);
        return new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository, decoratorFactory);
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, TaskArtifactStateCacheAccess cacheAccess, StartParameter startParameter) {
        FileSnapshotter fileSnapshotter = new DefaultFileSnapshotter(
                new CachingHasher(
                        new DefaultHasher(),
                        cacheAccess), cacheAccess);

        FileSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter(fileSnapshotter, new RandomLongIdGenerator(), cacheAccess);

        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess,
                new CacheBackedFileSnapshotRepository(cacheAccess,
                        new RandomLongIdGenerator()));

        return new ShortCircuitTaskArtifactStateRepository(
                        startParameter,
                        instantiator,
                        new DefaultTaskArtifactStateRepository(
                                taskHistoryRepository,
                                instantiator,
                                outputFilesSnapshotter,
                                fileSnapshotter
                        )
        );
    }

    TaskPlanExecutor createTaskExecutorFactory(StartParameter startParameter, TaskArtifactStateCacheAccess cacheAccess) {
        return new TaskPlanExecutorFactory(cacheAccess, startParameter.getParallelThreadCount()).create();
    }
}
