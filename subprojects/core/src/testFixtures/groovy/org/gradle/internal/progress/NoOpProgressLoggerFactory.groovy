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

class NoOpProgressLoggerFactory implements ProgressLoggerFactory {
    ProgressLogger newOperation(String loggerCategory) {
        throw new UnsupportedOperationException()
    }

    ProgressLogger newOperation(Class<?> loggerCategory) {
        throw new UnsupportedOperationException()
    }

    ProgressLogger newOperation(Class<?> loggerCategory, BuildOperationDescriptor buildOperationDescriptor) {
        return new Logger()
    }

    ProgressLogger newOperation(Class<?> loggerClass, ProgressLogger parent) {
        throw new UnsupportedOperationException()
    }

    static class Logger implements ProgressLogger {
        String description
        String shortDescription
        String loggingHeader

        String getDescription() { description }

        ProgressLogger setDescription(String description) {
            this.description = description
            this
        }

        String getShortDescription() { shortDescription }

        ProgressLogger setShortDescription(String description) {
            this.shortDescription = description
            this
        }

        String getLoggingHeader() { loggingHeader }

        ProgressLogger setLoggingHeader(String header) {
            this.loggingHeader = header
            this
        }

        ProgressLogger start(String description, String shortDescription) {
            setDescription(description)
            setShortDescription(shortDescription)
            started()
            this
        }

        void started() {}
        void started(String status) {}
        void progress(String status) {}
        void completed() {}
        void completed(String status) {}
    }
}
