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
package org.gradle.api.internal.changedetection;

import org.gradle.api.tasks.TaskInputChanges;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;

public class FileCacheBroadcastTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private final TaskArtifactStateRepository repository;
    private final FileCacheListener listener;

    public FileCacheBroadcastTaskArtifactStateRepository(TaskArtifactStateRepository repository, FileCacheListener listener) {
        this.repository = repository;
        this.listener = listener;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        final TaskArtifactState state = repository.getStateFor(task);
        return new TaskArtifactState() {
            public boolean isUpToDate() {
                listener.cacheable(task.getInputs().getFiles());
                listener.cacheable(task.getOutputs().getFiles());

                return state.isUpToDate();
            }

            public TaskInputChanges getInputChanges() {
                return state.getInputChanges();
            }

            public void beforeTask() {
                if (task.getOutputs().getHasOutput()) {
                    listener.invalidate(task.getOutputs().getFiles());
                } else {
                    listener.invalidateAll();
                }
                state.beforeTask();
            }

            public void afterTask() {
                listener.cacheable(task.getOutputs().getFiles());
                state.afterTask();
            }

            public void finished() {
                state.finished();
            }

            public TaskExecutionHistory getExecutionHistory() {
                return state.getExecutionHistory();
            }
        };
    }
}
