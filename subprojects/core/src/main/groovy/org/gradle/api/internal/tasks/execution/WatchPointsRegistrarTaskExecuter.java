/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.WatchPointsBuilder;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.filewatch.WatchPointsRegistry;

public class WatchPointsRegistrarTaskExecuter implements TaskExecuter {
    private final WatchPointsRegistry watchPointsRegistry;
    private final boolean continuousModeEnabled;
    private final TaskExecuter executer;

    public WatchPointsRegistrarTaskExecuter(WatchPointsRegistry watchPointsRegistry, boolean continuousModeEnabled, TaskExecuter executer) {
        this.watchPointsRegistry = watchPointsRegistry;
        this.continuousModeEnabled = continuousModeEnabled;
        this.executer = executer;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if(continuousModeEnabled) {
            FileCollectionInternal inputFiles = Cast.cast(FileCollectionInternal.class, task.getInputs().getFiles());
            WatchPointsBuilder watchPointsBuilder = watchPointsRegistry.createForTask(task);
            inputFiles.registerWatchPoints(watchPointsBuilder);
        }
        executer.execute(task, state, context);
    }
}
