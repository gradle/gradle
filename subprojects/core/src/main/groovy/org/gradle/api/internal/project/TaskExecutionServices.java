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
import org.gradle.api.internal.changedetection.*;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.*;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.listener.ListenerManager;
import org.gradle.util.RandomLongIdGenerator;

public class TaskExecutionServices extends DefaultServiceRegistry {
    private final Gradle gradle;

    public TaskExecutionServices(ServiceRegistry parent, Gradle gradle) {
        super(parent);
        this.gradle = gradle;
    }

    protected TaskExecuter createTaskExecuter() {
        return new ExecuteAtMostOnceTaskExecuter(
                new SkipOnlyIfTaskExecuter(
                        new SkipTaskWithNoActionsExecuter(
                                new SkipEmptySourceFilesTaskExecuter(
                                        new ValidatingTaskExecuter(
                                                new SkipUpToDateTaskExecuter(
                                                        new PostExecutionAnalysisTaskExecuter(
                                                                new ExecuteActionsTaskExecuter(
                                                                        get(ListenerManager.class).getBroadcaster(TaskActionListener.class))),
                                                        get(TaskArtifactStateRepository.class)))))));
    }

    protected TaskArtifactStateRepository createTaskArtifactStateRepository() {
        CacheRepository cacheRepository = get(CacheRepository.class);
        FileSnapshotter fileSnapshotter = new DefaultFileSnapshotter(
                new CachingHasher(
                        new DefaultHasher(),
                        cacheRepository,
                        gradle));

        FileSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter(fileSnapshotter, new RandomLongIdGenerator(), cacheRepository, gradle);

        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheRepository, new CacheBackedFileSnapshotRepository(cacheRepository, gradle), gradle);

        return new FileCacheBroadcastTaskArtifactStateRepository(
                new ShortCircuitTaskArtifactStateRepository(
                        get(StartParameter.class),
                        new DefaultTaskArtifactStateRepository(
                                taskHistoryRepository,
                                fileSnapshotter,
                                outputFilesSnapshotter)),
                new DefaultFileCacheListener());
    }
}
