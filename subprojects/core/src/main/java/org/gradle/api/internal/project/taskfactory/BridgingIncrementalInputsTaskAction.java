/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.lang.reflect.Method;

public class BridgingIncrementalInputsTaskAction extends IncrementalInputsTaskAction {
    public BridgingIncrementalInputsTaskAction(Class<? extends Task> taskType, Method method) {
        super(taskType, method);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void doExecute(Task task, String methodName) {
        JavaMethod.of(task, Object.class, methodName, org.gradle.api.tasks.incremental.IncrementalTaskInputs.class).invoke(task, new BridgingInputChanges(getInputChanges()));
    }

    @SuppressWarnings("deprecation")
    private static class BridgingInputChanges implements org.gradle.api.tasks.incremental.IncrementalTaskInputs, InputChanges {
        private final InputChanges inputChanges;

        public BridgingInputChanges(InputChanges inputChanges) {
            this.inputChanges = inputChanges;
        }

        @Override
        public boolean isIncremental() {
            return inputChanges.isIncremental();
        }

        @Override
        public Iterable<FileChange> getFileChanges(FileCollection parameter) {
            return inputChanges.getFileChanges(parameter);
        }

        @Override
        public Iterable<FileChange> getFileChanges(Provider<? extends FileSystemLocation> parameter) {
            return inputChanges.getFileChanges(parameter);
        }

        @Override
        public void outOfDate(Action<? super InputFileDetails> outOfDateAction) {
            throw new UnsupportedOperationException("Only for bridging");
        }

        @Override
        public void removed(Action<? super InputFileDetails> removedAction) {
            throw new UnsupportedOperationException("Only for bridging");
        }
    }
}
