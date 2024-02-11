/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.progress

import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationDescriptor

class RecordingProgressLoggerFactory implements ProgressLoggerFactory {
    private List<String> messages = []

    ProgressLogger newOperation(String loggerCategory) {
        return new Logger(messages)
    }

    ProgressLogger newOperation(Class<?> loggerCategory) {
        return new Logger(messages)
    }

    ProgressLogger newOperation(Class<?> loggerCategory, BuildOperationDescriptor buildOperationDescriptor) {
        return new Logger(messages)
    }

    ProgressLogger newOperation(Class<?> loggerClass, ProgressLogger parent) {
        return new Logger(messages)
    }

    List<String> getRecordedMessages() {
        return messages
    }

    class Logger implements ProgressLogger {
        private List<String> messages

        String description

        Logger(List<String> messages) {
            this.messages = messages
        }

        String getDescription() { description }

        ProgressLogger setDescription(String description) {
            this.description = description
            this
        }

        ProgressLogger start(String description, String status) {
            setDescription(description)
            started()
            this
        }

        void started() {}

        void started(String status) {
            messages.add(status)
        }

        void progress(String status) {
            messages.add(status)
        }

        void progress(String status, boolean failing) {
            messages.add(status)
        }

        void completed() {}

        void completed(String status, boolean failed) {
            messages.add(status)
        }
    }
}
