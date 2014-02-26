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
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.*;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.execution.taskgraph.TaskPlanExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ListenerManager;
import org.gradle.messaging.serialize.DefaultSerializerRegistry;
import org.gradle.messaging.serialize.SerializerRegistry;

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

    TaskArtifactStateCacheAccess createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryTaskArtifactCache inMemoryTaskArtifactCache, GradleBuildEnvironment environment) {
        CacheDecorator decorator;
        if (environment.isLongLivingProcess()) {
            decorator = inMemoryTaskArtifactCache;
        } else {
            decorator = new NoOpDecorator();
        }
        return new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository, decorator);
    }

    FileSnapshotter createFileSnapshotter(TaskArtifactStateCacheAccess cacheAccess) {
        return new CachingFileSnapshotter(new DefaultHasher(), cacheAccess);
    }

    TaskArtifactStateRepository createTaskArtifactStateRepository(Instantiator instantiator, TaskArtifactStateCacheAccess cacheAccess, StartParameter startParameter, FileSnapshotter fileSnapshotter) {
        FileCollectionSnapshotter fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cacheAccess);

        FileCollectionSnapshotter outputFilesSnapshotter = new OutputFilesCollectionSnapshotter(fileCollectionSnapshotter, new RandomLongIdGenerator(), cacheAccess);

        SerializerRegistry<FileCollectionSnapshot> serializerRegistry = new DefaultSerializerRegistry<FileCollectionSnapshot>();
        fileCollectionSnapshotter.registerSerializers(serializerRegistry);
        outputFilesSnapshotter.registerSerializers(serializerRegistry);

        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess,
                new CacheBackedFileSnapshotRepository(cacheAccess,
                        serializerRegistry.build(),
                        new RandomLongIdGenerator()));

        return new ShortCircuitTaskArtifactStateRepository(
                        startParameter,
                        instantiator,
                        new DefaultTaskArtifactStateRepository(
                                taskHistoryRepository,
                                instantiator,
                                outputFilesSnapshotter,
                                fileCollectionSnapshotter
                        )
        );
    }

    TaskPlanExecutor createTaskExecutorFactory(StartParameter startParameter, ExecutorFactory executorFactory) {
        return new TaskPlanExecutorFactory(startParameter.getParallelThreadCount(), executorFactory).create();
    }
}
