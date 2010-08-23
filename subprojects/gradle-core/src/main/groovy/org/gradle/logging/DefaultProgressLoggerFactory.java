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

package org.gradle.logging;

import org.gradle.logging.internal.ProgressCompleteEvent;
import org.gradle.logging.internal.ProgressEvent;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.logging.internal.ProgressStartEvent;

public class DefaultProgressLoggerFactory implements ProgressLoggerFactory {
    private final ProgressListener progressListener;

    public DefaultProgressLoggerFactory(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public ProgressLogger start(String loggerCategory) {
        return start(loggerCategory, "");
    }

    public ProgressLogger start(String loggerCategory, String description) {
        ProgressLoggerImpl logger = new ProgressLoggerImpl(loggerCategory, description, progressListener);
        logger.started();
        return logger;
    }

    private static class ProgressLoggerImpl implements ProgressLogger {
        private final String category;
        private final String description;
        private final ProgressListener listener;
        private String status = "";
        private boolean completed;

        public ProgressLoggerImpl(String category, String description, ProgressListener listener) {
            this.category = category;
            this.description = description;
            this.listener = listener;
        }

        public String getDescription() {
            return description;
        }

        public String getStatus() {
            return status;
        }

        public void started() {
            listener.started(new ProgressStartEvent(category, description));
        }

        public void progress(String status) {
            assertNotCompleted();
            this.status = status;
            listener.progress(new ProgressEvent(category, status));
        }

        public void completed() {
            completed("");
        }

        public void completed(String status) {
            this.status = status;
            completed = true;
            listener.completed(new ProgressCompleteEvent(category, status));
        }

        private void assertNotCompleted() {
            if (completed) {
                throw new IllegalStateException("This ProgressLogger has been completed.");
            }
        }
    }
}
