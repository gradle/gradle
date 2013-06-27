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
package org.gradle.api.internal.project;

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
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;

public class TaskExecutionServices extends DefaultServiceRegistry {
    private final Gradle gradle;

    public TaskExecutionServices(ServiceRegistry parent, Gradle gradle) {
        super(parent);
        this.gradle = gradle;
    }

    protected TaskExecuter createTaskExecuter() {
        TaskArtifactStateCacheAccess cacheAccess = get(TaskArtifactStateCacheAccess.class);
        TaskArtifactStateRepository repository = get(TaskArtifactStateRepository.class);
        return new ExecuteAtMostOnceTaskExecuter(
                new SkipOnlyIfTaskExecuter(
                        new SkipTaskWithNoActionsExecuter(
                                new SkipEmptySourceFilesTaskExecuter(
                                        new ValidatingTaskExecuter(
                                                new SkipUpToDateTaskExecuter(repository,
                                                        new PostExecutionAnalysisTaskExecuter(
                                                                new ExecuteActionsTaskExecuter(
                                                                        get(ListenerManager.class).getBroadcaster(TaskActionListener.class)
                                                                ))))))));
    }

    protected TaskArtifactStateCacheAccess createCacheAccess() {
        return new DefaultTaskArtifactStateCacheAccess(gradle, get(CacheRepository.class));
    }

    protected TaskArtifactStateRepository createTaskArtifactStateRepository() {
        TaskArtifactStateCacheAccess cacheAccess = get(TaskArtifactStateCacheAccess.class);

        FileSnapshotter fileSnapshotter = new DefaultFileSnapshotter(
                new CachingHasher(
                        new DefaultHasher(),
                        cacheAccess), cacheAccess);

        FileSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter(fileSnapshotter, new RandomLongIdGenerator(), cacheAccess);

        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess, new CacheBackedFileSnapshotRepository(cacheAccess, new RandomLongIdGenerator()));

        Instantiator instantiator = get(Instantiator.class);
        return new ShortCircuitTaskArtifactStateRepository(
                        get(StartParameter.class),
                        instantiator,
                        new DefaultTaskArtifactStateRepository(
                                taskHistoryRepository,
                                instantiator,
                                outputFilesSnapshotter,
                                fileSnapshotter
                        )
        );
    }

    protected TaskPlanExecutor createTaskExecutorFactory() {
        StartParameter startParameter = gradle.getStartParameter();
        TaskArtifactStateCacheAccess cacheAccess = get(TaskArtifactStateCacheAccess.class);
        return new TaskPlanExecutorFactory(cacheAccess, startParameter.getParallelThreadCount()).create();
    }
}
