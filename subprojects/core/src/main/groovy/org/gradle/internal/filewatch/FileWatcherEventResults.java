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

package org.gradle.internal.filewatch;

public final class FileWatcherEventResults {
    private static final ContinueFileWatcherEventResult CONTINUE_FILE_WATCHER_EVENT_RESULT = new ContinueFileWatcherEventResult() {
    };
    private static final TerminateFileWatcherEventResult TERMINATE_FILE_WATCHER_EVENT_RESULT = newTerminateResult(null);

    private FileWatcherEventResults() {
    }

    public static TerminateFileWatcherEventResult getTerminateResult() {
        return TERMINATE_FILE_WATCHER_EVENT_RESULT;
    }

    public static TerminateFileWatcherEventResult newTerminateResult(final Runnable onTerminateRunnable) {
        return new TerminateFileWatcherEventResult() {
            @Override
            public Runnable getOnTerminateAction() {
                return onTerminateRunnable;
            }
        };
    }

    public static ContinueFileWatcherEventResult getContinueResult() {
        return CONTINUE_FILE_WATCHER_EVENT_RESULT;
    }
}
