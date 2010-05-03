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

import org.gradle.api.logging.ProgressLogger;
import org.gradle.listener.ListenerManager;

public class DefaultProgressLoggerFactory implements ProgressLoggerFactory {
    private final ListenerManager listenerManager;

    public DefaultProgressLoggerFactory(ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    public ProgressLogger start(String description) {
        ProgressListener listener = listenerManager.getBroadcaster(ProgressListener.class);
        ProgressLoggerImpl logger = new ProgressLoggerImpl(description, listener);
        logger.started();
        return logger;
    }

    private static class ProgressLoggerImpl implements ProgressLogger {
        private final String description;
        private final ProgressListener listener;
        private String status = "";
        private boolean completed;

        public ProgressLoggerImpl(String description, ProgressListener listener) {
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
            listener.started(this);
        }

        public void progress(String status) {
            assertNotCompleted();
            this.status = status;
            listener.progress(this);
        }

        public void completed() {
            completed("");
        }

        public void completed(String status) {
            this.status = status;
            completed = true;
            listener.completed(this);
        }

        private void assertNotCompleted() {
            if (completed) {
                throw new IllegalStateException("This ProgressLogger has been completed.");
            }
        }
    }
}
