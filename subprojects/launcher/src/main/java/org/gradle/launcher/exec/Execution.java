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
package org.gradle.launcher.exec;

import org.gradle.api.Action;

/**
 * Models the execution of something, allowing customisable completion and error handling strategies.
 */
public class Execution {

    public static void execute(Action<ExecutionListener> action, ExecutionCompleter completer, Action<Throwable> errorHandler) {
        RecordingExecutionListener listener = new RecordingExecutionListener();
        try {
            action.execute(listener);
        } catch (Throwable e) {
            if (errorHandler != null) {
                errorHandler.execute(e);
            }
            listener.onFailure(e);
        }

        Throwable failure = listener.getFailure();
        if (failure == null) {
            completer.complete();
        } else {
            completer.completeWithFailure(failure);
        }
    }

    private static class RecordingExecutionListener implements ExecutionListener {
        private Throwable failure;

        public void onFailure(Throwable failure) {
            this.failure = failure;
        }

        public Throwable getFailure() {
            return failure;
        }
    }
}